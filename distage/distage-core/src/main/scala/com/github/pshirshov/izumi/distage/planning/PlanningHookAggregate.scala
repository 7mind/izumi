package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleBase}
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class PlanningHookAggregate(hooks: Set[PlanningHook]) extends PlanningHook {
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

  override def hookStep(context: ModuleBase, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan = {
    hooks.foldLeft(currentPlan) {
      case (acc, hook) =>
        hook.hookStep(context, acc, binding, next)
    }
  }

  override def phase00PostCompletion(plan: DodgyPlan): DodgyPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase00PostCompletion(acc)
    }
  }

  override def phase10PostFinalization(plan: FinalPlan): FinalPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase10PostFinalization(acc)
    }
  }

  override def phase20Customization(plan: FinalPlan): FinalPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase20Customization(acc)
    }
  }

  override def phase50PreForwarding(plan: FinalPlan): FinalPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase50PreForwarding(acc)
    }
  }

  override def phase90AfterForwarding(plan: FinalPlan): FinalPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.phase90AfterForwarding(acc)
    }
  }
}
