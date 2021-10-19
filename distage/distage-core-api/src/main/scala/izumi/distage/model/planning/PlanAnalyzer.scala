package izumi.distage.model.planning

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.reflection.DIKey

trait PlanAnalyzer {
  def requirements(op: ExecutableOp): Seq[(DIKey, Set[DIKey])]
}
