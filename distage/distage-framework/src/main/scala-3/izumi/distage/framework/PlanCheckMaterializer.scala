package izumi.distage.framework

import izumi.distage.framework.model.PlanCheckResult
import izumi.distage.plugins.PluginBase

/**
  * This implicit performs a compile-time check for a given `distage`-based `App` when materialized.
  *
  * The reason to use an implicit macro for this, instead of a direct def-macro is because
  * implicit macros are more compositional – you may write new functions using the implicit without writing new macros,
  * by contrast macros can only be composed inside new macros – and this in turn requires separating them into different
  * `sbt` modules.
  *
  * @tparam AppMain The application to check, this should be a static object, most often a `main` object inherited from [[izumi.distage.roles.RoleAppMain]]
  * @tparam Cfg Additional configuration options for compile-time checker
  *
  * @see [[izumi.distage.framework.PlanCheck User API]]
  * @see [[izumi.distage.framework.PlanCheckConfig Configuration Options]]
  */
final case class PlanCheckMaterializer[AppMain <: CheckableApp, -Cfg <: PlanCheckConfig.Any](
  checkPassed: Boolean,
  checkedPlugins: Seq[PluginBase],
  app: AppMain,
  roles: String,
  excludeActivations: String,
  config: String,
  checkConfig: Option[Boolean],
  printBindings: Option[Boolean],
  onlyWarn: Option[Boolean],
) {
  /** @throws izumi.distage.framework.model.exceptions.PlanCheckException on found issues */
  def assertAgainAtRuntime(): Unit = PlanCheck.runtime.assertApp(app, makeCfg)

  /** @return a list of issues, if any. Does not throw. */
  def checkAgainAtRuntime(): PlanCheckResult = PlanCheck.runtime.checkApp(app, makeCfg)

  def makeCfg: PlanCheckConfig.Any = new PlanCheckConfig(
    roles,
    excludeActivations,
    config,
    checkConfig = checkConfig.getOrElse(PlanCheck.defaultCheckConfig),
    printBindings = printBindings.getOrElse(PlanCheck.defaultPrintBindings),
    onlyWarn = onlyWarn.getOrElse(PlanCheck.defaultOnlyWarn),
  )
}

object PlanCheckMaterializer {

  implicit def materialize[
    AppMain <: CheckableApp,
    Roles <: String,
    ExcludeActivations <: String,
    Config <: String,
    CheckConfig <: Boolean,
    PrintBindings <: Boolean,
    OnlyWarn <: Boolean,
  ]: PlanCheckMaterializer[AppMain, PlanCheckConfig[Roles, ExcludeActivations, Config, CheckConfig, PrintBindings, OnlyWarn]] =
    ???

}
