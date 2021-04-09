package izumi.distage.planning

import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import izumi.distage.model.planning.PlanningObserver
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.DG

final class PlanningObserverAggregate(
  planningObservers: Set[PlanningObserver]
) extends PlanningObserver {

  override def onPlanningFinished(input: PlannerInput, plan: DG[DIKey, ExecutableOp]): Unit = {
    planningObservers.foreach(_.onPlanningFinished(input, plan))
  }

  override def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = {
    planningObservers.foreach(_.onPhase90AfterForwarding(finalPlan))
  }

}
