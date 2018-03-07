package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.references.RefTable
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

import scala.collection.mutable

trait PlanAnalyzer {
  type Accumulator = mutable.HashMap[RuntimeUniverse.DIKey, mutable.Set[RuntimeUniverse.DIKey]]

  def computeFwdRefTable(plan: Iterable[ExecutableOp]): RefTable

  def computeFullRefTable(plan: Iterable[ExecutableOp]): RefTable

  def computeFwdRefTable(
                          plan: Iterable[ExecutableOp]
                          , refFilter: Accumulator => RuntimeUniverse.DIKey => Boolean
                          , postFilter: ((RuntimeUniverse.DIKey, mutable.Set[RuntimeUniverse.DIKey])) => Boolean
                        ): RefTable

  def reverseReftable(dependencies: Map[RuntimeUniverse.DIKey, Set[RuntimeUniverse.DIKey]]): Map[RuntimeUniverse.DIKey, Set[RuntimeUniverse.DIKey]]
}
