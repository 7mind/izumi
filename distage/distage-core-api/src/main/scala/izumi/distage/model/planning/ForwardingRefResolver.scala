package izumi.distage.model.planning

import izumi.distage.model.definition.errors.DIError.LoopResolutionError
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.graphs.DG

trait ForwardingRefResolver {
  def resolveMatrix(plan: DG[DIKey, ExecutableOp.SemiplanOp]): Either[NEList[LoopResolutionError], DG[DIKey, ExecutableOp]]
}
