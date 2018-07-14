package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan, ReplanningContext, ResolvedCyclesPlan}

trait PlanningObserver {
  def onSuccessfulStep(next: DodgyPlan): Unit

  def onReferencesResolved(context: ReplanningContext, plan: ResolvedCyclesPlan): Unit

  def onResolvingFinished(context: ReplanningContext, plan: FinalPlan): Unit

  def onFinalPlan(context: ReplanningContext, finalPlan: FinalPlan): Unit
}


