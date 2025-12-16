package izumi.distage.framework

/**
  * Options to alter the behavior of [[izumi.distage.framework.PlanCheck]]
  *
  * @param roles              "*" to check all roles,
  *
  *                           "role1 role2" to check specific roles,
  *
  *                           "* -role1 -role2" to check all roles _except_ specific roles.
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
  *                           and don't want to add them, then you may exclude "repo:dummy" from being checked.
  *
  * @param config             Config resource file name, e.g. "application.conf" or "*" if using the same config settings as `roleAppMain`
  *
  * @param checkConfig        Try to parse config file checking all the config bindings added using [[izumi.distage.config.ConfigModuleDef]]. Default: `true`
  *
  * @param printBindings      Print all the bindings loaded from plugins when a problem is found during plan checking. Default: `false`
  *
  * @param onlyWarn           Do not abort compilation when errors are found, just print a warning instead. Does not affect plan checks performed at runtime. Default: `false`
  */
final case class PlanCheckConfig[Roles <: String, ExcludeActivations <: String, Config <: String, CheckConfig <: Boolean, PrintBindings <: Boolean, OnlyWarn <: Boolean](
  roles: Roles,
  excludeActivations: ExcludeActivations,
  config: Config,
  checkConfig: CheckConfig,
  printBindings: PrintBindings,
  onlyWarn: OnlyWarn,
)

object PlanCheckConfig {
  def apply[
    Roles <: String & Singleton,
    ExcludeActivations <: String & Singleton,
    Config <: String & Singleton,
    CheckConfig <: Boolean & Singleton,
    PrintBindings <: Boolean & Singleton,
    OnlyWarn <: Boolean & Singleton,
  ](roles: Roles = "*",
    excludeActivations: ExcludeActivations = "",
    config: Config = "*",
    checkConfig: CheckConfig = unset(PlanCheck.defaultCheckConfig),
    printBindings: PrintBindings = unset(PlanCheck.defaultPrintBindings),
    onlyWarn: OnlyWarn = unset(PlanCheck.defaultOnlyWarn),
  ): PlanCheckConfig[Roles, ExcludeActivations, Config, CheckConfig, PrintBindings, OnlyWarn] = {
    new PlanCheckConfig(
      roles = roles,
      excludeActivations = excludeActivations,
      config = config,
      checkConfig = checkConfig,
      printBindings = printBindings,
      onlyWarn = onlyWarn,
    )
  }

  def empty: PlanCheckConfig["*", "", "*", Unset, Unset, Unset] = PlanCheckConfig()

  type Any = PlanCheckConfig[? <: String, ? <: String, ? <: String, ? <: Boolean, ? <: Boolean, ? <: Boolean]

  type Unset <: Boolean & Singleton

  /**
    * A type corresponding to an unset option.
    *
    * If unset, the value from the corresponding system property in [[izumi.distage.framework.DebugProperties]] will be used
    * (To affect compile-time, the system property must be set in sbt, `sbt -Dprop=true`, or in a `.jvmopts` file in project root)
    */
  def unset(default: Boolean): Unset = default.asInstanceOf[Unset]
}
