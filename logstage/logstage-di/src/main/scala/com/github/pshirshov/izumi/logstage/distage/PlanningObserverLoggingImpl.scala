package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.logstage.api.IzLogger

class PlanningObserverLoggingImpl(log: IzLogger) extends PlanningObserver {

  override def onFinalPlan(finalPlan: FinalPlan): Unit = {
    log.debug(s"DIStage produced final plan:\n$finalPlan")
  }


  override def onResolvingFinished(finalPlan: FinalPlan): Unit = {
    log.debug(s"DIStage resolved plan:\n$finalPlan")
  }

  override def onSuccessfulStep(next: DodgyPlan): Unit = {
    log.trace(s"DIStage performed planning step:\n$next")
  }

  override def onReferencesResolved(plan: DodgyPlan): Unit = {

  }
}
