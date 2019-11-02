package izumi.logstage.distage

import izumi.distage.model.plan.{DodgyPlan, SemiPlan, OrderedPlan}
import izumi.distage.model.planning.PlanningObserver
import izumi.logstage.api.IzLogger

class PlanningObserverLoggingImpl(log: IzLogger) extends PlanningObserver {
  override def onSuccessfulStep(next: DodgyPlan): Unit = {
    log.trace(s"DIStage performed planning step:\n$next")
  }

  override def onPhase00PlanCompleted(plan: DodgyPlan): Unit = {
    log.debug(s"[onPhase00PlanCompleted]:\n$plan")
  }


  override def onPhase05PreGC(plan: SemiPlan): Unit = {
    log.debug(s"[onPhase05PreFinalization]:\n$plan")
  }

  override def onPhase10PostGC(plan: SemiPlan): Unit = {
    log.debug(s"[onPhase10PostOrdering]:\n$plan")
  }

  override def onPhase20Customization(plan: SemiPlan): Unit = {
    log.debug(s"[onPhase15PostOrdering]:\n$plan")
  }

  override def onPhase50PreForwarding(plan: SemiPlan): Unit = {
    log.debug(s"[onPhase20PreForwarding]:\n$plan")
  }

  override def onPhase90AfterForwarding(plan: OrderedPlan): Unit = {
    log.debug(s"[onPhase30AfterForwarding]:\n$plan")
  }


}


