package izumi.distage.framework

import com.typesafe.config.ConfigFactory
import distage.Injector
import izumi.distage.InjectorFactory
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.config.model.{AppConfig, ConfTag}
import izumi.distage.constructors.TraitConstructor
import izumi.distage.framework.model.PlanCheckResult
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition._
import izumi.distage.model.plan.Roots
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.planning.AxisPoint
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.distage.modules.DefaultModule
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.{PlanIssue, PlanVerifierResult}
import izumi.distage.plugins.load.LoadedPlugins
import izumi.distage.roles.PlanHolder
import izumi.distage.roles.launcher.RoleProvider
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.fundamentals.collections.nonempty.NonEmptySet
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.exceptions.IzThrowable._
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.{Discarder, discard}
import izumi.fundamentals.platform.language.{open, unused}
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

import scala.annotation.tailrec

object PlanCheck {

  @open class Main[AppMain <: PlanHolder, Cfg <: PlanCheckConfig.Any](
    app: AppMain,
    cfg: Cfg = PlanCheckConfig.empty,
  )(implicit val planCheck: PlanCheckMaterializer[AppMain, Cfg]
  ) {
    discard(app, cfg)

    def main(@unused args: Array[String]): Unit = {
      assertAtRuntime()
    }

    def assertAtRuntime(): Unit = planCheck.assertAgainAtRuntime()
    def checkAtRuntime(): PlanCheckResult = planCheck.checkAgainAtRuntime()
  }

  def assertAppCompileTime[AppMain <: PlanHolder, Cfg <: PlanCheckConfig.Any](
    app: AppMain,
    cfg: Cfg = PlanCheckConfig.empty,
  )(implicit planCheckResult: PlanCheckMaterializer[AppMain, Cfg]
  ): PlanCheckMaterializer[AppMain, Cfg] = {
    discard(app, cfg)
    planCheckResult
  }

  object runtime {
    /** @throws izumi.distage.framework.model.exceptions.PlanCheckException on found issues */
    def assertApp(
      app: PlanHolder,
      cfg: PlanCheckConfig.Any = PlanCheckConfig.empty,
      logger: TrivialLogger = defaultLogger(),
    ): Unit = {
      checkApp(app, cfg, logger).throwOnError()
    }

    /** @return a list of issues, if any. Does not throw. */
    def checkApp(
      app: PlanHolder,
      cfg: PlanCheckConfig.Any = PlanCheckConfig.empty,
      logger: TrivialLogger = defaultLogger(),
    ): PlanCheckResult = {
      val chosenRoles = parseRoles(cfg.roles)
      val chosenActivations = parseActivations(cfg.excludeActivations)
      val chosenConfig = if (cfg.config == "*") None else Some(cfg.config)

      checkAppParsed[app.AppEffectType](app, chosenRoles, chosenActivations, chosenConfig, cfg.checkConfig, cfg.printBindings, cfg.onlyWarn, logger)
    }

    /** @return a list of issues, if any. Does not throw. */
    def checkAppParsed[F[_]](
      app: PlanHolder.Aux[F],
      chosenRoles: RoleSelection,
      excludedActivations: Set[NonEmptySet[AxisPoint]],
      chosenConfig: Option[String],
      checkConfig: Boolean,
      printBindings: Boolean,
      onlyWarn: Boolean = false,
      logger: TrivialLogger = defaultLogger(),
    ): PlanCheckResult = {

      var effectiveRoles = "unknown, failed too early"
      var effectiveConfig = "unknown, failed too early"
      var effectivePlugins = LoadedPlugins.empty

      def returnPlanCheckError(cause: Either[Throwable, PlanVerifierResult.Incorrect]): PlanCheckResult.Incorrect = {
        val visitedKeys = cause.fold(_ => Set.empty[DIKey], _.visitedKeys)
        val message = {
          val errorMsg = cause.fold("\n" + _.stackTrace, _.issues.fromNonEmptySet.niceList())
          val configStr = if (checkConfig) {
            s"\n  config              = ${chosenConfig.fold("*")(c => s"resource:$c")} (effective: $effectiveConfig)"
          } else {
            ""
          }
          val plugins = effectivePlugins.result
          val pluginStr = {
            val pluginClasses = plugins.map(p => s"${p.getClass.getName} (${p.bindings.size} bindings)")
            if (pluginClasses.isEmpty) {
              "<none>"
            } else if (pluginClasses.size > 7) {
              val otherPlugins = plugins.drop(7)
              val otherBindingsSize = otherPlugins.map(_.bindings.size).sum
              (pluginClasses.take(7) :+ s"<${otherPlugins.size} plugins omitted with $otherBindingsSize bindings>").mkString(", ")
            } else {
              pluginClasses.mkString(", ")
            }
          }
          val printedBindings = if (printBindings) {
            s"""Bindings were:
               |${plugins.flatMap(_.iterator.map(_.toString)).niceList()}
               |
               |Keys visited:
               |${visitedKeys.niceList()}
               |
               |""".stripMargin
          } else ""

          s"""Found a problem with your DI wiring, when checking application=${app.getClass.getName.split('.').last.split('$').last}, with parameters:
             |
             |  roles               = $chosenRoles (effective: $effectiveRoles)
             |  excludedActivations = ${excludedActivations.map(_.mkString(" ")).mkString(" | ")}
             |  plugins             = $pluginStr
             |  checkConfig         = $checkConfig$configStr
             |  printBindings       = $printBindings${if (!printBindings) ", set to `true` for full bindings printout" else ""}
             |  onlyWarn            = $onlyWarn${if (!onlyWarn) ", set to `true` to ignore compilation error" else ""}
             |
             |You may ignore this error by setting system property in sbt, `sbt -D${DebugProperties.`izumi.distage.plancheck.only-warn`.name}=true` or by adding the option to `.jvmopts` in project root.
             |
             |${printedBindings}Error was:
             |$errorMsg
             |""".stripMargin
        }

        PlanCheckResult.Incorrect(effectivePlugins, visitedKeys, message, cause)
      }

      val baseModuleOverrides = mainAppModulePlanCheckerOverrides(chosenRoles, chosenConfig.map(app.getClass.getClassLoader -> _))
      val baseModuleWithOverrides = app.mainAppModule.overriddenBy(baseModuleOverrides)

      try {
        import app.tagK

        Injector[Identity]().produceRun(baseModuleWithOverrides)(Functoid {
          (
            // module
            bsModule: BootstrapModule @Id("roleapp"),
            appModule: Module @Id("roleapp"),
            defaultModule: DefaultModule[F],
            // roots
            rolesInfo: RolesInfo,
            // config
            configLoader: ConfigLoader,
            // providedKeys
            injectorFactory: InjectorFactory,
            // effectivePlugins
            appPlugins: LoadedPlugins @Id("main"),
            bsPlugins: LoadedPlugins @Id("bootstrap"),
          ) =>
            logger.log(s"Checking with roles=`$chosenRoles` excludedActivations=$excludedActivations chosenConfig=$chosenConfig")

            checkRoleApp(
              excludedActivations,
              checkConfig,
            )(
              effectivePlugins = _,
              effectiveRoles = _,
              effectiveConfig = _,
              r => returnPlanCheckError(Right(r)),
            )(
              bsModule,
              appModule,
              defaultModule,
              rolesInfo,
              configLoader,
              injectorFactory,
              appPlugins ++ bsPlugins,
            )
        })
      } catch {
        case t: Throwable =>
          cutoffMacroTrace(t)
          returnPlanCheckError(Left(t))
      }
    }

    def checkCoreApp = {
      ???
    }

    private[this] def checkRoleApp[F[_]: TagK](
      //    logger: IzLogger,
      excludedActivations: Set[NonEmptySet[AxisPoint]],
      checkConfig: Boolean,
    )(reportEffectivePlugins: LoadedPlugins => Unit,
      reportEffectiveRoles: String => Unit,
      reportEffectiveConfig: String => Unit,
      returnPlanCheckError: PlanVerifierResult.Incorrect => PlanCheckResult,
    )(bsModule: BootstrapModule,
      appModule: Module,
      defaultModule: DefaultModule[F],
      rolesInfo: RolesInfo,
      configLoader: ConfigLoader,
      injectorFactory: InjectorFactory,
      loadedPlugins: LoadedPlugins,
    ): PlanCheckResult = {

      locally {
        reportEffectivePlugins(loadedPlugins)
        reportEffectiveRoles(rolesInfo.requiredRoleBindings.map(_.descriptor.id).mkString(" "))
      }

      val providedKeys = {
        injectorFactory.providedKeys(bsModule) ++
        defaultModule.module.keys
      }
      val planVerifierResult = PlanVerifier().verify[F](
        bindings = appModule,
        roots = Roots(rolesInfo.requiredComponents),
        providedKeys = providedKeys,
        excludedActivations = excludedActivations,
      )
      val reachableKeys = providedKeys ++ planVerifierResult.visitedKeys

      val configIssues = if (checkConfig) {
        val realAppConfig = configLoader.loadConfig()
        reportEffectiveConfig(realAppConfig.config.origin().toString)

        bsModule.iterator
          .++(defaultModule.module.iterator)
          .++(appModule.iterator)
          .filter(reachableKeys contains _.key)
          .flatMap(
            b =>
              b.tags.iterator.collect {
                case c: ConfTag => (b, c.parser)
              }
          )
          .flatMap {
            case (b, parser) =>
              try {
                parser(realAppConfig)
                None
              } catch {
                case t: Throwable =>
                  cutoffMacroTrace(t)
                  Some(PlanIssue.UnparseableConfigBinding(b.key, OperationOrigin.UserBinding(b), t))
              }
          }.toList
      } else {
        Nil
      }

      NonEmptySet.from(planVerifierResult.issues.fromNonEmptySet ++ configIssues) match {
        case Some(allIssues) =>
          returnPlanCheckError(PlanVerifierResult.Incorrect(Some(allIssues), planVerifierResult.visitedKeys))
        case None =>
          PlanCheckResult.Correct(loadedPlugins, planVerifierResult.visitedKeys)
      }
    }

    def mainAppModulePlanCheckerOverrides(
      chosenRoles: RoleSelection,
      chosenConfigResource: Option[(ClassLoader, String)],
    ): ModuleDef = {
      new ModuleDef {
        make[IzLogger].named("early").fromValue(IzLogger.NullLogger)
        make[IzLogger].fromValue(IzLogger.NullLogger)

        make[AppConfig].fromValue(AppConfig.empty)
        make[RawAppArgs].fromValue(RawAppArgs.empty)

        make[RoleProvider].from {
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
            make[ConfigLoader].fromValue(specificResourceConfigLoader(classLoader, resourceName))
          case None => // keep original ConfigLoader
        }

        private[this] def namePredicateRoleProvider(f: String => Boolean): Functoid[RoleProvider] = {
          // use Auto-Traits feature to override just the few specific methods of a class succinctly
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

        private[this] def specificResourceConfigLoader(classLoader: ClassLoader, resourceName: String): ConfigLoader = {
          () =>
            {
              val cfg = ConfigFactory.parseResources(classLoader, resourceName).resolve()
              if (cfg.origin().resource() eq null) {
                throw new DIConfigReadException(s"Couldn't find a config resource with name `$resourceName` - file not found", null)
              }
              AppConfig(cfg)
            }
        }
      }
    }

    sealed trait RoleSelection {
      override final def toString: String = this match {
        case RoleSelection.Everything => "*"
        case RoleSelection.OnlySelected(selection) => selection.mkString(" ")
        case RoleSelection.AllExcluding(excluded) => excluded.map("-" + _).mkString(" ")
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

    private[this] def defaultLogger(): TrivialLogger = {
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

  final val defaultCheckConfig = DebugProperties.`izumi.distage.plancheck.check-config`.boolValue(true)
  final val defaultPrintBindings = DebugProperties.`izumi.distage.plancheck.print-bindings`.boolValue(false)
  final val defaultOnlyWarn = DebugProperties.`izumi.distage.plancheck.only-warn`.boolValue(false)

}
