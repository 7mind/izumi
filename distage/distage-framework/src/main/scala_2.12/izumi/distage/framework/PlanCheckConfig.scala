package izumi.distage.framework

import izumi.fundamentals.platform.language.literals.{LiteralBoolean, LiteralString}

/**
  * If on `2.12` you're getting errors such as
  *
  * {{{
  * [error]  type mismatch;
  * [error]  found   : String("mode:test")
  * [error]  required: izumi.fundamentals.platform.language.literals.LiteralString{type T = String("mode:test")} with izumi.fundamentals.platform.language.literals.LiteralString
  * }}}
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

  type Any = PlanCheckConfig[_ <: String, _ <: String, _ <: String, _ <: Boolean, _ <: Boolean, _ <: Boolean]

  type Unset <: Boolean with Singleton

  /**
    * A type corresponding to an unset option.
    *
    * If unset, the value from the corresponding system property in [[izumi.distage.framework.DebugProperties]] will be used
    * (To affect compile-time, the system property must be set in sbt, `sbt -Dprop=true`, or in a `.jvmopts` file in project root)
    */
  def unset(default: Boolean): LiteralBoolean.Of[Unset] = new LiteralBoolean(default).asInstanceOf[LiteralBoolean.Of[Unset]]
}
