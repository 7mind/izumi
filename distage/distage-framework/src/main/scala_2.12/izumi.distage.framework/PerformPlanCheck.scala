package izumi.distage.framework

import izumi.distage.framework.PlanCheck.{PlanCheckResult, checkRoleApp}
import izumi.distage.plugins.PluginBase
import izumi.distage.roles.PlanHolder
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.language.{LiteralCompat, unused}

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.macros.whitebox

final case class PerformPlanCheck[
  RoleAppMain <: PlanHolder,
  Roles <: String,
  ExcludeActivations <: String,
  Config <: String,
  CheckConfig <: Boolean,
  OnlyWarn <: Boolean,
](roleAppMain: RoleAppMain,
  roles: Roles,
  excludeActivations: ExcludeActivations,
  config: Config,
  checkConfig: Option[CheckConfig],
  onlyWarn: Option[OnlyWarn],
  checkedPlugins: Seq[PluginBase],
) {
  def check(): PlanCheckResult =
    checkRoleApp(
      roleAppMain = roleAppMain,
      roles = roles,
      excludeActivations = excludeActivations,
      config = config,
      checkConfig = checkConfig.getOrElse(PlanCheck.defaultCheckConfig),
    )
}

object PerformPlanCheck {
  def bruteforce[
    RoleAppMain <: PlanHolder,
    Roles <: LiteralString,
    ExcludeActivations <: LiteralString,
    Config <: LiteralString,
    CheckConfig <: LiteralBoolean,
    OnlyWarn <: LiteralBoolean,
  ](@unused roleAppMain: RoleAppMain,
    @unused roles: Roles with LiteralString = LiteralString("*"),
    @unused excludeActivations: ExcludeActivations with LiteralString = LiteralString("*"),
    @unused config: Config with LiteralString = LiteralString("*"),
    @unused checkConfig: CheckConfig with LiteralBoolean = unset,
    @unused onlyWarn: OnlyWarn with LiteralBoolean = unset,
  )(implicit planCheck: PerformPlanCheck[RoleAppMain, Roles#T, ExcludeActivations#T, Config#T, CheckConfig#T, OnlyWarn#T]
  ): PerformPlanCheck[RoleAppMain, Roles#T, ExcludeActivations#T, Config#T, CheckConfig#T, OnlyWarn#T] = planCheck

  implicit def materialize[
    RoleAppMain <: PlanHolder,
    Roles <: String,
    ExcludeActivations <: String,
    Config <: String,
    CheckConfig <: Boolean,
    OnlyWarn <: Boolean,
  ]: PerformPlanCheck[RoleAppMain, Roles, ExcludeActivations, Config, CheckConfig, OnlyWarn] =
    macro PlanCheckMacro.impl[RoleAppMain, Roles, ExcludeActivations, Config, CheckConfig, OnlyWarn]

  // 2.12 requires `Witness`-like mechanism
  class Main[
    RoleAppMain <: PlanHolder,
    Roles <: LiteralString,
    ExcludeActivations <: LiteralString,
    Config <: LiteralString,
    CheckConfig <: LiteralBoolean,
    OnlyWarn <: LiteralBoolean,
  ](@unused roleAppMain: RoleAppMain,
    @unused roles: Roles with LiteralString = LiteralString("*"),
    @unused excludeActivations: ExcludeActivations with LiteralString = LiteralString("*"),
    @unused config: Config with LiteralString = LiteralString("*"),
    @unused checkConfig: CheckConfig with LiteralBoolean = unset,
    @unused onlyWarn: OnlyWarn with LiteralBoolean = unset,
  )(implicit
    val planCheck: PerformPlanCheck[RoleAppMain, Roles#T, ExcludeActivations#T, Config#T, CheckConfig#T, OnlyWarn#T]
  ) {
    def rerunAtRuntime(): Unit = {
      planCheck.check().throwOnError().discard()
    }

    def main(args: Array[String]): Unit = rerunAtRuntime().discard()
  }

  final abstract class LiteralString { type T <: String }
  object LiteralString {
    @inline implicit final def apply(s: String): LiteralString { type T = s.type } = null
  }

  final abstract class LiteralBoolean { type T <: Boolean }
  object LiteralBoolean {
    @inline implicit final def apply(b: Boolean): LiteralBoolean = macro LiteralBooleanMacro.createBool

    @inline final def True: LiteralBoolean { type T = LiteralCompat.`true`.T } = null
    @inline final def False: LiteralBoolean { type T = LiteralCompat.`false`.T } = null

    object LiteralBooleanMacro {
      def createBool(c: whitebox.Context)(b: c.Expr[Boolean]): c.Tree = {
        import c.universe._
        val bool = b.tree.asInstanceOf[LiteralApi].value.value.asInstanceOf[Boolean]
        val methodName = TermName(bool.toString.capitalize)
        q"${reify(LiteralBoolean)}.$methodName"
      }
    }
  }

  type Unset <: Boolean

  /**
    * Value corresponding to an unset option.
    *
    * If unset, the value from the corresponding system property in [[izumi.distage.framework.DebugProperties]] will be used
    */
  def unset: LiteralBoolean { type T = Unset } = null
}
