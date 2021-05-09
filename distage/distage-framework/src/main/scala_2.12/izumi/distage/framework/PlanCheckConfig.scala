package izumi.distage.framework

import izumi.fundamentals.platform.language.literals.{LiteralBoolean, LiteralString}

/**
  * Options to alter the behavior of [[izumi.distage.framework.PlanCheck]]
  *
  * If on Scala `2.12` you're getting errors such as
  *
  * {{{
  * [error]  type mismatch;
  * [error]  found   : String("mode:test")
  * [error]  required: izumi.fundamentals.platform.language.literals.LiteralString{type T = String("mode:test")} with izumi.fundamentals.platform.language.literals.LiteralString
  * }}}
  *
  * Then you'll have to refactor your instance of `PlanCheck.Main` (or similar) to make sure that PlanCheckConfig is defined in a separate val.
  * You may do this by moving it from constructor parameter to early initializer.
  *
  * Example:
  *
  * {{{
  * object WiringTest extends PlanCheck.Main(MyApp, PlanCheckConfig(...))
  * // [error]
  * }}}
  *
  * Fix:
  *
  * {{{
  * object WiringTest extends {
  *   val config = PlanCheckConfig(
  *     ...
  *   )
  * } with PlanCheck.Main(MyApp, config)
  * }}}
  *
  * Note that such an issue does not exist on 2.13+, it is caused by a bug in Scala 2.12's treatment of implicits in class-parameter scope.
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
  * @param checkConfig        Try to parse config file checking all the config bindings added using [[izumi.distage.config.ConfigModuleDef]] default: `true`
  *
  * @param printBindings      Print all the bindings loaded from plugins when a problem is found during plan checking. default: `false`
  *
  * @param onlyWarn           Do not abort compilation when errors are found, just print a warning instead. Does not affect plan checks performed at runtime. default: `false`
  */
final class PlanCheckConfig[Roles <: String, ExcludeActivations <: String, Config <: String, CheckConfig <: Boolean, PrintBindings <: Boolean, OnlyWarn <: Boolean](
  val roles: Roles,
  val excludeActivations: ExcludeActivations,
  val config: Config,
  val checkConfig: CheckConfig,
  val printBindings: PrintBindings,
  val onlyWarn: OnlyWarn,
)

object PlanCheckConfig {
  def apply[
    Roles <: LiteralString,
    ExcludeActivations <: LiteralString,
    Config <: LiteralString,
    CheckConfig <: LiteralBoolean,
    PrintBindings <: LiteralBoolean,
    OnlyWarn <: LiteralBoolean,
  ](roles: Roles with LiteralString = LiteralString("*"),
    excludeActivations: ExcludeActivations with LiteralString = LiteralString(""),
    config: Config with LiteralString = LiteralString("*"),
    checkConfig: CheckConfig with LiteralBoolean = unset(PlanCheck.defaultCheckConfig),
    printBindings: PrintBindings with LiteralBoolean = unset(PlanCheck.defaultPrintBindings),
    onlyWarn: OnlyWarn with LiteralBoolean = unset(PlanCheck.defaultOnlyWarn),
  ): PlanCheckConfig[
    LiteralString.Get[Roles],
    LiteralString.Get[ExcludeActivations],
    LiteralString.Get[Config],
    LiteralBoolean.Get[CheckConfig],
    LiteralBoolean.Get[PrintBindings],
    LiteralBoolean.Get[OnlyWarn],
  ] = {
    new PlanCheckConfig[
      LiteralString.Get[Roles],
      LiteralString.Get[ExcludeActivations],
      LiteralString.Get[Config],
      LiteralBoolean.Get[CheckConfig],
      LiteralBoolean.Get[PrintBindings],
      LiteralBoolean.Get[OnlyWarn],
    ](
      roles = roles,
      excludeActivations = excludeActivations,
      config = config,
      checkConfig = checkConfig,
      printBindings = printBindings,
      onlyWarn = onlyWarn,
    )
  }

  def empty = PlanCheckConfig()

  type Any = PlanCheckConfig[? <: String, ? <: String, ? <: String, ? <: Boolean, ? <: Boolean, ? <: Boolean]

  type Unset <: Boolean with Singleton

  /**
    * A type corresponding to an unset option.
    *
    * If unset, the value from the corresponding system property in [[izumi.distage.framework.DebugProperties]] will be used
    * (To affect compile-time, the system property must be set in sbt, `sbt -Dprop=true`, or in a `.jvmopts` file in project root)
    */
  def unset(default: Boolean): LiteralBoolean.Of[Unset] = new LiteralBoolean(default).asInstanceOf[LiteralBoolean.Of[Unset]]
}
