package izumi.distage.model.planning

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.topology.PlanTopology

trait PlanAnalyzer {
  def topology(plan: Iterable[ExecutableOp]): PlanTopology

  def topologyFwdRefs(plan: Iterable[ExecutableOp]): PlanTopology
}
