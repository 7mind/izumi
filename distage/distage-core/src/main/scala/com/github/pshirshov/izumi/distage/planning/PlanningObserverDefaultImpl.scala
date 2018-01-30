package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver

class PlanningObserverDefaultImpl extends PlanningObserver {

  override def onFinalPlan(finalPlan: FinalPlan): Unit = {
    System.err.println("=" * 60 + " Final Plan " + "=" * 60)
    System.err.println(s"$finalPlan")
    System.err.println("\n")
  }


  override def onResolvingFinished(finalPlan: FinalPlan): Unit = {
    System.err.println("=" * 60 + " Resolved Plan " + "=" * 60)
    System.err.println(s"$finalPlan")
    System.err.println("\n")
  }

  override def onSuccessfulStep(next: DodgyPlan): Unit = {
//    System.err.println("-" * 60 + " Next Plan " + "-" * 60)
//    System.err.println(next)
  }

  override def onReferencesResolved(plan: DodgyPlan): Unit = {

  }
}
