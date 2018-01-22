package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, RefTable}
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.WiringOp

import scala.collection.mutable


class PlanAnalyzerDefaultImpl() extends PlanAnalyzer {

  override def computeFwdRefTable(plan: Iterable[ExecutableOp]): RefTable = {
    computeFwdRefTable(
      plan
      , (acc) => (key) => acc.contains(key)
      , _._2.nonEmpty
    )
  }

  override def computeFullRefTable(plan: Iterable[ExecutableOp]): RefTable = {
    computeFwdRefTable(
      plan
      , (acc) => (key) => false
      , _ => true
    )
  }

  override def computeFwdRefTable(
                                   plan: Iterable[ExecutableOp]
                                 , refFilter: Accumulator => DIKey => Boolean
                                 , postFilter: ((DIKey, mutable.Set[DIKey])) => Boolean
                                 ): RefTable = {

    val dependencies = plan.toList.foldLeft(new Accumulator) {
      case (acc, op: WiringOp) =>
        val forwardRefs = op.wiring.associations.map(_.wireWith).filterNot(refFilter(acc)).toSet
        acc.getOrElseUpdate(op.target, mutable.Set.empty) ++= forwardRefs
        acc

      case (acc, op) =>
        acc.getOrElseUpdate(op.target, mutable.Set.empty)
        acc
    }
      .filter(postFilter)
      .mapValues(_.toSet).toMap

    val dependants = reverseReftable(dependencies)
    RefTable(dependencies, dependants)
  }

  override def reverseReftable(dependencies: Map[DIKey, Set[DIKey]]): Map[DIKey, Set[DIKey]] = {
    val dependants = dependencies.foldLeft(new Accumulator with mutable.MultiMap[DIKey, DIKey]) {
      case (acc, (reference, referencee)) =>
        referencee.foreach(acc.addBinding(_, reference))
        acc
    }
    dependants.mapValues(_.toSet).toMap
  }
}
