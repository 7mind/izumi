package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.definition.Module
import com.github.pshirshov.izumi.distage.model.exceptions.UnsupportedOpException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, ProxyOp, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, SemiPlan}
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.graphs.AbstractGCTracer

import scala.collection.mutable


class TracingDIGC(plan: SemiPlan, isRoot: GCRootPredicate) extends AbstractGCTracer[DIKey, ExecutableOp] {
  @inline
  override protected def extract(index: Map[DIKey, ExecutableOp], node: ExecutableOp): Set[DIKey] = {
    node match {
      case op: ExecutableOp.InstantiationOp =>
        op match {
          case w: WiringOp =>
            w.wiring.requiredKeys
          case c: CreateSet =>
            c.members.filterNot {
              key =>
                index.get(key)
                  .collect {
                    case o: ExecutableOp.WiringOp =>
                      o.wiring
                  }
                  .collect {
                    case r: Wiring.UnaryWiring.Reference =>
                      r
                  }
                  .exists(_.weak == true)
            }
        }
      case _: ImportDependency =>
        Set.empty
      case p: ProxyOp =>
        throw new UnsupportedOpException(s"Garbage collector didn't expect a proxy operation", p)
    }
  }

  @inline
  override protected def prePrune(pruned: Pruned): Pruned = {
    // this method removes unreachable weak set members
    val newTraced = new mutable.HashSet[DIKey]()
    newTraced ++= pruned.reachable

    val prefiltered = pruned.nodes.map {
      case c: CreateSet =>
        val weakMembers = c.members
          .map(m => (m, plan.index(m)))
          .collect {
            case (k, op: ExecutableOp.WiringOp) =>
              (k, op.wiring)
          }
          .collect {
            case (k, r: Wiring.UnaryWiring.Reference) if r.weak =>
              (k, r)
          }


        val (referencedWeaks, unreferencedWeaks) = weakMembers.partition(kv => newTraced.contains(kv._2.key))

        newTraced ++= referencedWeaks.map(_._1)

        val referencedMembers = c.members
          .diff(unreferencedWeaks.map(_._1))
          .intersect(newTraced)

        c.copy(members = referencedMembers)

      case o =>
        o
    }

    Pruned(prefiltered, newTraced.toSet)
  }

  override protected def isRoot(node: DIKey): Boolean = isRoot.isRoot(node)

  override protected def id(node: ExecutableOp): DIKey = node.target
}

object TracingDIGC extends DIGarbageCollector {
  override def gc(plan: SemiPlan, isRoot: GCRootPredicate): SemiPlan = {
    val collected = new TracingDIGC(plan, isRoot).gc(plan.steps)

    val oldDefn = plan.definition.bindings
    val updatedDefn = Module.make(oldDefn.filter(b => collected.reachable.contains(b.key)))
    SemiPlan(updatedDefn, collected.nodes)
  }
}
