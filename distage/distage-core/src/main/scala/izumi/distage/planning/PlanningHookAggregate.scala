package izumi.distage.planning

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.planning.PlanningHook

final class PlanningHookAggregate(
  hooks: Set[PlanningHook]
) extends PlanningHook {

  override def hookDefinition(defn: ModuleBase): ModuleBase = {
    hooks.foldLeft(defn) {
      case (acc, hook) =>
        hook.hookDefinition(acc)
    }
  }
}
