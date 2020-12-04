package izumi.distage.framework

/**
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
  * @param checkConfig        Try to parse config file checking all the config bindings added using [[izumi.distage.config.ConfigModuleDef]]
  *
  * @param printBindings      Print all the bindings loaded from plugins when a problem is found during plan checking.
  */
final class PlanCheckConfig[
  Roles <: String,
  ExcludeActivations <: String,
  Config <: String,
  CheckConfig <: Boolean,
  OnlyWarn <: Boolean,
  PrintBindings <: Boolean,
] private (
  val roles: Roles,
  val excludeActivations: ExcludeActivations,
  val config: Config,
  val checkConfig: CheckConfig,
  val onlyWarn: OnlyWarn,
  val printBindings: PrintBindings,
)

object PlanCheckConfig {
  def apply[
    Roles <: String with Singleton,
    ExcludeActivations <: String with Singleton,
    Config <: String with Singleton,
    CheckConfig <: Boolean with Singleton,
    OnlyWarn <: Boolean with Singleton,
    PrintBindings <: Boolean with Singleton,
  ](roles: Roles = "*",
    excludeActivations: ExcludeActivations = "*",
    config: Config = "*",
    checkConfig: CheckConfig = unset,
    onlyWarn: OnlyWarn = unset,
    printBindings: PrintBindings = unset,
  ): PlanCheckConfig[Roles, ExcludeActivations, Config, CheckConfig, OnlyWarn, PrintBindings] =
    new PlanCheckConfig(roles, excludeActivations, config, checkConfig, onlyWarn, printBindings)

  def empty: PlanCheckConfig["*", "*", "*", Unset, Unset, Unset] = PlanCheckConfig()

  type Any = PlanCheckConfig[_, _, _, _, _, _]

  final case class Runtime(
    roles: String,
    excludeActivations: String,
    config: String,
    checkConfig: Option[Boolean],
    printBindings: Option[Boolean],
//    roles: String = "*",
//    excludeActivations: String = "",
//    config: String = "*",
//    checkConfig: Boolean = defaultCheckConfig,
//    printBindings: Boolean = defaultPrintBindings,
  )

  type Unset <: Boolean with Singleton

  /**
    * Value corresponding to an unset option.
    *
    * If unset, the value from the corresponding system property in [[izumi.distage.framework.DebugProperties]] will be used
    */
  def unset: Unset = null.asInstanceOf[Unset]
}
