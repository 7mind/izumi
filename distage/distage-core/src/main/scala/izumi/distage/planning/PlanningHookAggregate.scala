package izumi.distage.planning

import izumi.distage.model.definition.{Binding, ModuleBase}
import izumi.distage.model.plan.initial.PrePlan
import izumi.distage.model.plan.{OrderedPlan, SemiPlan, Wiring}
import izumi.distage.model.planning.PlanningHook

final class PlanningHookAggregate
(
  hooks: Set[PlanningHook]
) extends PlanningHook {

  override def hookWiring(binding: Binding.ImplBinding, wiring: Wiring): Wiring = {
    hooks.foldLeft(wiring) {
      case (acc, hook) =>
        hook.hookWiring(binding, acc)
    }
  }

  override def hookDefinition(defn: ModuleBase): ModuleBase = {
    hooks.foldLeft(defn) {
      case (acc, hook) =>
        hook.hookDefinition(acc)
    }
  }

  override def phase00PostCompletion(plan: PrePlan): PrePlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase00PostCompletion(acc)
    }
  }

  override def phase10PostGC(plan: SemiPlan): SemiPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase10PostGC(acc)
    }
  }

  override def phase20Customization(plan: SemiPlan): SemiPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase20Customization(acc)
    }
  }

  override def phase45PreForwardingCleanup(plan: SemiPlan): SemiPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase45PreForwardingCleanup(acc)
    }
  }


  override def phase50PreForwarding(plan: SemiPlan): SemiPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase50PreForwarding(acc)
    }
  }

  override def phase90AfterForwarding(plan: OrderedPlan): OrderedPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase90AfterForwarding(acc)
    }
  }
}
