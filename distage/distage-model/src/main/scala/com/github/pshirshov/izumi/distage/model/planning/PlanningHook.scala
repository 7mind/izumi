package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleBase}
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan, ReplanningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

trait PlanningHook {
  def hookWiring(binding: Binding.ImplBinding, wiring: RuntimeDIUniverse.Wiring): RuntimeDIUniverse.Wiring = {
    Quirks.discard(binding)
    wiring
  }

  def hookDefinition(defn: ModuleBase): ModuleBase = defn

  def hookStep(context: ModuleBase, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan = {
    Quirks.discard(context, currentPlan, binding)
    next
  }

  def hookResolved(context: ReplanningContext, plan: FinalPlan): FinalPlan = {
    Quirks.discard(context)
    plan
  }

  def hookFinal(context: ReplanningContext, plan: FinalPlan): FinalPlan = {
    Quirks.discard(context)
    plan
  }

  protected def firstOnly[P](context: ReplanningContext, plan: P)(f: P => P): P = {
    if (context.count == 0) {
      f(plan)
    } else {
      plan
    }
  }
}
