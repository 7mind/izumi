package izumi.distage.bootstrap

import izumi.distage.model.plan.{OrderedPlan, SemiPlan}
import izumi.distage.model.planning.PlanningObserver
import izumi.fundamentals.platform.console.TrivialLogger

class BootstrapPlanningObserver(logger: TrivialLogger) extends PlanningObserver {
  override def onPhase05PreGC(plan: SemiPlan): Unit = {}
  override def onPhase10PostGC(plan: SemiPlan): Unit = {}
  override def onPhase20Customization(plan: SemiPlan): Unit = {}

  override def onPhase50PreForwarding(finalPlan: SemiPlan): Unit = {
    doLog("Resolved Plan", finalPlan.toString)
  }

  override def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = {
    doLog("Final Plan", finalPlan.toString)
  }

  private def doLog(title: => String, body: => String): Unit = {
    logger.log(
      Seq(
        "=" * 60 + s" $title " + "=" * 60,
        s"$body",
        "\n",
      ).mkString("\n")
    )
  }
}
