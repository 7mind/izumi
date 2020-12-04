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
    Roles <: LiteralString,
    ExcludeActivations <: LiteralString,
    Config <: LiteralString,
    CheckConfig <: LiteralBoolean,
    OnlyWarn <: LiteralBoolean,
    PrintBindings <: LiteralBoolean,
  ](roles: Roles with LiteralString = LiteralString("*"),
    excludeActivations: ExcludeActivations with LiteralString = LiteralString("*"),
    config: Config with LiteralString = LiteralString("*"),
    checkConfig: CheckConfig with LiteralBoolean = unset,
    onlyWarn: OnlyWarn with LiteralBoolean = unset,
    printBindings: PrintBindings with LiteralBoolean = unset,
  ): PlanCheckConfig[
    LiteralString.Get[Roles],
    LiteralString.Get[ExcludeActivations],
    LiteralString.Get[Config],
    LiteralBoolean.Get[CheckConfig],
    LiteralBoolean.Get[OnlyWarn],
    LiteralBoolean.Get[PrintBindings],
  ] = {
    new PlanCheckConfig[
      LiteralString.Get[Roles],
      LiteralString.Get[ExcludeActivations],
      LiteralString.Get[Config],
      LiteralBoolean.Get[CheckConfig],
      LiteralBoolean.Get[OnlyWarn],
      LiteralBoolean.Get[PrintBindings],
    ](
      roles,
      excludeActivations,
      config,
      checkConfig,
      onlyWarn,
      printBindings,
    )
  }

  def empty = PlanCheckConfig()

  type Any = PlanCheckConfig[_, _, _, _, _, _]

  final case class Runtime(
    roles: String,
    excludeActivations: String,
    config: String,
    checkConfig: Option[Boolean],
    onlyWarn: Option[Boolean],
    printBindings: Option[Boolean],
  )

  type Unset <: Boolean with Singleton

  /**
    * Value corresponding to an unset option.
    *
    * If unset, the value from the corresponding system property in [[izumi.distage.framework.DebugProperties]] will be used
    */
  def unset: LiteralBoolean.Of[Unset] = null.asInstanceOf[LiteralBoolean.Of[Unset]]
}
