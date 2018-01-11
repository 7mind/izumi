package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.DependentOp

import scala.collection.mutable

case class RefTable(dependencies: Map[DIKey, Set[DIKey]], dependants: Map[DIKey, Set[DIKey]])

trait WithPlanAnalysis {
  private type Accumulator = mutable.HashMap[DIKey, mutable.Set[DIKey]]

  protected def computeFwdRefTable(plan: Stream[ExecutableOp]): RefTable = {
    computeFwdRefTable(
      plan
      , (acc) => (key) => acc.contains(key)
      , _._2.nonEmpty
    )
  }

  protected def computeFullRefTable(plan: Stream[ExecutableOp]): RefTable = {
    computeFwdRefTable(
      plan
      , (acc) => (key) => false
      , _ => true
    )
  }

  protected def computeFwdRefTable(
                                    plan: Stream[ExecutableOp]
                                    , refFilter: Accumulator => DIKey => Boolean
                                    , postFilter: ((DIKey, mutable.Set[DIKey])) => Boolean
                                  ): RefTable = {
    // TODO: make it immu
    val dependencies = plan.foldLeft(new Accumulator) {
      case (acc, op: DependentOp) =>
        val forwardRefs = op.deps.map(_.wireWith).filterNot(refFilter(acc)).toSet
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

  protected def reverseReftable(dependencies: Map[DIKey, Set[DIKey]]): Map[DIKey, Set[DIKey]] = {
    val dependants = dependencies.foldLeft(new Accumulator with mutable.MultiMap[DIKey, DIKey]) {
      case (acc, (reference, referencee)) =>
        referencee.foreach(acc.addBinding(_, reference))
        acc
    }
    dependants.mapValues(_.toSet).toMap
  }
}
