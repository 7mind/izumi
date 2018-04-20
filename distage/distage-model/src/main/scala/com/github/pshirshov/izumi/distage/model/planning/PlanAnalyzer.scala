package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.references.RefTable
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.collection.mutable

trait PlanAnalyzer {
  type Accumulator = mutable.HashMap[RuntimeDIUniverse.DIKey, mutable.Set[RuntimeDIUniverse.DIKey]]

  def computeFwdRefTable(plan: Iterable[ExecutableOp]): RefTable

  def computeFullRefTable(plan: Iterable[ExecutableOp]): RefTable

  def computeFwdRefTable(
                          plan: Iterable[ExecutableOp]
                          , refFilter: Accumulator => RuntimeDIUniverse.DIKey => Boolean
                          , postFilter: ((RuntimeDIUniverse.DIKey, mutable.Set[RuntimeDIUniverse.DIKey])) => Boolean
                        ): RefTable

  def reverseReftable(dependencies: Map[RuntimeDIUniverse.DIKey, Set[RuntimeDIUniverse.DIKey]]): Map[RuntimeDIUniverse.DIKey, Set[RuntimeDIUniverse.DIKey]]
}
