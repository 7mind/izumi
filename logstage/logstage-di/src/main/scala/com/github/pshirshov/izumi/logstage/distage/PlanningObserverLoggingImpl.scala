package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, ReplanningContext, ResolvedCyclesPlan}
import com.github.pshirshov.izumi.distage.model.planning.{PlanningObserver, ExtendedFinalPlan}
import com.github.pshirshov.izumi.logstage.api.IzLogger

class PlanningObserverLoggingImpl(log: IzLogger) extends PlanningObserver {
  override def onSuccessfulStep(next: DodgyPlan): Unit = {
    log.trace(s"DIStage performed planning step:\n$next")
  }

  override def onReferencesResolved(context: ReplanningContext, plan: ResolvedCyclesPlan): Unit = {
    log.trace(s"DIStage performed cycle resolution step (iteration ${context.count}):\n$plan")
  }

  override def onResolvingFinished(context: ReplanningContext, finalPlan: ExtendedFinalPlan): Unit = {
    log.debug(s"DIStage resolved plan (iteration ${context.count}):\n${finalPlan.plan}")
  }

  override def onFinalPlan(context: ReplanningContext, finalPlan: ExtendedFinalPlan): Unit = {
    log.debug(s"DIStage produced final plan (iteration ${context.count}):\n${finalPlan.plan}")
  }
}


