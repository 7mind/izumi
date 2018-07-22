package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, PlanTopology, XPlanTopology}
import com.github.pshirshov.izumi.distage.model.references.RefTable
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

trait PlanAnalyzer {
  type Accumulator = mutable.HashMap[DIKey, mutable.Set[DIKey]]

  def topoBuild(ops: Seq[ExecutableOp]): PlanTopology

  def topoExtend(topology: XPlanTopology, op: ExecutableOp): Unit

  def computeFwdRefTable(plan: Iterable[ExecutableOp]): RefTable

  def computeFullRefTable(plan: Iterable[ExecutableOp]): RefTable

  def requirements(op: ExecutableOp): Set[DIKey]
}
