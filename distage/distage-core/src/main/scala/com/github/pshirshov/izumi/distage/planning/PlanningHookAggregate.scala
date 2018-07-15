package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleBase}
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan, ReplanningContext}
import com.github.pshirshov.izumi.distage.model.planning.{PlanningHook, ExtendedFinalPlan}
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

  override def hookResolved(context: ReplanningContext, plan: FinalPlan): ExtendedFinalPlan = {
    hooks.foldLeft(ExtendedFinalPlan(plan, replanningRequested = false)) {
      case (acc, hook) =>
        val out = hook.hookResolved(context, acc.plan)
        out.copy(replanningRequested = acc.replanningRequested || out.replanningRequested)
    }
  }

  override def hookFinal(context: ReplanningContext, plan: FinalPlan): ExtendedFinalPlan = {
    hooks.foldLeft(ExtendedFinalPlan(plan, replanningRequested = false)) {
      case (acc, hook) =>
        val out = hook.hookFinal(context, acc.plan)
        out.copy(replanningRequested = acc.replanningRequested || out.replanningRequested)
    }
  }
}
