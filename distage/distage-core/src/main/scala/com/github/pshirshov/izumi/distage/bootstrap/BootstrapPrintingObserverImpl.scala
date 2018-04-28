package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver

class BootstrapPrintingObserverImpl() extends PlanningObserver {

  override def onFinalPlan(finalPlan: FinalPlan): Unit = {
    out(Seq(
      "=" * 60 + " Final Plan " + "=" * 60
      , s"$finalPlan"
      , "\n"
    ).mkString("\n"))
  }


  override def onResolvingFinished(finalPlan: FinalPlan): Unit = {
    out(Seq(
      "=" * 60 + " Resolved Plan " + "=" * 60
      , s"$finalPlan"
      , "\n"
    ).mkString("\n"))
  }

  override def onSuccessfulStep(next: DodgyPlan): Unit = {
    out(Seq(
      "=" * 60 + " Next Plan " + "=" * 60
      , s"$next"
      , "\n"
    ).mkString("\n"))
  }

  override def onReferencesResolved(plan: DodgyPlan): Unit = {

  }

  private def out(s: String): Unit = println(s)
}
