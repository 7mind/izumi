package izumi.distage.framework

import izumi.distage.framework.PlanCheck.{PlanCheckResult, checkRoleApp}
import izumi.distage.plugins.PluginBase
import izumi.distage.roles.PlanHolder
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.language.unused

import scala.language.experimental.macros

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
    Roles <: String with Singleton,
    ExcludeActivations <: String with Singleton,
    Config <: String with Singleton,
    CheckConfig <: Boolean with Singleton,
    OnlyWarn <: Boolean with Singleton,
  ](@unused roleAppMain: RoleAppMain,
    @unused roles: Roles = "*",
    @unused excludeActivations: ExcludeActivations = "*",
    @unused config: Config = "*",
    @unused checkConfig: CheckConfig = unset,
    @unused onlyWarn: OnlyWarn = unset,
  )(implicit planCheck: PerformPlanCheck[RoleAppMain, Roles, ExcludeActivations, Config, CheckConfig, OnlyWarn]
  ): PerformPlanCheck[RoleAppMain, Roles, ExcludeActivations, Config, CheckConfig, OnlyWarn] = planCheck

  implicit def materialize[
    RoleAppMain <: PlanHolder,
    Roles <: String,
    ExcludeActivations <: String,
    Config <: String,
    CheckConfig <: Boolean,
    OnlyWarn <: Boolean,
  ]: PerformPlanCheck[RoleAppMain, Roles, ExcludeActivations, Config, CheckConfig, OnlyWarn] =
    macro PlanCheckMacro.impl[RoleAppMain, Roles, ExcludeActivations, Config, CheckConfig, OnlyWarn]

  class Main[
    RoleAppMain <: PlanHolder,
    Roles <: String with Singleton,
    ExcludeActivations <: String with Singleton,
    Config <: String with Singleton,
    CheckConfig <: Boolean with Singleton,
    OnlyWarn <: Boolean with Singleton,
  ](@unused roleAppMain: RoleAppMain,
    @unused roles: Roles = "*",
    @unused excludeActivations: ExcludeActivations = "*",
    @unused config: Config = "*",
    @unused checkConfig: CheckConfig = unset,
    @unused onlyWarn: OnlyWarn = unset,
  )(implicit
    val planCheck: PerformPlanCheck[RoleAppMain, Roles, ExcludeActivations, Config, CheckConfig, OnlyWarn]
  ) {
    def rerunAtRuntime(): Unit = {
      planCheck.check().throwOnError().discard()
    }

    def main(args: Array[String]): Unit = rerunAtRuntime().discard()
  }

  type Unset <: Boolean with Singleton

  /**
    * Value corresponding to an unset option.
    *
    * If unset, the value from the corresponding system property in [[izumi.distage.framework.DebugProperties]] will be used
    */
  def unset: Unset = null.asInstanceOf[Unset]
}
