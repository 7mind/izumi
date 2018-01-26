package org.bitbucket.pshirshov.izumi.distage.planning

import org.bitbucket.pshirshov.izumi.distage.model.DIKey
import org.bitbucket.pshirshov.izumi.distage.model.plan.{ExecutableOp, RefTable}

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
}
