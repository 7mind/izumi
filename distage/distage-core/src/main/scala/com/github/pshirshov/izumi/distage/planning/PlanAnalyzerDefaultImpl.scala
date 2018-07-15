package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, PlanTopology}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan.PlanTopology.empty
import com.github.pshirshov.izumi.distage.model.planning.PlanAnalyzer
import com.github.pshirshov.izumi.distage.model.references.RefTable
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable


class PlanAnalyzerDefaultImpl extends PlanAnalyzer {
  def topoBuild(ops: Seq[ExecutableOp]): PlanTopology = {
    val out = empty
    ops
      .collect({ case i: InstantiationOp => i })
      .foreach(topoExtend(out, _))
    out
  }

  def topoExtend(topology: PlanTopology, op: InstantiationOp): Unit = {
    val req = requirements(op)
    val transitiveReq = topology.dependencies.getOrElse(op.target, mutable.Set.empty[DIKey]) ++ req
    topology.register(op.target, transitiveReq.toSet)
  }

  def computeFwdRefTable(plan: Iterable[ExecutableOp]): RefTable = {
    computeFwdRefTable(
      plan
      , (acc) => (key) => acc.contains(key)
      , _._2.nonEmpty
    )
  }

  def computeFullRefTable(plan: Iterable[ExecutableOp]): RefTable = {
    computeFwdRefTable(
      plan
      , (acc) => (key) => false
      , _ => true
    )
  }

  type RefFilter = Accumulator => DIKey => Boolean
  type PostFilter = ((DIKey, mutable.Set[DIKey])) => Boolean

  def requirements(op: InstantiationOp): Set[DIKey] = {
    op match {
      case w: WiringOp =>
        w.wiring match {
          case r: Wiring.UnaryWiring.Reference =>
            Set(r.key)

          case o =>
            o.associations.map(_.wireWith).toSet
        }

      case c: CreateSet =>
        c.members

      case _ =>
        Set.empty
    }
  }

  private def computeFwdRefTable(plan: Iterable[ExecutableOp], refFilter: RefFilter, postFilter: PostFilter): RefTable = {

    val dependencies = plan.toList.foldLeft(new Accumulator) {
      case (acc, op: InstantiationOp) =>
        acc.getOrElseUpdate(op.target, mutable.Set.empty) ++= requirements(op).filterNot(refFilter(acc))
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

  private def reverseReftable(dependencies: Map[DIKey, Set[DIKey]]): Map[DIKey, Set[DIKey]] = {
    val dependants = dependencies.foldLeft(new Accumulator with mutable.MultiMap[DIKey, DIKey]) {
      case (acc, (reference, referencee)) =>
        referencee.foreach(acc.addBinding(_, reference))
        acc
    }
    dependants.mapValues(_.toSet).toMap
  }
}
