package izumi.distage.framework.services

import izumi.distage.framework.config.PlanningOptions
import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.planning.PlanningHook

import scala.annotation.unused

class BootstrapPlatformModule(@unused options: PlanningOptions) extends BootstrapModuleDef {
  many[PlanningHook]
}
