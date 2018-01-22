package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, FinalPlan}

trait PlanningObsever {
  def onSuccessfulStep(next: DodgyPlan): Unit
  def onReferencesResolved(plan: DodgyPlan): Unit
  def onResolvingFinished(plan: FinalPlan): Unit
  def onFinalPlan(finalPlan: FinalPlan): Unit
}


