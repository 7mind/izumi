package izumi.distage.framework

import izumi.distage.config.model.ConfTag
import izumi.distage.framework.model.{PlanCheckInput, PlanCheckResult}
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.Roots
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.planning.AxisPoint
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.{PlanIssue, PlanVerifierResult}
import izumi.distage.plugins.PluginBase
import izumi.distage.plugins.load.LoadedPlugins
import izumi.fundamentals.collections.nonempty.NonEmptySet
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import izumi.fundamentals.platform.language.Quirks.discard
import izumi.fundamentals.platform.strings.IzString.toRichIterable

import scala.annotation.{tailrec, unused}

/**
  * API for performing compile-time and runtime checks of correctness for `distage` applications.
  *
  * The easiest way to add compile-time safety to your application is to add
  * an object inheriting [[PlanCheck.Main]] in __test scope__ of the same module
  * where you define your [[izumi.distage.roles.RoleAppMain Role Launcher]]
  *
  * @example
  *
  * {{{
  *   import izumi.distage.framework.PlanCheck
  *   import com.example.myapp.MainLauncher
  *
  *   object WiringCheck extends PlanCheck.Main(MainLauncher)
  * }}}
  *
  * This object will emit compile-time errors for any issues or omissions in your `ModuleDefs`
  *
  * @see [[izumi.distage.framework.PlanCheckConfig Configuration Options]]
  * @see [[izumi.distage.framework.CheckableApp Support for checking not role-based applications]]
  * @see [[izumi.distage.framework.PlanCheckMaterializer Implicit-based API]]
  * @see [[izumi.distage.framework.DebugProperties Configuration options in system properties]]
  */
object PlanCheck {

  open class Main[AppMain <: CheckableApp, Cfg <: PlanCheckConfig.Any](
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

  def assertAppCompileTime[AppMain <: CheckableApp, Cfg <: PlanCheckConfig.Any](
    app: AppMain,
    cfg: Cfg = PlanCheckConfig.empty,
  )(implicit planCheckResult: PlanCheckMaterializer[AppMain, Cfg]
  ): PlanCheckMaterializer[AppMain, Cfg] = {
    discard(app, cfg)
    planCheckResult
  }

  final val defaultCheckConfig = DebugProperties.`izumi.distage.plancheck.check-config`.boolValue(true)
  final val defaultPrintBindings = DebugProperties.`izumi.distage.plancheck.print-bindings`.boolValue(false)
  final val defaultOnlyWarn = DebugProperties.`izumi.distage.plancheck.only-warn`.boolValue(false)

  object runtime {
    /** @throws izumi.distage.framework.model.exceptions.PlanCheckException on found issues */
    def assertApp(
      app: CheckableApp,
      cfg: PlanCheckConfig.Any = PlanCheckConfig.empty,
      planVerifier: PlanVerifier = PlanVerifier(),
      logger: TrivialLogger = defaultLogger(),
    ): Unit = {
      checkApp(app, cfg, planVerifier, logger).throwOnError()
    }

    /** @return a list of issues, if any. Does not throw. */
    def checkApp(
      app: CheckableApp,
      cfg: PlanCheckConfig.Any = PlanCheckConfig.empty,
      planVerifier: PlanVerifier = PlanVerifier(),
      logger: TrivialLogger = defaultLogger(),
    ): PlanCheckResult = {
      val chosenRoles = parseRoles(cfg.roles)
      val chosenActivations = parseActivations(cfg.excludeActivations)
      val chosenConfig = if (cfg.config == "*") None else Some(cfg.config)

      checkAppParsed[app.AppEffectType](
        app,
        chosenRoles,
        excludedActivations = chosenActivations,
        chosenConfig = chosenConfig,
        checkConfig = cfg.checkConfig,
        printBindings = cfg.printBindings,
        onlyWarn = cfg.onlyWarn,
        planVerifier,
        logger,
      )
    }

    /** @return a list of issues, if any. Does not throw. */
    def checkAppParsed[F[_]](
      app: CheckableApp.Aux[F],
      chosenRoles: RoleSelection,
      excludedActivations: Set[NonEmptySet[AxisPoint]],
      chosenConfig: Option[String],
      checkConfig: Boolean,
      printBindings: Boolean,
      onlyWarn: Boolean = false,
      planVerifier: PlanVerifier = PlanVerifier(),
      logger: TrivialLogger = defaultLogger(),
    ): PlanCheckResult = {

      var effectiveRoleNames = "unknown, failed too early"
      var effectiveRoots = "unknown, failed too early"
      var effectiveConfig = "unknown, failed too early"
      var effectiveBsPlugins = LoadedPlugins.empty
      var effectiveAppPlugins = LoadedPlugins.empty
      var effectiveModule = ModuleBase.empty
      var effectivePlugins = LoadedPlugins.empty

      def renderPlugins(plugins: Seq[PluginBase]): String = {
        val pluginClasses = plugins.map(p => s"${p.getClass.getName} (${p.bindings.size} bindings)")
        if (pluginClasses.isEmpty) {
          "ø"
        } else if (pluginClasses.size > 7) {
          val otherPlugins = plugins.drop(7)
          val otherBindingsSize = otherPlugins.map(_.bindings.size).sum
          (pluginClasses.take(7) :+ s"<${otherPlugins.size} plugins omitted with $otherBindingsSize bindings>").mkString(", ")
        } else {
          pluginClasses.mkString(", ")
        }
      }

      def returnPlanCheckError(cause: Either[Throwable, PlanVerifierResult.Incorrect]): PlanCheckResult.Incorrect = {
        val visitedKeys = cause.fold(_ => Set.empty[DIKey], _.visitedKeys)
        val errorMsg = cause.fold("\n" + _.stackTrace, _.issues.fromNonEmptySet.map(_.render + "\n").niceList())
        val message = {
          val configStr = if (checkConfig) {
            s"\n  config              = ${chosenConfig.fold("*")(c => s"resource:$c")} (effective: $effectiveConfig)"
          } else {
            ""
          }
          val bindings = effectiveModule
          val bsPlugins = effectiveBsPlugins.result
          val appPlugins = effectiveAppPlugins.result
          // fixme missing DefaultModule bindings !!!
          val bsPluginsStr = renderPlugins(bsPlugins)
          val appPluginsStr = renderPlugins(appPlugins)
          val printedBindings = if (printBindings) {
            (if (bsPlugins.nonEmpty)
               s"""
                  |Bootstrap bindings were:
                  |${bsPlugins.flatMap(_.iterator.map(_.toString)).niceList()}
                  |""".stripMargin
             else "") ++
            s"""Bindings were:
               |${bindings.iterator.map(_.toString).niceList()}
               |
               |Keys visited:
               |${visitedKeys.niceList()}
               |
               |App plugin bindings were:
               |${appPlugins.flatMap(_.iterator.map(_.toString)).niceList()}
               |""".stripMargin
          } else ""

          val onlyWarnAdvice = if (onlyWarn) {
            ""
          } else {
            s"""
               |You may may turn this error into a warning by setting system property in sbt, `sbt -D${DebugProperties.`izumi.distage.plancheck.only-warn`.name}=true` or by adding the option to `.jvmopts` in project root.
               |""".stripMargin
          }

          s"""Found a problem with your DI wiring, when checking application=${app.getClass.getName.split('.').last.split('$').last}, with parameters:
             |
             |  roles               = $chosenRoles (effective roles: $effectiveRoleNames) (all effective roots: $effectiveRoots)
             |  excludedActivations = ${NonEmptySet.from(excludedActivations).fold("ø")(_.map(_.mkString(" ")).mkString(" | "))}
             |  bootstrapPlugins    = $bsPluginsStr
             |  plugins             = $appPluginsStr
             |  checkConfig         = $checkConfig$configStr
             |  printBindings       = $printBindings${if (!printBindings) ", set to `true` for full bindings printout" else ""}
             |  onlyWarn            = $onlyWarn${if (!onlyWarn) ", set to `true` to ignore compilation error" else ""}
             |$onlyWarnAdvice
             |$printedBindings
             |Error was:
             |$errorMsg
             |""".stripMargin
        }

        PlanCheckResult.Incorrect(effectivePlugins, visitedKeys, message, cause)
      }

      logger.log(s"Checking with roles=`$chosenRoles` excludedActivations=`$excludedActivations` chosenConfig=`$chosenConfig`")

      try {
        val input = app.preparePlanCheckInput(chosenRoles, chosenConfig)

        effectiveRoleNames = input.roleNames.mkString(", ")
        effectiveRoots = input.roots match {
          case Roots.Of(roots) => roots.mkString(", ")
          case Roots.Everything => "<Roots.Everything>"
        }
        effectiveModule = input.module
        effectiveAppPlugins = input.appPlugins
        effectiveBsPlugins = input.bsPlugins
        val loadedPlugins = input.appPlugins ++ input.bsPlugins
        effectivePlugins = loadedPlugins

        checkAnyApp[F](planVerifier, excludedActivations, checkConfig, effectiveConfig = _)(input) match {
          case incorrect: PlanVerifierResult.Incorrect => returnPlanCheckError(Right(incorrect))
          case PlanVerifierResult.Correct(visitedKeys, _) => PlanCheckResult.Correct(loadedPlugins, visitedKeys)
        }
      } catch {
        case t: Throwable =>
          cutoffMacroTrace(t)
          returnPlanCheckError(Left(t))
      }
    }

    private[this] def checkAnyApp[F[_]](
      planVerifier: PlanVerifier,
      excludedActivations: Set[NonEmptySet[AxisPoint]],
      checkConfig: Boolean,
      reportEffectiveConfig: String => Unit,
    )(planCheckInput: PlanCheckInput[F]
    ): PlanVerifierResult = {
      val PlanCheckInput(effectType, module, roots, _, providedKeys, configLoader, _, _) = planCheckInput

      val planVerifierResult = planVerifier.verify[F](
        bindings = module,
        roots = roots,
        providedKeys = providedKeys,
        excludedActivations = excludedActivations,
      )(effectType)
      val reachableKeys = providedKeys ++ planVerifierResult.visitedKeys

      val configIssues = if (checkConfig) {
        val realAppConfig = configLoader.loadConfig()
        reportEffectiveConfig(realAppConfig.config.origin().toString)

        module.iterator
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
          PlanVerifierResult.Incorrect(Some(allIssues), planVerifierResult.visitedKeys, planVerifierResult.time)
        case None =>
          planVerifierResult
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
           |Valid syntax:
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

}
