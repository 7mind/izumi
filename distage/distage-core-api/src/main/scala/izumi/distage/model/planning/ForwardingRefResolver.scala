package izumi.distage.model.planning

import izumi.distage.model.plan.{ExecutableOp, OrderedPlan, Roots}
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.DG

trait ForwardingRefResolver {
  def resolveMatrix(plan: DG[DIKey, ExecutableOp.SemiplanOp]): DG[DIKey, ExecutableOp]
}
