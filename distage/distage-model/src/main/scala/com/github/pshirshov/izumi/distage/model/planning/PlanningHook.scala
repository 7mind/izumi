package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleBase}
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan, ReplanningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

/**
  * @param replanningRequested when a hook is sure that the plan is completely correct after rewriting it should set this flag to false
  */
case class ExtendedFinalPlan(
                               plan: FinalPlan
                               , replanningRequested: Boolean
                             )

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

  def hookResolved(context: ReplanningContext, plan: FinalPlan): ExtendedFinalPlan = {
    Quirks.discard(context)
    ExtendedFinalPlan(plan, replanningRequested = false)
  }

  def hookFinal(context: ReplanningContext, plan: FinalPlan): ExtendedFinalPlan = {
    Quirks.discard(context)
    ExtendedFinalPlan(plan, replanningRequested = false)
  }

  protected def firstOnly(context: ReplanningContext, plan: FinalPlan)(f: FinalPlan => ExtendedFinalPlan): ExtendedFinalPlan = {
    if (context.count == 0) {
      f(plan)
    } else {
      ExtendedFinalPlan(plan, replanningRequested = false)
    }
  }
}
