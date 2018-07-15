package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver

class PlanningObserverDefaultImpl extends PlanningObserver {
  override def onSuccessfulStep(next: DodgyPlan): Unit = {}

  override def onPhase00PlanCompleted(plan: DodgyPlan): Unit = {}

  override def onPhase10PostOrdering(plan: FinalPlan): Unit = {}

  override def onPhase30AfterForwarding(finalPlan: FinalPlan): Unit = {}

  override def onPhase20PreForwarding(finalPlan: FinalPlan): Unit = {}
}

