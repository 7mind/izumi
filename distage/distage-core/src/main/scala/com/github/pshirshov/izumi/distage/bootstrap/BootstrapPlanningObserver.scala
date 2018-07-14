package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan, ReplanningContext, ResolvedCyclesPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks


class BootstrapPlanningObserver(logger: TrivialLogger) extends PlanningObserver {

  override def onReferencesResolved(context: ReplanningContext, plan: ResolvedCyclesPlan): Unit = {
    Quirks.discard(context, plan)
  }

  override def onFinalPlan(context: ReplanningContext, finalPlan: FinalPlan): Unit = {
    Quirks.discard(context)

    logger.log(Seq(
      "=" * 60 + " Final Plan " + "=" * 60
      , s"$finalPlan"
      , "\n"
    ).mkString("\n"))
  }


  override def onResolvingFinished(context: ReplanningContext, finalPlan: FinalPlan): Unit = {
    Quirks.discard(context)

    logger.log(Seq(
      "=" * 60 + " Resolved Plan " + "=" * 60
      , s"$finalPlan"
      , "\n"
    ).mkString("\n"))
  }

  override def onSuccessfulStep(next: DodgyPlan): Unit = {
    logger.log(Seq(
      "=" * 60 + " Next Plan " + "=" * 60
      , s"$next"
      , "\n"
    ).mkString("\n"))
  }

}
