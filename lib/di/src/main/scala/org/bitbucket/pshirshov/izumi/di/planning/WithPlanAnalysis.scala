package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.Statement
import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, ReadyPlan}
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.DependentOp

import scala.collection.mutable

trait WithPlanAnalysis {
  protected def computeFwdRefTable(plan: DodgyPlan): FdwRefTable = {
    // TODO: make it immu
    val dependencies = plan.steps.foldLeft(new mutable.HashMap[DIKey, mutable.Set[DIKey]]) {
      case (acc, Statement(op: DependentOp)) =>
        val forwardRefs = op.deps.map(_.wireWith).filterNot(acc.contains).toSet
        acc.getOrElseUpdate(op.target, mutable.Set.empty) ++= forwardRefs
        acc

      case (acc, Statement(op)) =>
        acc.getOrElseUpdate(op.target, mutable.Set.empty)
        acc

      case (acc, _) =>
        acc
    }
      .filter(_._2.nonEmpty)
      .mapValues(_.toSet).toMap

    val dependants = reverseReftable(dependencies)
    FdwRefTable(dependencies, dependants)
  }

  protected def computeFwdRefTable(plan: ReadyPlan): FdwRefTable = {
    // TODO: make it immu
    val dependencies = plan.getPlan.foldLeft(new mutable.HashMap[DIKey, mutable.Set[DIKey]]) {
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
