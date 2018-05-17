package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.InstantiationOp
import com.github.pshirshov.izumi.distage.model.references.RefTable
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

trait PlanAnalyzer {
  type Accumulator = mutable.HashMap[DIKey, mutable.Set[DIKey]]

  def computeFwdRefTable(plan: Iterable[ExecutableOp]): RefTable

  def computeFullRefTable(plan: Iterable[ExecutableOp]): RefTable

  def computeFwdRefTable(
                          plan: Iterable[ExecutableOp]
                          , refFilter: Accumulator => DIKey => Boolean
                          , postFilter: ((DIKey, mutable.Set[DIKey])) => Boolean
                        ): RefTable

  def reverseReftable(dependencies: Map[DIKey, Set[DIKey]]): Map[DIKey, Set[DIKey]]

  def requirements(op: InstantiationOp): Set[DIKey]
}
