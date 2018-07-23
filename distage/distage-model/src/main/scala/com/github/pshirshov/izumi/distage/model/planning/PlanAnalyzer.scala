package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, PlanTopology}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

trait PlanAnalyzer {
  type Accumulator = mutable.HashMap[DIKey, mutable.Set[DIKey]]

  def topology(ops: Seq[ExecutableOp]): PlanTopology

  def topologyFwdRefs(plan: Iterable[ExecutableOp]): PlanTopology

  def requirements(op: ExecutableOp): Set[DIKey]
}
