package izumi.distage.planning

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.planning.PlanningHook

final class PlanningHookAggregate(
  hooks: Set[PlanningHook]
) extends PlanningHook {

  override def hookDefinition(definition: ModuleBase): ModuleBase = {
    hooks.foldLeft(definition) {
      case (acc, hook) =>
        hook.hookDefinition(acc)
    }
  }
}
