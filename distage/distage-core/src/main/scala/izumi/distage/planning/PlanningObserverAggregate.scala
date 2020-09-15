package izumi.distage.planning

import izumi.distage.model.plan.{OrderedPlan, SemiPlan}
import izumi.distage.model.planning.PlanningObserver

final class PlanningObserverAggregate(
  planningObservers: Set[PlanningObserver]
) extends PlanningObserver {

  override def onPhase05PreGC(plan: SemiPlan): Unit = {
    planningObservers.foreach(_.onPhase05PreGC(plan))
  }
  override def onPhase10PostGC(plan: SemiPlan): Unit = {
    planningObservers.foreach(_.onPhase10PostGC(plan))
  }
  override def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = {
    planningObservers.foreach(_.onPhase90AfterForwarding(finalPlan))
  }

}
