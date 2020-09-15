package izumi.distage.planning

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.{OrderedPlan, SemiPlan}
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

  override def phase90AfterForwarding(plan: OrderedPlan): OrderedPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase90AfterForwarding(acc)
    }
  }
}
