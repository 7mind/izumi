package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, SemiPlan, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger

class BootstrapPlanningObserver(logger: TrivialLogger) extends PlanningObserver {
  override def onSuccessfulStep(next: DodgyPlan): Unit = {
    doLog("Next Plan", next.toString)
  }

  override def onPhase00PlanCompleted(plan: DodgyPlan): Unit = {

  }

  override def onPhase05PreGC(plan: SemiPlan): Unit = {

  }

  override def onPhase10PostGC(plan: SemiPlan): Unit = {

  }

  override def onPhase20Customization(plan: SemiPlan): Unit = {

  }

  override def onPhase50PreForwarding(finalPlan: SemiPlan): Unit = {
    logPlan(finalPlan, "Resolved Plan")
  }

  override def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = {
    doLog("Final Plan", finalPlan.toString)
  }

  private def logPlan(finalPlan: SemiPlan, title: String): Unit = {
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
