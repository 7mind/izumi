package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, FinalPlan}

class PlanningObserverDefaultImpl extends PlanningObsever {

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

  override def onFailedStep(next: DodgyPlan): Unit = {
//    System.err.println("-" * 60 + " Next Plan (failed) " + "-" * 60)
//    System.err.println(next)
  }

  override def onReferencesResolved(plan: DodgyPlan): Unit = {
    
  }
}
