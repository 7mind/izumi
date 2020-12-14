package izumi.distage.bootstrap

import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.planning.PlanningObserver
import izumi.fundamentals.platform.console.TrivialLogger

class BootstrapPlanningObserver(logger: TrivialLogger) extends PlanningObserver {
  override def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = {
    logger.log(
      Seq(
        "=" * 60 + " Final Plan " + "=" * 60,
        s"${finalPlan.toString}",
        "\n",
      ).mkString("\n")
    )
  }

}
