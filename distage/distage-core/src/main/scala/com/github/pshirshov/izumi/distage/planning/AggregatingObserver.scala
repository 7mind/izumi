package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, OrderedPlan, SemiPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver

class AggregatingObserver(planningObservers: Set[PlanningObserver]) extends PlanningObserver {
  override def onSuccessfulStep(next: DodgyPlan): Unit = {
    planningObservers.foreach(_.onSuccessfulStep(next))
  }

  override def onPhase00PlanCompleted(plan: DodgyPlan): Unit = {
    planningObservers.foreach(_.onPhase00PlanCompleted(plan))
  }


  override def onPhase05PreGC(plan: SemiPlan): Unit = {
    planningObservers.foreach(_.onPhase05PreGC(plan))
  }

  override def onPhase10PostGC(plan: SemiPlan): Unit = {
    planningObservers.foreach(_.onPhase10PostGC(plan))
  }

  override def onPhase20Customization(plan: SemiPlan): Unit = {
    planningObservers.foreach(_.onPhase20Customization(plan))
  }

  override def onPhase50PreForwarding(plan: SemiPlan): Unit = {
    planningObservers.foreach(_.onPhase50PreForwarding(plan))
  }

  override def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = {
    planningObservers.foreach(_.onPhase90AfterForwarding(finalPlan))
  }
}


