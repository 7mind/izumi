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
  Activations <: String,
  Config <: String,
  CheckConfig <: Boolean,
  PrintPlan <: Boolean,
  OnlyWarn <: Boolean,
](roleAppMain: RoleAppMain,
  roles: Roles,
  activations: Activations,
  config: Config,
  checkConfig: Option[CheckConfig],
  printPlan: Option[PrintPlan],
  onlyWarn: Option[OnlyWarn],
  checkedPlugins: Seq[PluginBase],
) {
  def check(): PlanCheckResult =
    checkRoleApp(
      roleAppMain = roleAppMain,
      roles = roles,
      activations = activations,
      config = config,
      checkConfig = checkConfig.getOrElse(PlanCheck.defaultCheckConfig),
      printPlan = printPlan.getOrElse(PlanCheck.defaultPrintPlan),
    )
}

object PerformPlanCheck {
  def bruteforce[
    RoleAppMain <: PlanHolder,
    Roles <: String with Singleton,
    Activations <: String with Singleton,
    Config <: String with Singleton,
    CheckConfig <: Boolean with Singleton,
    PrintPlan <: Boolean with Singleton,
    OnlyWarn <: Boolean with Singleton,
  ](@unused roleAppMain: RoleAppMain,
    @unused roles: Roles = "*",
    @unused activations: Activations = "*",
    @unused config: Config = "*",
    @unused checkConfig: CheckConfig = unset,
    @unused printPlan: PrintPlan = unset,
    @unused onlyWarn: OnlyWarn = unset,
  )(implicit planCheck: PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, PrintPlan, OnlyWarn]
  ): PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, PrintPlan, OnlyWarn] = planCheck

  implicit def materialize[
    RoleAppMain <: PlanHolder,
    Roles <: String,
    Activations <: String,
    Config <: String,
    CheckConfig <: Boolean,
    PrintPlan <: Boolean,
    OnlyWarn <: Boolean,
  ]: PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, PrintPlan, OnlyWarn] =
    macro PlanCheckMacro.impl[RoleAppMain, Roles, Activations, Config, CheckConfig, PrintPlan, OnlyWarn]

  // 2.12 requires `Witness`-like mechanism
  class Main[
    RoleAppMain <: PlanHolder,
    Roles <: String with Singleton,
    Activations <: String with Singleton,
    Config <: String with Singleton,
    CheckConfig <: Boolean with Singleton,
    PrintPlan <: Boolean with Singleton,
    OnlyWarn <: Boolean with Singleton,
  ](@unused roleAppMain: RoleAppMain,
    @unused roles: Roles = "*",
    @unused activations: Activations = "*",
    @unused config: Config = "*",
    @unused checkConfig: CheckConfig = unset,
    @unused printPlan: PrintPlan = unset,
    @unused onlyWarn: OnlyWarn = unset,
  )(implicit
    val planCheck: PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, PrintPlan, OnlyWarn]
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
