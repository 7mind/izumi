package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver

class PlanningObserverDefaultImpl extends PlanningObserver {
  override def onFinalPlan(finalPlan: FinalPlan): Unit = {

  }

  override def onResolvingFinished(finalPlan: FinalPlan): Unit = {

  }

  override def onSuccessfulStep(next: DodgyPlan): Unit = {

  }

  override def onReferencesResolved(plan: DodgyPlan): Unit = {
  }
}

