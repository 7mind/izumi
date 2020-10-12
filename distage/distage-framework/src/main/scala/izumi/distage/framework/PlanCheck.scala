package izumi.distage.framework

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.config.ConfigFactory
import distage.Injector
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.config.model.{AppConfig, ConfTag}
import izumi.distage.constructors.TraitConstructor
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.{ActivationChoicesExtractor, ConfigLoader}
import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition._
import izumi.distage.model.effect.DIEffectAsync
import izumi.distage.model.exceptions.InvalidPlanException
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.providers.Functoid
import izumi.distage.model.recursive.{BootConfig, Bootloader, LocatorRef}
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.distage.plugins.load.LoadedPlugins
import izumi.distage.roles.PlanHolder
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.ActivationParser.activationKV
import izumi.distage.roles.launcher.{RoleAppActivationParser, RoleProvider}
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.strings.IzString.{toRichIterable, toRichString}
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

object PlanCheck {
  final val defaultActivationsLimit = DebugProperties.`distage.plancheck.max-activations`.strValue().fold(9000)(_.asInt(9000))
  final val defaultCheckConfig = DebugProperties.`distage.plancheck.check-config`.boolValue(true)
  final val defaultPrintPlan = DebugProperties.`distage.plancheck.print-plan`.boolValue(false)

  /**
    * @param roleAppMain ...
    * @param roles       "*" to check all roles
    * @param activations "*" to check all possible activations
    * @param config      Config resource file name, e.g. "application.conf" or "*" if using the same config settings as `roleAppMain`
    * @param checkConfig Try to parse config file checking all the config bindings added using [[izumi.distage.config.ConfigModuleDef]]
    * @param limit       Upper limit on possible activation checks, default = 9000
    */
  def checkRoleApp(
    roleAppMain: PlanHolder,
    roles: String = "*",
    activations: String = "*",
    config: String = "*",
    checkConfig: Boolean = defaultCheckConfig,
    printPlan: Boolean = defaultPrintPlan,
    limit: Int = defaultActivationsLimit,
    logger: TrivialLogger = defaultLogger,
  ): LoadedPlugins = {
    val chosenRoles = if (roles == "*") None else Some(parseRoles(roles))
    val chosenActivations = if (activations == "*") None else Some(parseActivations(activations))
    val chosenConfig = if (config == "*") None else Some(config)

    checkRoleAppParsed(roleAppMain, chosenRoles, chosenActivations, chosenConfig, checkConfig, printPlan, limit, logger)
  }

  def checkRoleAppParsed(
    roleAppMain: PlanHolder,
    chosenRoles: Option[Set[String]],
    chosenActivations: Option[Array[Iterable[(String, String)]]],
    chosenConfig: Option[String],
    checkConfig: Boolean,
    printPlan: Boolean,
    limit: Int,
    logger: TrivialLogger = defaultLogger,
  ): LoadedPlugins = {

    var effectiveRoles = "* (effective: unknown, failed too early)"
    var effectiveActivations = "* (effective: unknown, failed too early)"
    var effectiveConfig = "*"
    def message(t: Throwable, plan: Option[OrderedPlan]): String = {
      val trace = t.getStackTrace
      val cutoffIdx = Some(trace.indexWhere(_.getClassName contains "scala.reflect.macros.runtime.JavaReflectionRuntimes$JavaReflectionResolvers")).filter(_ > 0)
      t.setStackTrace(cutoffIdx.fold(trace)(trace.take))

      val printedPlan = plan.filter(_ => printPlan).fold("")("Plan was:\n" + _.render() + "\n\n")

      s"""Found a problem with your DI wiring, when checking wiring of application=${roleAppMain.getClass.getName.split('.').last.split('$').last}, with parameters:
         |
         |  roles       = ${chosenRoles.fold(effectiveRoles)(_.toSeq.sorted.mkString(" "))}
         |  activations = ${chosenActivations.fold(effectiveActivations)(_.map(_.map(t => t._1 + ":" + t._2).mkString(" ")).mkString(" | "))}
         |  config      = ${chosenConfig.fold(effectiveConfig)("resource:" + _)}
         |  checkConfig = $checkConfig
         |  printPlan   = $printPlan${if (!printPlan) ", set to `true` for plan printout" else ""}
         |
         |$printedPlan${t.stackTrace}
         |""".stripMargin
    }

    try Injector[Identity]().produceRun(roleAppMain.finalAppModule(ArgV(Array.empty)) overriddenBy new ModuleDef {
      make[IzLogger].named("early").fromValue(IzLogger.NullLogger)
      make[IzLogger].fromValue(IzLogger.NullLogger)
      make[AppConfig].fromValue(AppConfig.empty)
      make[RawAppArgs].fromValue(RawAppArgs.empty)
      make[Activation].named("roleapp").todo // TODO
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
      chosenConfig match {
        case Some(resourceName) =>
          make[ConfigLoader].fromValue[ConfigLoader](
            () =>
              AppConfig {
                val cfg = ConfigFactory.parseResources(roleAppMain.getClass.getClassLoader, resourceName).resolve()
                if (cfg.origin().resource() eq null) {
                  throw new DIConfigReadException(s"Couldn't find a config resource with name `$resourceName` - file not found", null)
                }
                cfg
              }
          )
        case None => // use original ConfigLoader
      }
    })(Functoid {
      (
        bootloader: Bootloader @Id("roleapp"),
        bsModule: BootstrapModule @Id("roleapp"),
        activationChoicesExtractor: ActivationChoicesExtractor,
        roleAppActivationParser: RoleAppActivationParser,
        configLoader: ConfigLoader,
        rolesInfo: RolesInfo,
        activationInfo: ActivationInfo,
        locatorRef: LocatorRef,
        appPlugins: LoadedPlugins @Id("main"),
        bsPlugins: LoadedPlugins @Id("bootstrap"),
      ) =>
        effectiveRoles = s"* (effective: ${rolesInfo.requiredRoleBindings.map(_.descriptor.id).mkString(" ")})"

        val allChoices = chosenActivations match {
          case None =>
            val choices = activationChoicesExtractor.findAvailableChoices(bootloader.input.bindings).availableChoices
            effectiveActivations = s"* (effective: ${choices.map { case (k, vs) => s"$k:${vs.mkString("|")}" }.mkString(" ")})"
            allActivations(choices, limit)
          case Some(choiceSets) =>
            choiceSets.iterator.map(roleAppActivationParser.parseActivation(_, activationInfo)).toSet
        }
        logger.log(s"RoleAppMain boot-up plan was: ${locatorRef.get.plan}")
        logger.log(s"Checking with allChoices=${allChoices.niceList()}")

        val allKeysFromRoleAppMainModule = {
          val keysUsedInBootstrap = locatorRef.get.allInstances.iterator.map(_.key).toSet
          val keysThatCouldveBeenInBootstrap = roleAppMain.finalAppModule(ArgV(Array.empty)).keys
          keysUsedInBootstrap ++ keysThatCouldveBeenInBootstrap
        }

        val maybeConcurrentConfigParsersMap = if (checkConfig) Some(ConcurrentHashMap.newKeySet[AppConfig => Any]()) else None

        //allChoices.foreach {
        DIEffectAsync.diEffectParIdentity.parTraverse_(allChoices) {
          checkPlanJob(bootloader, bsModule, allKeysFromRoleAppMainModule, logger, maybeConcurrentConfigParsersMap)
        }

        maybeConcurrentConfigParsersMap.foreach {
          chm =>
            val realAppConfig = configLoader.loadConfig()
            effectiveConfig = s"* (effective: ${realAppConfig.config.origin()})"
            chm.forEach(_.apply(realAppConfig).discard())
        }

        appPlugins ++ bsPlugins
    })
    catch {
      case t: InvalidPlanException =>
        throw new InvalidPlanException(message(t, t.plan), t.plan, omitClassName = true, captureStackTrace = false)
      case t: Throwable =>
        throw new InvalidPlanException(message(t, None), None, omitClassName = true, captureStackTrace = false)
    }
  }

  private[this] def checkPlanJob[F[_]: TagK](
    bootloader: Bootloader,
    bsModule: BootstrapModule,
    allKeysFromRoleAppMainModule: Set[DIKey],
    logger: TrivialLogger,
    maybeChm: Option[ConcurrentHashMap.KeySetView[AppConfig => Any, java.lang.Boolean]],
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

    // bindings can have arbitrary relationships with the rest of the graph which would force us to run the check
    // for all possible activations, we just collect all the parsers added via `makeConfig` and execute them on
    // an `AppConfig` once to check that the default config is well-formed.
    maybeChm.foreach {
      chm =>
        app.plan.steps.foreach {
          _.origin
            .value.fold(
              onUnknown = (),
              onDefined = _.tags.foreach {
                case c: ConfTag => chm.add(c.parser)
                case _ => ()
              },
            )
        }
    }
  }

  private[this] def parseRoles(s: String): Set[String] = {
    s.split(" ").iterator.filter(_.nonEmpty).map(_.stripPrefix(":")).toSet
  }

  private[this] def parseActivations(s: String): Array[Iterable[(String, String)]] = {
    s.split(" *\\| *").map(_.split(" ").iterator.filter(_.nonEmpty).map(activationKV).toSeq)
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
    TrivialLogger.make[this.type](DebugProperties.`distage.plancheck.debug`.name)
  }

}
