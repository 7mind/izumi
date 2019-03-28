package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, InstantiationOp, MonadicOp, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.PlanAnalyzer
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

class PlanAnalyzerDefaultImpl extends PlanAnalyzer {
  def topology(ops: Seq[ExecutableOp]): PlanTopology = {
    computeTopology(
      ops
      , (_) => (_) => false
      , _ => true
    )
  }

  def topologyFwdRefs(plan: Iterable[ExecutableOp]): PlanTopology = {
    computeTopology(
      plan
      , (acc) => (key) => acc.contains(key)
      , _._2.nonEmpty
    )
  }

  def requirements(op: ExecutableOp): Set[DIKey] = {
    op match {
      case w: WiringOp =>
        w.wiring.requiredKeys

      case w: MonadicOp =>
        w.effectWiring.requiredKeys

      case c: CreateSet =>
        c.members

      case _: MakeProxy =>
        Set.empty

      case _: ImportDependency =>
        Set.empty

      case i: InitProxy =>
        Set(i.proxy.target) ++ requirements(i.proxy.op)
    }
  }

  private type RefFilter = Accumulator => DIKey => Boolean

  private type PostFilter = ((DIKey, mutable.Set[DIKey])) => Boolean

  private def computeTopology(plan: Iterable[ExecutableOp], refFilter: RefFilter, postFilter: PostFilter): PlanTopology = {
    val dependencies = plan.toList.foldLeft(new Accumulator) {
      case (acc, op: InstantiationOp) =>
        val filtered = requirements(op).filterNot(refFilter(acc)) // it's important NOT to update acc before we computed deps
        acc.getOrElseUpdate(op.target, mutable.Set.empty) ++= filtered
        acc

      case (acc, op) =>
        acc.getOrElseUpdate(op.target, mutable.Set.empty)
        acc
    }
      .filter(postFilter)
      .mapValues(_.toSet).toMap

    val dependants = reverseReftable(dependencies)
    PlanTopologyImmutable(DependencyGraph(dependants, DependencyKind.Required), DependencyGraph(dependencies, DependencyKind.Depends))
  }

  private def reverseReftable(dependencies: Map[DIKey, Set[DIKey]]): Map[DIKey, Set[DIKey]] = {
    val dependants = dependencies.foldLeft(new Accumulator with mutable.MultiMap[DIKey, DIKey]) {
      case (acc, (reference, referencee)) =>
        acc.getOrElseUpdate(reference, mutable.Set.empty[DIKey])
        referencee.foreach(acc.addBinding(_, reference))
        acc
    }
    dependants.mapValues(_.toSet).toMap
  }
}
