package izumi.distage.model

import izumi.distage.model.plan.{AbstractPlan, ExecutableOp, OrderedPlan}
import izumi.distage.model.planning.PlanSplitter

trait PlannerExtendedAPI extends PlanSplitter {
  this: Planner =>

  def merge[OpType <: ExecutableOp](a: AbstractPlan[OpType], b: AbstractPlan[OpType]): OrderedPlan
}
