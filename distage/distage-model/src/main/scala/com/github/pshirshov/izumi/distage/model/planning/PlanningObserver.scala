package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}

trait PlanningObserver {
  def onSuccessfulStep(next: DodgyPlan): Unit
  def onReferencesResolved(plan: DodgyPlan): Unit
  def onResolvingFinished(plan: FinalPlan): Unit
  def onFinalPlan(finalPlan: FinalPlan): Unit
}


