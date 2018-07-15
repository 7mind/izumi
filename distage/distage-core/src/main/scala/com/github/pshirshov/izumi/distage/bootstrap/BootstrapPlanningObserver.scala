package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger


class BootstrapPlanningObserver(logger: TrivialLogger) extends PlanningObserver {
  override def onSuccessfulStep(next: DodgyPlan): Unit = {
    doLog("Next Plan", next.toString)
  }

  override def onPhase00PlanCompleted(plan: DodgyPlan): Unit = {

  }

  override def onPhase10PostOrdering(plan: FinalPlan): Unit = {

  }

  override def onPhase15PostOrdering(plan: FinalPlan): Unit = {

  }

  override def onPhase20PreForwarding(finalPlan: FinalPlan): Unit = {
    logPlan(finalPlan, "Resolved Plan")
  }

  override def onPhase30AfterForwarding(finalPlan: FinalPlan): Unit = {
    logPlan(finalPlan, "Final Plan")
  }

  private def logPlan(finalPlan: FinalPlan, title: String): Unit = {
    doLog(title, finalPlan.toString)
  }

  private def doLog(title: String, body: String): Unit = {
    logger.log(Seq(
      "=" * 60 + s" $title " + "=" * 60
      , s"$body"
      , "\n"
    ).mkString("\n"))
  }
}
