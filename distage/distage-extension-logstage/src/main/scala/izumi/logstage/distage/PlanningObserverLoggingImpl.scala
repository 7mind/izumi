package izumi.logstage.distage

import izumi.distage.model.plan.{OrderedPlan, SemiPlan}
import izumi.distage.model.planning.PlanningObserver
import izumi.logstage.api.IzLogger

final class PlanningObserverLoggingImpl(
  log: IzLogger
) extends PlanningObserver {
  override def onPhase05PreGC(plan: SemiPlan): Unit = {
    log.trace(s"[onPhase05PreFinalization]:\n$plan")
  }
  override def onPhase10PostGC(plan: SemiPlan): Unit = {
    log.trace(s"[onPhase10PostOrdering]:\n$plan")
  }
  override def onPhase20Customization(plan: SemiPlan): Unit = {
    log.trace(s"[onPhase15PostOrdering]:\n$plan")
  }
  override def onPhase50PreForwarding(plan: SemiPlan): Unit = {
    log.trace(s"[onPhase20PreForwarding]:\n$plan")
  }
  override def onPhase90AfterForwarding(plan: OrderedPlan): Unit = {
    log.trace(s"[onPhase30AfterForwarding]:\n$plan")
  }
}
