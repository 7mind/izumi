package izumi.distage.framework

import izumi.distage.config.model.ConfTag
import izumi.distage.framework.model.{PlanCheckInput, PlanCheckResult}
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.Roots
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.planning.{AxisPoint, PlanIssue}
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.distage.plugins.load.LoadedPlugins
import izumi.fundamentals.collections.nonempty.NESet
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
      assertAgainAtRuntime()
    }

    def assertAgainAtRuntime(): Unit = planCheck.assertAgainAtRuntime()
    def checkAgainAtRuntime(): PlanCheckResult = planCheck.checkAgainAtRuntime()

    @deprecated("Renamed to `assertAgainAtRuntime`", "1.2.0")
    def assertAtRuntime(): Unit = planCheck.assertAgainAtRuntime()
    @deprecated("Renamed to `checkAgainAtRuntime`", "1.2.0")
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
      val chosenRoles = RoleSelection.parseRoles(cfg.roles)
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
      excludedActivations: Set[NESet[AxisPoint]],
      chosenConfig: Option[String],
      checkConfig: Boolean,
      printBindings: Boolean,
      onlyWarn: Boolean = false,
      planVerifier: PlanVerifier = PlanVerifier(),
      logger: TrivialLogger = defaultLogger(),
    ): PlanCheckResult = {

      var reportingEffectiveRoleNames = "unknown, failed too early"
      var reportingEffectiveRoots = "unknown, failed too early"
      var reportingEffectiveConfig = "unknown, failed too early"
      var reportingEffectiveBsPlugins = LoadedPlugins.empty
      var reportingEffectiveAppPlugins = LoadedPlugins.empty
      var reportingEffectiveModule = ModuleBase.empty
      var reportingEffectivePlugins = LoadedPlugins.empty

      def renderPlugins(loadedPlugins: LoadedPlugins): String = {
        val plugins = loadedPlugins.loaded
        val pluginClasses = plugins.map(p => s"${p.getClass.getName} (${p.bindings.size} bindings)")

        (if (pluginClasses.isEmpty) {
           "ø"
         } else if (pluginClasses.size > 7) {
           val otherPlugins = plugins.drop(7)
           val otherBindingsSize = otherPlugins.map(_.bindings.size).sum
           (pluginClasses.take(7) :+ s"<${otherPlugins.size} plugins omitted with $otherBindingsSize bindings>").mkString(", ")
         } else {
           pluginClasses.mkString(", ")
         }) + (
          if (loadedPlugins.merges.nonEmpty) {
            s", ${loadedPlugins.merges.size} merge modules with ${loadedPlugins.merges.foldLeft(0)(_ + _.bindings.size)} bindings"
          } else {
            ""
          }
        ) + (
          if (loadedPlugins.overrides.nonEmpty) {
            s", ${loadedPlugins.overrides.size} override modules with ${loadedPlugins.overrides.foldLeft(0)(_ + _.bindings.size)} bindings"
          } else ""
        )
      }

      def returnPlanCheckError(cause: Either[Throwable, PlanVerifierResult.Incorrect]): PlanCheckResult.Incorrect = {
        val visitedKeys = cause.fold(_ => Set.empty[DIKey], _.visitedKeys)
        val errorMsg = cause.fold("\n" + _.stacktraceString, _.issues.fromNESet.map(_.render + "\n").niceList())
        val message = {
          val configStr = if (checkConfig) {
            s"\n  config              = ${chosenConfig.fold("*")(c => s"resource:$c")} (effective: $reportingEffectiveConfig)"
          } else {
            ""
          }
          val bindings = reportingEffectiveModule
          val bsPlugins = reportingEffectiveBsPlugins.result
          val appPlugins = reportingEffectiveAppPlugins.result
          // fixme missing DefaultModule bindings !!!
          val bsPluginsStr = renderPlugins(reportingEffectiveBsPlugins)
          val appPluginsStr = renderPlugins(reportingEffectiveAppPlugins)
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
             |  roles               = $chosenRoles (effective roles: $reportingEffectiveRoleNames) (all effective roots: $reportingEffectiveRoots)
             |  excludedActivations = ${NESet.from(excludedActivations).fold("ø")(_.map(_.mkString(" ")).mkString(" | "))}
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

        PlanCheckResult.Incorrect(reportingEffectivePlugins, visitedKeys, message, cause)
      }

      logger.log(s"Checking with roles=`$chosenRoles` excludedActivations=`$excludedActivations` chosenConfig=`$chosenConfig`")

      try {
        val input = app.preparePlanCheckInput(chosenRoles, chosenConfig)
        val loadedPlugins = input.appPlugins ++ input.bsPlugins

        reportingEffectiveRoleNames = input.roleNames.mkString(", ")
        reportingEffectiveRoots = input.roots match {
          case Roots.Of(roots) => roots.mkString(", ")
          case Roots.Everything => "<Roots.Everything>"
        }
        reportingEffectiveModule = input.module
        reportingEffectiveAppPlugins = input.appPlugins
        reportingEffectiveBsPlugins = input.bsPlugins
        reportingEffectivePlugins = loadedPlugins

        val primaryCheckResult = checkAnyApp[F](planVerifier, excludedActivations, checkConfig, { reportingEffectiveConfig = _ }, input)
        val additionalCheckResult = app.customCheck(planVerifier, excludedActivations, checkConfig, input)

        primaryCheckResult.combine(additionalCheckResult) match {
          case incorrect: PlanVerifierResult.Incorrect => returnPlanCheckError(Right(incorrect))
          case PlanVerifierResult.Correct(visitedKeys, _) => PlanCheckResult.Correct(loadedPlugins, visitedKeys)
        }
      } catch {
        case t: Throwable =>
          cutoffMacroTrace(t)
          returnPlanCheckError(Left(t))
      }
    }

    def checkAnyApp[F[_]](
      planVerifier: PlanVerifier,
      excludedActivations: Set[NESet[AxisPoint]],
      checkConfig: Boolean,
      reportEffectiveConfig: String => Unit,
      planCheckInput: PlanCheckInput[F],
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
        val realAppConfig = configLoader.loadConfig("compile-time validation")
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

      NESet.from(planVerifierResult.issues.fromNESet ++ configIssues) match {
        case Some(allIssues) =>
          PlanVerifierResult.Incorrect(Some(allIssues), planVerifierResult.visitedKeys, planVerifierResult.time)
        case None =>
          planVerifierResult
      }
    }

    private def parseActivations(s: String): Set[NESet[AxisPoint]] = {
      s.split("\\|").iterator.filter(_.nonEmpty).flatMap {
          NESet `from` _.split(" ").iterator.filter(_.nonEmpty).map(AxisPoint.parseAxisPoint).toSet
        }.toSet
    }

    private def defaultLogger(): TrivialLogger = {
      TrivialLogger.make[this.type](DebugProperties.`izumi.debug.macro.distage.plancheck`.name)
    }

    @tailrec private def cutoffMacroTrace(t: Throwable): Unit = {
      val trace = t.getStackTrace
      val cutoffIdx = Some(trace.indexWhere(_.getClassName contains "scala.reflect.macros.runtime.JavaReflectionRuntimes$JavaReflectionResolvers")).filter(_ > 0)
      t.setStackTrace(cutoffIdx.fold(trace)(trace.take))
      val suppressed = t.getSuppressed
      suppressed.foreach(cutSuppressed)
      if (t.getCause ne null) cutoffMacroTrace(t.getCause)
    }
    // indirection for tailrec
    private def cutSuppressed(t: Throwable): Unit = cutoffMacroTrace(t)

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

    def parseRoles(s: String): RoleSelection = {
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

    private def throwInvalidRoleSelectionError(s: String): Nothing = {
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
  }

}
