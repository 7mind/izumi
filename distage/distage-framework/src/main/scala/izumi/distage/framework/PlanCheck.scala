package izumi.distage.framework

import com.typesafe.config.ConfigFactory
import distage.{DefaultModule, Injector}
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.config.model.{AppConfig, ConfTag}
import izumi.distage.constructors.TraitConstructor
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition._
import izumi.distage.model.exceptions.PlanCheckException
import izumi.distage.model.plan.Roots
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.planning.AxisPoint
import izumi.distage.model.providers.Functoid
import izumi.distage.model.recursive.{BootConfig, Bootloader, LocatorRef}
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.{PlanIssue, PlanVerifierConfig, PlanVerifierResult}
import izumi.distage.plugins.load.LoadedPlugins
import izumi.distage.roles.PlanHolder
import izumi.distage.roles.launcher.RoleProvider
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.fundamentals.collections.nonempty.NonEmptySet
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.logstage.api.IzLogger
import izumi.reflect.{Tag, TagK}

import scala.annotation.{nowarn, tailrec}

object PlanCheck {
  final val defaultCheckConfig = DebugProperties.`izumi.distage.plancheck.check-config`.boolValue(true)

  sealed trait PlanCheckResult {
    def loadedPlugins: LoadedPlugins
    def maybeError: Option[Either[Throwable, NonEmptySet[PlanIssue]]]
    def maybeErrorMessage: Option[String]

    final def throwOnError(): LoadedPlugins = this match {
      case PlanCheckResult.Correct(loadedPlugins) => loadedPlugins
      case PlanCheckResult.Incorrect(_, message, _) => throw new PlanCheckException(message)
    }
  }
  object PlanCheckResult {
    final case class Correct(loadedPlugins: LoadedPlugins) extends PlanCheckResult {
      override def maybeError: Option[Either[Throwable, NonEmptySet[PlanIssue]]] = None
      override def maybeErrorMessage: Option[String] = None
    }
    final case class Incorrect(loadedPlugins: LoadedPlugins, message: String, cause: Either[Throwable, NonEmptySet[PlanIssue]]) extends PlanCheckResult {
      override def maybeError: Option[Either[Throwable, NonEmptySet[PlanIssue]]] = Some(cause)
      override def maybeErrorMessage: Option[String] = Some(message)
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
    * @param excludeActivations "repo:dummy" to ignore missing implementations or other issues in `repo:dummy` axis choice.
    *
    *                           "repo:dummy | scene:managed" to ignore missing implementations or other issues in `repo:dummy` axis choice and in `scene:managed` axis choice.
    *
    *                           "repo:dummy mode:test | scene:managed" to ignore missing implementations or other issues in `repo:dummy mode:test` activation and in `scene:managed` activation.
    *                           This will ignore parts of the graph accessible through these activations and larger activations that include them.
    *                           That is, anything involving `scene:managed` or the combination of both `repo:dummy mode:test` will not be checked.
    *                           but activations `repo:prod mode:test scene:provided` and `repo:dummy mode:prod scene:provided` are not excluded and will be checked.
    *
    *                           Allows the check to pass even if some axis choices or combinations of choices are (wilfully) left invalid,
    *                           e.g. if you do have `repo:prod` components, but no counterpart `repo:dummy` components,
    *                                and don't want to add them, then you may exclude "repo:dummy" from being checked.
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
    excludedActivations: Set[NonEmptySet[AxisPoint]],
    chosenConfig: Option[String],
    checkConfig: Boolean,
    logger: TrivialLogger = makeDefaultLogger(),
  ): PlanCheckResult = {

    var effectiveRoles = "unknown, failed too early"
    var effectivePlugins = "unknown, failed too early"
    var effectiveConfig = "unknown, failed too early"
    var effectiveLoadedPlugins = LoadedPlugins.empty

    def returnPlanCheckError(cause: Either[Throwable, NonEmptySet[PlanIssue]]): PlanCheckResult.Incorrect = {
      val message = {
        val errorMsg = cause.fold(_.toString, _.toSet.niceList())
        val configStr = if (checkConfig) {
          s"\n  config              = ${chosenConfig.fold("*")(c => s"resource:$c")} (effective: $effectiveConfig)"
        } else {
          ""
        }
        s"""Found a problem with your DI wiring, when checking application=${roleAppMain.getClass.getName.split('.').last.split('$').last}, with parameters:
           |
           |  roles               = $chosenRoles (effective: $effectiveRoles)
           |  excludedActivations = ${excludedActivations.mkString(" ")}
           |  plugins             = $effectivePlugins
           |  checkConfig         = $checkConfig$configStr
           |
           |You may ignore this error by setting system property `-D${DebugProperties.`izumi.distage.plancheck.onlywarn`.name}=true`
           |
           |$errorMsg
           |""".stripMargin
      }

      PlanCheckResult.Incorrect(effectiveLoadedPlugins, message, cause)
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
          effectivePlugins = appPlugins.toString

          val allKeysFromRoleAppMainModule = {
            val keysUsedInBaseModule = locatorRef.get.allInstances.iterator.map(_.key).toSet
            keysUsedInBaseModule ++ allKeysProvidedByBaseModule
          }

          val bindings = bootloader.input.bindings

          val PlanVerifierResult(issues, reachableKeys) = {
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
            PlanVerifier().verify(
              bindings = bindings,
              roots = Roots(rolesInfo.requiredComponents),
              planVerifierConfig = PlanVerifierConfig(
                providedKeys = allKeysFromRoleAppMainModule ++
                  bsModule.keys ++
                  defaultModule.module.keys ++
                  veryBadInjectorContext +
                  DIKey[LocatorRef],
                excludedActivations = excludedActivations,
              ),
            )
          }

          val configIssues = if (checkConfig) {
            val realAppConfig = configLoader.loadConfig()
            effectiveConfig = realAppConfig.config.origin().toString
            // FIXME: does not consider bsModule for reachability [must test it]
            bindings.iterator
              .filter(reachableKeys contains _.key)
              .flatMap(b => b.tags.iterator.collect { case c: ConfTag => (b, c.parser) })
              .flatMap {
                case (b, parser) =>
                  try { parser(realAppConfig); None }
                  catch {
                    case t: Throwable =>
                      cutoffMacroTrace(t)
                      Some(PlanIssue.UnparseableConfigBinding(b.key, OperationOrigin.UserBinding(b), t))
                  }
              }.toList
          } else {
            Nil
          }

          NonEmptySet.from(issues ++ configIssues) match {
            case Some(allIssues) =>
              returnPlanCheckError(Right(allIssues))
            case None =>
              PlanCheckResult.Correct(loadedPlugins)
          }
      })
    } catch {
      case t: Throwable =>
        cutoffMacroTrace(t)
        returnPlanCheckError(Left(t))
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

  private[this] def parseActivations(s: String): Set[NonEmptySet[AxisPoint]] = {
    s.split("\\|").iterator.filter(_.nonEmpty).flatMap {
        NonEmptySet from _.split(" ").iterator.filter(_.nonEmpty).map(AxisPoint.parseAxisPoint).toSet
      }.toSet
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
