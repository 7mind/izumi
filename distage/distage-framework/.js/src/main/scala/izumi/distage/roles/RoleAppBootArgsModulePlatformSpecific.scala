package izumi.distage.roles

import izumi.distage.framework.config.PlanningOptions

trait RoleAppBootArgsModulePlatformSpecific {
  def mkPlanningOptionsPlatformSpecific: PlanningOptions = {
    PlanningOptions()
  }
}
