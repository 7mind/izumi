package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, ReplanningContext, ResolvedCyclesPlan}

trait PlanningObserver {
  def onSuccessfulStep(next: DodgyPlan): Unit

  def onReferencesResolved(context: ReplanningContext, plan: ResolvedCyclesPlan): Unit

  def onResolvingFinished(context: ReplanningContext, plan: ExtendedFinalPlan): Unit

  def onFinalPlan(context: ReplanningContext, plan: ExtendedFinalPlan): Unit
}


