package izumi.distage.framework

import com.typesafe.config.ConfigFactory
import distage.{DefaultModule, Injector}
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.config.model.{AppConfig, ConfTag}
import izumi.distage.constructors.TraitConstructor
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.{ActivationChoicesExtractor, ConfigLoader}
import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition._
import izumi.distage.model.effect.QuasiAsync
import izumi.distage.model.exceptions.InvalidPlanException
import izumi.distage.model.plan.{OrderedPlan, Roots}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.recursive.{BootConfig, Bootloader, LocatorRef}
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanIssue.UnsaturatedAxis
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.distage.plugins.load.LoadedPlugins
import izumi.distage.roles.PlanHolder
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.{RoleAppActivationParser, RoleProvider}
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.strings.IzString.{toRichIterable, toRichString}
import izumi.logstage.api.IzLogger
import izumi.reflect.{Tag, TagK}

import scala.annotation.tailrec

object PlanCheck {
  final val defaultActivationsLimit = DebugProperties.`izumi.distage.plancheck.bruteforce.max-activations`.strValue().fold(9000)(_.asInt(9000))
  final val defaultCheckConfig = DebugProperties.`izumi.distage.plancheck.check-config`.boolValue(true)
  final val defaultPrintPlan = DebugProperties.`izumi.distage.plancheck.print-plan`.boolValue(false)

  sealed abstract class PlanCheckResult {
    def loadedPlugins: LoadedPlugins
    def issues: Option[InvalidPlanException]

    @throws[InvalidPlanException]
    final def throwOnError(): LoadedPlugins = this match {
      case PlanCheckResult.Correct(loadedPlugins) => loadedPlugins
      case PlanCheckResult.Incorrect(_, exception) => throw exception
    }
  }
  object PlanCheckResult {
    final case class Correct(loadedPlugins: LoadedPlugins) extends PlanCheckResult {
      override def issues: Option[InvalidPlanException] = None
    }
    final case class Incorrect(loadedPlugins: LoadedPlugins, exception: InvalidPlanException) extends PlanCheckResult {
      override def issues: Option[InvalidPlanException] = Some(exception)
    }
  }

  /**
    * @param roleAppMain ...
    * @param roles       "*" to check all roles
    * @param activations "*" to check all possible activations
    * @param config      Config resource file name, e.g. "application.conf" or "*" if using the same config settings as `roleAppMain`
    * @param checkConfig Try to parse config file checking all the config bindings added using [[izumi.distage.config.ConfigModuleDef]]
    */
  def checkRoleApp(
    roleAppMain: PlanHolder,
    roles: String = "*",
    activations: String = "*",
    config: String = "*",
    checkConfig: Boolean = defaultCheckConfig,
    printPlan: Boolean = defaultPrintPlan,
    logger: TrivialLogger = defaultLogger,
  ): PlanCheckResult = {
    val chosenRoles = if (roles == "*") None else Some(parseRoles(roles))
    val chosenActivations = if (activations == "*") None else Some(parseActivations(activations))
    val chosenConfig = if (config == "*") None else Some(config)

    checkRoleAppParsed(roleAppMain, chosenRoles, chosenActivations, chosenConfig, checkConfig, printPlan, logger)
  }

  def checkRoleAppParsed(
    roleAppMain: PlanHolder,
    chosenRoles: Option[Set[String]],
    chosenActivations: Option[Array[Iterable[(String, String)]]],
    chosenConfig: Option[String],
    checkConfig: Boolean,
    printPlan: Boolean,
    logger: TrivialLogger = defaultLogger,
  ): PlanCheckResult = {

    var effectiveRoles = "* (effective: unknown, failed too early)"
    var effectiveActivations = "* (effective: unknown, failed too early)"
    var effectiveConfig = "*"
    var effectiveLoadedPlugins = LoadedPlugins.empty

    // indirection for tailrec
    def cutSuppressed(t: Throwable): Unit = cutoffMacroTrace(t)

    @tailrec def cutoffMacroTrace(t: Throwable): Unit = {
      val trace = t.getStackTrace
      val cutoffIdx = Some(trace.indexWhere(_.getClassName contains "scala.reflect.macros.runtime.JavaReflectionRuntimes$JavaReflectionResolvers")).filter(_ > 0)
      t.setStackTrace(cutoffIdx.fold(trace)(trace.take))
      val suppressed = t.getSuppressed
      suppressed.foreach(cutSuppressed)
      if (t.getCause ne null) cutoffMacroTrace(t.getCause)
    }

    def handlePlanError(t: Throwable, plan: Option[OrderedPlan]): PlanCheckResult.Incorrect = {
      val message = {
        cutoffMacroTrace(t)
        val printedPlan = plan.filter(_ => printPlan).fold("")("Plan was:\n" + _.render() + "\n\n")
        s"""Found a problem with your DI wiring, when checking wiring of application=${roleAppMain.getClass.getName.split('.').last.split('$').last}, with parameters:
           |
           |  roles       = ${chosenRoles.fold(effectiveRoles)(_.toSeq.sorted.mkString(" "))}
           |  activations = ${chosenActivations.fold(effectiveActivations)(_.map(_.map(t => t._1 + ":" + t._2).mkString(" ")).mkString(" | "))}
           |  config      = ${chosenConfig.fold(effectiveConfig)("resource:" + _)}
           |  checkConfig = $checkConfig
           |  printPlan   = $printPlan${if (!printPlan) ", set to `true` for plan printout" else ""}
           |
           |You may ignore this error by setting system property `-D${DebugProperties.`izumi.distage.plancheck.onlywarn`.name}=true`
           |
           |$printedPlan${t.stackTrace}
           |""".stripMargin
      }

      PlanCheckResult.Incorrect(effectiveLoadedPlugins, new InvalidPlanException(message, plan, omitClassName = true, captureStackTrace = false))
    }

    try {
      val baseModule = roleAppMain.finalAppModule(ArgV(Array.empty))
      val allKeysProvidedByBaseModule = baseModule.keys
      import roleAppMain.AppEffectType
      def combine[F[_]: TagK]: Tag[DefaultModule[F]] = Tag[DefaultModule[F]]
      // FIXME: remove defaultmoudle
      implicit val tg: Tag[DefaultModule[AppEffectType]] = combine[AppEffectType](roleAppMain.tagK)
      object xa {
        type T <: DefaultModule[AppEffectType]
      }
      implicit val t: Tag[xa.T] = tg.asInstanceOf[Tag[xa.T]]

      Injector[Identity]().produceRun(
        baseModule overriddenBy mainAppModulePlanCheckerOverrides(chosenRoles, chosenConfig.map((roleAppMain.getClass.getClassLoader, _)))
      )(Functoid {
        (
          bootloader: Bootloader @Id("roleapp"),
          bsModule: BootstrapModule @Id("roleapp"),
          activationChoicesExtractor: ActivationChoicesExtractor,
          roleAppActivationParser: RoleAppActivationParser,
          configLoader: ConfigLoader,
          activationInfo: ActivationInfo,
          locatorRef: LocatorRef,
          appPlugins: LoadedPlugins @Id("main"),
          bsPlugins: LoadedPlugins @Id("bootstrap"),
          // fixme:
          rolesInfo: RolesInfo,
          // fixme:
          defaultModule: xa.T,
        ) =>
          val loadedPlugins = appPlugins ++ bsPlugins

          effectiveLoadedPlugins = loadedPlugins
          effectiveRoles = s"* (effective: ${rolesInfo.requiredRoleBindings.map(_.descriptor.id).mkString(" ")})"

          val allKeysFromRoleAppMainModule = {
            val keysUsedInBaseModule = locatorRef.get.allInstances.iterator.map(_.key).toSet
            keysUsedInBaseModule ++ allKeysProvidedByBaseModule
          }

          val bindings0000 = bootloader.input.bindings
          val reachables0000 = locally {
            // need to ignore bsModule + Injector parent + defaults too, need to check F type

            // OrderedPlanOps#isValid should use PlannerInputVerifier (?)
//            app.plan.assertValidOrThrow[F](k => allKeysFromRoleAppMainModule(k))

            val veryBadInjectorContext = {
              bootloader
                .boot(
                  BootConfig(
                    bootstrap = _ => BootstrapModule.empty,
                    appModule = _ => Module.empty,
                    roots = _ => Roots.Everything,
                  )
                ).injector.produceGet[LocatorRef](Module.empty).use(_.get.allInstances.map(_.key)).toSet
            }
            val PlanVerifierResult(issues, reachableKeys) = PlanVerifier().verify(
              bindings = bindings0000,
              roots = Roots(rolesInfo.requiredComponents),
              providedImports = allKeysFromRoleAppMainModule ++
                bsModule.keys ++
                defaultModule.module.keys ++
                veryBadInjectorContext +
                DIKey[LocatorRef],
            )
            if (!issues.forall(_.isInstanceOf[UnsaturatedAxis])) {
              throw new java.lang.RuntimeException(s"${issues.niceList()}") { override def fillInStackTrace(): Throwable = this }
            }
            if (issues.nonEmpty) {
              System.err.println(s"bad: ${issues.niceList()}")
            }
            reachableKeys
          }

          // FIXME: config to enable bruteforce
          val bruteforce = false
          if (bruteforce) {
            val allChoices = chosenActivations match {
              case None =>
                val choices = activationChoicesExtractor.findAvailableChoices(bootloader.input.bindings).availableChoices
                effectiveActivations = s"* (effective: ${choices.map { case (k, vs) => s"$k:${vs.mkString("|")}" }.mkString(" ")})"

                allActivations(choices, defaultActivationsLimit)
              case Some(choiceSets) =>
                choiceSets.iterator.map(roleAppActivationParser.parseActivation(_, activationInfo)).toSet
            }

//          val maybeConcurrentConfigParsersMap = if (checkConfig) {
//            Some(ConcurrentHashMap.newKeySet[AppConfig => Any]())
//          } else None

            logger.log(s"RoleAppMain boot-up plan was: ${locatorRef.get.plan}")
            logger.log(s"Checking with allChoices=${allChoices.niceList()}")

            QuasiAsync.quasiAsyncIdentity.parTraverse_(allChoices) {
              checkPlanJob(bootloader, bsModule, allKeysFromRoleAppMainModule, logger)
            }
          }

          if (checkConfig) {
            val realAppConfig = configLoader.loadConfig()
            effectiveConfig = s"* (effective: `${realAppConfig.config.origin()}`)"
            // FIXME: does not consider bsModule for reachability [must test it]
            val parsersIter = bindings0000
              .iterator
              .filter(b => b.tags.exists(_.isInstanceOf[ConfTag]) && reachables0000.contains(b.key))
              .flatMap(_.tags.iterator.collect { case c: ConfTag => c.parser })
              .toList
            parsersIter.foreach(_.apply(realAppConfig))
          }

          PlanCheckResult.Correct(loadedPlugins)
      })
    } catch {
      case t: Throwable =>
        val plan = t match {
          case t: InvalidPlanException => t.plan
          case _ => None
        }
        handlePlanError(t, plan)
    }
  }

  def mainAppModulePlanCheckerOverrides(
    chosenRoles: Option[Set[String]],
    chosenConfigResource: Option[(ClassLoader, String)],
  ): ModuleDef = new ModuleDef {
    make[IzLogger].named("early").fromValue(IzLogger.NullLogger)
    make[IzLogger].fromValue(IzLogger.NullLogger)
    make[AppConfig].fromValue(AppConfig.empty)
    make[RawAppArgs].fromValue(RawAppArgs.empty)
    make[RoleProvider].from {
      chosenRoles match {
        case None =>
          @impl trait AllRolesProvider extends RoleProvider.Impl {
            override protected def isRoleEnabled(requiredRoles: Set[String])(b: RoleBinding): Boolean = true
          }
          TraitConstructor[AllRolesProvider]
        case Some(chosenRoles) =>
          @impl trait ConfiguredRoleProvider extends RoleProvider.Impl {
            override protected def getInfo(bindings: Set[Binding], @unused requiredRoles: Set[String], roleType: SafeType): RolesInfo = {
              super.getInfo(bindings, chosenRoles, roleType)
            }
          }
          TraitConstructor[ConfiguredRoleProvider]
      }
    }
    make[ConfigLoader.Args].from {
      chosenRoles match {
        case Some(roleNames) =>
          Functoid(() => ConfigLoader.Args.forEnabledRoles(roleNames))
        case None =>
          // use all roles
          Functoid(ConfigLoader.Args forEnabledRoles (_: RolesInfo).availableRoleNames)
      }
    }
    chosenConfigResource match {
      case Some((classLoader, resourceName)) =>
        make[ConfigLoader].fromValue[ConfigLoader](
          () =>
            AppConfig {
              val cfg = ConfigFactory.parseResources(classLoader, resourceName).resolve()
              if (cfg.origin().resource() eq null) {
                throw new DIConfigReadException(s"Couldn't find a config resource with name `$resourceName` - file not found", null)
              }
              cfg
            }
        )
      case None => // use original ConfigLoader
    }
  }

  private[this] def checkPlanJob[F[_]: TagK](
    bootloader: Bootloader,
    bsModule: BootstrapModule,
    allKeysFromRoleAppMainModule: Set[DIKey],
    logger: TrivialLogger,
  )(activation: Activation
  ): Unit = {
    val app = bootloader.boot(
      BootConfig(
        bootstrap = _ => bsModule,
        activation = _ => activation,
      )
    )
    logger.log(s"Checking for activation=$activation, plan=${app.plan}")

    app.plan.assertValidOrThrow[F](k => allKeysFromRoleAppMainModule(k))
  }

  private[this] def parseRoles(s: String): Set[String] = {
    s.split(" ").iterator.filter(_.nonEmpty).map(_.stripPrefix(":")).toSet
  }

  private[this] def parseActivations(s: String): Array[Iterable[(String, String)]] = {
    s.split(" *\\| *").map(_.split(" ").iterator.filter(_.nonEmpty).map(AxisValue.splitAxisValue).toSeq)
  }

  private[this] def allActivations(allPossibleChoices: Map[Axis, Set[AxisValue]], limit: Int): Set[Activation] = {
    var counter = 0
    var printed = false
    def go(accumulator: Activation, axisValues: Map[Axis, Set[AxisValue]]): Set[Activation] = {
      if (counter >= limit) {
        if (!printed) {
          printed = true
          System.err.println(s"WARN: too many possible activations, over $limit, will not consider further possibilities (check back soon for a non-bruteforce checker)")
        }
        Set.empty
      } else {
        axisValues.headOption match {
          case Some((axis, allChoices)) =>
            allChoices.flatMap {
              choice =>
                go(accumulator ++ Activation(axis -> choice), axisValues.tail)
            }
          case None =>
            counter += 1
            Set(accumulator)
        }
      }
    }
    go(Activation.empty, allPossibleChoices)
  }

  private[this] def defaultLogger: TrivialLogger = {
    TrivialLogger.make[this.type](DebugProperties.`izumi.debug.macro.distage.plancheck`.name)
  }

}
