package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}

trait PlanningObserver {
  def onSuccessfulStep(next: DodgyPlan): Unit

  def onPhase00PlanCompleted(plan: DodgyPlan): Unit
  def onPhase10PostOrdering(plan: FinalPlan): Unit
  def onPhase20PreForwarding(plan: FinalPlan): Unit
  def onPhase30AfterForwarding(finalPlan: FinalPlan): Unit
}


