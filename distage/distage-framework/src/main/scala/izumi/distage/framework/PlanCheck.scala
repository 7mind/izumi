package izumi.distage.framework

import com.typesafe.config.ConfigFactory
import distage.{DefaultModule, Injector}
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.config.model.{AppConfig, ConfTag}
import izumi.distage.constructors.TraitConstructor
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition.Axis.AxisPoint
import izumi.distage.model.definition._
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
import izumi.distage.roles.launcher.RoleProvider
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.logstage.api.IzLogger
import izumi.reflect.{Tag, TagK}

import scala.annotation.{nowarn, tailrec}

object PlanCheck {
  final val defaultCheckConfig = DebugProperties.`izumi.distage.plancheck.check-config`.boolValue(true)

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
    *
    * @param roles "*" to check all roles,
    *
    *              "role1 role2" to check specific roles,
    *
    *              "* -role1 -role2" to check all roles _except_ specific roles.
    *
    * @param excludeActivations "axis1:choice1 axis2:choice2" to ignore missing components for any of the specified axis choices.
    *
    *                           Allows the check to pass even if some axis choices are (wilfully) invalid,
    *                           e.g. if you have no `dummy` components for the `repo:dummy` choice, you may exclude "repo:dummy" from being checked.
    *
    * @param config      Config resource file name, e.g. "application.conf" or "*" if using the same config settings as `roleAppMain`
    *
    * @param checkConfig Try to parse config file checking all the config bindings added using [[izumi.distage.config.ConfigModuleDef]]
    */
  def checkRoleApp(
    roleAppMain: PlanHolder,
    roles: String = "*",
    excludeActivations: String = "",
    config: String = "*",
    checkConfig: Boolean = defaultCheckConfig,
    logger: TrivialLogger = makeDefaultLogger(),
  ): PlanCheckResult = {
    val chosenRoles = parseRoles(roles)
    val chosenActivations = parseActivations(excludeActivations)
    val chosenConfig = if (config == "*") None else Some(config)

    checkRoleAppParsed(roleAppMain, chosenRoles, chosenActivations, chosenConfig, checkConfig, logger)
  }

  def checkRoleAppParsed(
    roleAppMain: PlanHolder,
    chosenRoles: RoleSelection,
    excludedActivations: Set[AxisPoint],
    chosenConfig: Option[String],
    checkConfig: Boolean,
    logger: TrivialLogger = makeDefaultLogger(),
  ): PlanCheckResult = {

    var effectiveRoles = "unknown, failed too early"
    var effectiveConfig = "unknown, failed too early"
    var effectiveLoadedPlugins = LoadedPlugins.empty

    def handlePlanError(t: Throwable, plan: Option[OrderedPlan]): PlanCheckResult.Incorrect = {
      val message = {
        cutoffMacroTrace(t)
        val confiStr = if (checkConfig) {
          s"\n  config              = ${chosenConfig.fold("*")(c => s"resource:$c")} (effective: $effectiveConfig)"
        } else {
          ""
        }
        s"""Found a problem with your DI wiring, when checking application=${roleAppMain.getClass.getName.split('.').last.split('$').last}, with parameters:
           |
           |  roles               = $chosenRoles (effective: $effectiveRoles)
           |  excludedActivations = ${excludedActivations.mkString(" ")}
           |  checkConfig         = $checkConfig$confiStr
           |
           |You may ignore this error by setting system property `-D${DebugProperties.`izumi.distage.plancheck.onlywarn`.name}=true`
           |
           |${t.stackTrace}
           |""".stripMargin
      }

      PlanCheckResult.Incorrect(effectiveLoadedPlugins, new InvalidPlanException(message, plan, omitClassName = true, captureStackTrace = false))
    }

    try {
      val baseModule = roleAppMain.finalAppModule
      val allKeysProvidedByBaseModule = baseModule.keys
      import roleAppMain.AppEffectType
      def combine[F[_]: TagK]: Tag[DefaultModule[F]] = Tag[DefaultModule[F]]
      // FIXME: remove defaultmoudle
      implicit val tg: Tag[DefaultModule[AppEffectType]] = combine[AppEffectType](roleAppMain.tagK)
      object xa {
        type T <: DefaultModule[AppEffectType]
      }
      @nowarn implicit val t: Tag[xa.T] = tg.asInstanceOf[Tag[xa.T]]

      Injector[Identity]().produceRun(
        baseModule overriddenBy mainAppModulePlanCheckerOverrides(chosenRoles, chosenConfig.map((roleAppMain.getClass.getClassLoader, _)))
      )(Functoid {
        (
          bootloader: Bootloader @Id("roleapp"),
          bsModule: BootstrapModule @Id("roleapp"),
          appPlugins: LoadedPlugins @Id("main"),
          bsPlugins: LoadedPlugins @Id("bootstrap"),
          configLoader: ConfigLoader,
          locatorRef: LocatorRef,
          // fixme:
          rolesInfo: RolesInfo,
          // fixme:
          defaultModule: xa.T,
        ) =>
          logger.log(s"RoleAppMain boot-up plan was: ${locatorRef.get.plan}")
          logger.log(s"Checking with roles=`$chosenRoles` excludedActivations=$excludedActivations chosenConfig=$chosenConfig")

          val loadedPlugins = appPlugins ++ bsPlugins

          effectiveLoadedPlugins = loadedPlugins
          effectiveRoles = rolesInfo.requiredRoleBindings.map(_.descriptor.id).mkString(" ")

          val allKeysFromRoleAppMainModule = {
            val keysUsedInBaseModule = locatorRef.get.allInstances.iterator.map(_.key).toSet
            keysUsedInBaseModule ++ allKeysProvidedByBaseModule
          }

          val bindings = bootloader.input.bindings
          val reachableKeys: Set[DIKey] = {
            // need to ignore bsModule,  Injector parent, defaults
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
              bindings = bindings,
              roots = Roots(rolesInfo.requiredComponents),
              providedKeys = allKeysFromRoleAppMainModule ++
                bsModule.keys ++
                defaultModule.module.keys ++
                veryBadInjectorContext +
                DIKey[LocatorRef],
            )
            val verifiedIssues = issues.filterNot {
              case UnsaturatedAxis(_, _, missingAxisValues) => missingAxisValues.toSet.subsetOf(excludedActivations)
              case _ => false
            }
            if (verifiedIssues.nonEmpty) {
              throw new RuntimeException(s"${issues.niceList()}") { override def fillInStackTrace(): Throwable = this }
            } else {
              reachableKeys
            }
          }

          if (checkConfig) {
            val realAppConfig = configLoader.loadConfig()
            effectiveConfig = s"* (effective: `${realAppConfig.config.origin()}`)"
            // FIXME: does not consider bsModule for reachability [must test it]
            val parsers = bindings
              .iterator
              .filter(reachableKeys contains _.key)
              .flatMap(_.tags.iterator.collect { case c: ConfTag => c.parser })
              .toArray
            parsers.foreach(_.apply(realAppConfig))
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
    chosenRoles: RoleSelection,
    chosenConfigResource: Option[(ClassLoader, String)],
  ): ModuleDef = new ModuleDef {
    make[IzLogger].named("early").fromValue(IzLogger.NullLogger)
    make[IzLogger].fromValue(IzLogger.NullLogger)
    make[AppConfig].fromValue(AppConfig.empty)
    make[RawAppArgs].fromValue(RawAppArgs.empty)

    make[RoleProvider].from {
      def namePredicateRoleProvider(f: String => Boolean): Functoid[RoleProvider] = {
        @impl trait NamePredicateRoleProvider extends RoleProvider.Impl {
          override protected def isRoleEnabled(requiredRoles: Set[String])(b: RoleBinding): Boolean = {
            f(b.descriptor.id)
          }
          override protected def getInfo(bindings: Set[Binding], requiredRoles: Set[String], roleType: SafeType): RolesInfo = {
            requiredRoles.discard()
            super.getInfo(bindings, Set.empty, roleType)
          }
        }
        TraitConstructor[NamePredicateRoleProvider]
      }

      chosenRoles match {
        case RoleSelection.Everything => namePredicateRoleProvider(_ => true)
        case RoleSelection.AllExcluding(excluded) => namePredicateRoleProvider(!excluded(_))
        case RoleSelection.OnlySelected(selection) =>
          @impl trait SelectedRoleProvider extends RoleProvider.Impl {
            override protected def getInfo(bindings: Set[Binding], requiredRoles: Set[String], roleType: SafeType): RolesInfo = {
              requiredRoles.discard()
              super.getInfo(bindings, selection, roleType)
            }
          }
          TraitConstructor[SelectedRoleProvider]
      }
    }

    chosenConfigResource match {
      case Some((classLoader, resourceName)) =>
        make[ConfigLoader].fromValue[ConfigLoader] {
          () =>
            AppConfig {
              val cfg = ConfigFactory.parseResources(classLoader, resourceName).resolve()
              if (cfg.origin().resource() eq null) {
                throw new DIConfigReadException(s"Couldn't find a config resource with name `$resourceName` - file not found", null)
              }
              cfg
            }
        }
      case None =>
      // do not override original ConfigLoader
    }
  }

  sealed trait RoleSelection {
    override final def toString: String = this match {
      case RoleSelection.Everything => "*"
      case RoleSelection.OnlySelected(selection) => selection.mkString(" ")
      case RoleSelection.AllExcluding(excluded) => excluded.map(x => s"-$x").mkString(" ")
    }
  }
  object RoleSelection {
    case object Everything extends RoleSelection
    final case class OnlySelected(selection: Set[String]) extends RoleSelection
    final case class AllExcluding(excluded: Set[String]) extends RoleSelection
  }

  private[this] def parseRoles(s: String): RoleSelection = {
    val tokens = s.split(" ").iterator.filter(_.nonEmpty).toList
    tokens match {
      case "*" :: Nil =>
        RoleSelection.Everything
      case "*" :: exclusions if exclusions.forall(_.startsWith("-")) =>
        RoleSelection.AllExcluding(exclusions.iterator.map(_.stripPrefix("-")).toSet)
      case inclusions if !inclusions.exists(_.startsWith("-")) =>
        RoleSelection.OnlySelected(inclusions.iterator.map(_.stripPrefix(":")).toSet)
      case _ =>
        throwInvalidRoleSelectionError(s)
    }
  }

  private[this] def throwInvalidRoleSelectionError(s: String): Nothing = {
    throw new IllegalArgumentException(
      s"""Invalid role selection syntax in `$s`.
         |
         |Valid syntaxes:
         |
         |  - "*" to check all roles,
         |  - "role1 role2" to check specific roles,
         |  - "* -role1 -role2" to check all roles _except_ specific roles.
         |""".stripMargin
    )
  }

  private[this] def parseActivations(s: String): Set[AxisPoint] = {
    s.split(" ").iterator.filter(_.nonEmpty).map(AxisPoint.parseAxisPoint).toSet
  }

  private[this] def makeDefaultLogger(): TrivialLogger = {
    TrivialLogger.make[this.type](DebugProperties.`izumi.debug.macro.distage.plancheck`.name)
  }

  @tailrec private[this] def cutoffMacroTrace(t: Throwable): Unit = {
    val trace = t.getStackTrace
    val cutoffIdx = Some(trace.indexWhere(_.getClassName contains "scala.reflect.macros.runtime.JavaReflectionRuntimes$JavaReflectionResolvers")).filter(_ > 0)
    t.setStackTrace(cutoffIdx.fold(trace)(trace.take))
    val suppressed = t.getSuppressed
    suppressed.foreach(cutSuppressed)
    if (t.getCause ne null) cutoffMacroTrace(t.getCause)
  }
  // indirection for tailrec
  private[this] def cutSuppressed(t: Throwable): Unit = cutoffMacroTrace(t)

}
