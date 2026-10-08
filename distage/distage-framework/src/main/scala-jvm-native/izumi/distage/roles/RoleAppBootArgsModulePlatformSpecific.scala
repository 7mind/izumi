package izumi.distage.roles

import izumi.distage.framework.config.PlanningOptions
import izumi.fundamentals.platform.cli.model.RoleAppArgs

trait RoleAppBootArgsModulePlatformSpecific {
  def mkPlanningOptionsPlatformSpecific: RoleAppArgs => PlanningOptions = {
    (parameters: RoleAppArgs) =>
      PlanningOptions.default.copy(
        addGraphVizDump = parameters.globalParameters.hasFlag(RoleAppMain.Options.dumpContext)
      )
  }
}
