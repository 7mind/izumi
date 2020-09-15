package izumi.distage.model.planning

import izumi.distage.model.definition.ModuleBase

trait PlanningHook {
  def hookDefinition(defn: ModuleBase): ModuleBase = defn
}
