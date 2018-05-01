package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleDef}
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class PlanningHookDefaultImpl extends PlanningHook {


}

class PlanningHookAggregate(hooks: Set[PlanningHook]) extends PlanningHook {
  override def hookWiring(binding: Binding.ImplBinding, wiring: RuntimeDIUniverse.Wiring): RuntimeDIUniverse.Wiring = {
    hooks.foldLeft(wiring) {
      case (acc, hook) =>
        hook.hookWiring(binding, acc)
    }
  }

  override def hookDefinition(defn: ModuleDef): ModuleDef = {
    hooks.foldLeft(defn) {
      case (acc, hook) =>
        hook.hookDefinition(acc)
    }
  }

  override def hookStep(context: ModuleDef, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan = {
    hooks.foldLeft(currentPlan) {
      case (acc, hook) =>
        hook.hookStep(context, acc, binding, next)
    }
  }

  override def hookFinal(plan: FinalPlan): FinalPlan = {
    hooks.foldLeft(plan) {
      case (acc, hook) =>
        hook.hookFinal(acc)
    }
  }
}
