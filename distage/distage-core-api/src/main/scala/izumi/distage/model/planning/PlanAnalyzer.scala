package izumi.distage.model.planning

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.reflection._

trait PlanAnalyzer {
  def topology(plan: Iterable[ExecutableOp]): PlanTopology
  def topologyFwdRefs(plan: Iterable[ExecutableOp]): PlanTopology

  def requirements(op: ExecutableOp): Set[DIKey]
}
