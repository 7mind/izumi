package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}

// TODO: Just one method onPhase(Id, plan) ?

trait PlanningObserver {
  def onSuccessfulStep(next: DodgyPlan): Unit

  def onPhase00PlanCompleted(plan: DodgyPlan): Unit
  def onPhase10PostFinalization(plan: FinalPlan): Unit
  def onPhase20Customization(plan: FinalPlan): Unit
  def onPhase50PreForwarding(plan: FinalPlan): Unit
  def onPhase90AfterForwarding(finalPlan: FinalPlan): Unit
}


