package izumi.distage.model.planning

import izumi.distage.model.definition.ModuleBase

trait PlanningHook {
  def hookDefinition(definition: ModuleBase): ModuleBase = definition
}
