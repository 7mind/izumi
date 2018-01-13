package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, FinalPlan}

trait PlanningObsever {
  def onFinalPlan(finalPlan: FinalPlan): Unit
  def onSuccessfulStep(next: DodgyPlan): Unit
  def onFailedStep(next: DodgyPlan): Unit
}

