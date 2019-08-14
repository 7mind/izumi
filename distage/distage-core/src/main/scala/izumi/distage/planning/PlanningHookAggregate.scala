package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleBase}
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, SemiPlan, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

final class PlanningHookAggregate
(
  hooks: Set[PlanningHook]
) extends PlanningHook {

  override def hookWiring(binding: Binding.ImplBinding, wiring: RuntimeDIUniverse.Wiring): RuntimeDIUniverse.Wiring = {
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

  override def phase00PostCompletion(plan: DodgyPlan): DodgyPlan = {
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
