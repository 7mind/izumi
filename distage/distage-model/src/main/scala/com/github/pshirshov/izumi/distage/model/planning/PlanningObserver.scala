package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, SemiPlan, OrderedPlan}

// TODO: Just one method onPhase(Id, plan) ?

trait PlanningObserver {
  def onSuccessfulStep(next: DodgyPlan): Unit

  def onPhase00PlanCompleted(plan: DodgyPlan): Unit
  def onPhase05PreFinalization(plan: SemiPlan): Unit
  def onPhase10PostFinalization(plan: SemiPlan): Unit
  def onPhase20Customization(plan: SemiPlan): Unit
  def onPhase50PreForwarding(plan: SemiPlan): Unit
  def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit
}

