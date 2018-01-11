package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.Statement
import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, ExecutableOp, ReadyPlan}
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.DependentOp

import scala.collection.mutable

case class FdwRefTable(dependencies: Map[DIKey, Set[DIKey]], dependants: Map[DIKey, Set[DIKey]])

trait WithPlanAnalysis {
  protected def computeFwdRefTable(plan: DodgyPlan): FdwRefTable = {
    computeFwdRefTable(plan.steps.collect { case Statement(op) => op }.toStream)
  }

  protected def computeFwdRefTable(plan: ReadyPlan): FdwRefTable = {
    computeFwdRefTable(plan.getPlan.toStream)
  }

  protected def computeFwdRefTable(plan: Stream[ExecutableOp]): FdwRefTable = {
    // TODO: make it immu
    val dependencies = plan.foldLeft(new mutable.HashMap[DIKey, mutable.Set[DIKey]]) {
      case (acc, op: DependentOp) =>
        val forwardRefs = op.deps.map(_.wireWith).filterNot(acc.contains).toSet
        acc.getOrElseUpdate(op.target, mutable.Set.empty) ++= forwardRefs
        acc

      case (acc, op) =>
        acc.getOrElseUpdate(op.target, mutable.Set.empty)
        acc
    }
      .filter(_._2.nonEmpty)
      .mapValues(_.toSet).toMap

    val dependants = reverseReftable(dependencies)
    FdwRefTable(dependencies, dependants)
  }

  protected def reverseReftable(dependencies: Map[DIKey, Set[DIKey]]): Map[DIKey, Set[DIKey]] = {
    val dependants = dependencies.foldLeft(new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]) {
      case (acc, (reference, referencee)) =>
        referencee.foreach(acc.addBinding(_, reference))
        acc
    }
    dependants.mapValues(_.toSet).toMap
  }
}
