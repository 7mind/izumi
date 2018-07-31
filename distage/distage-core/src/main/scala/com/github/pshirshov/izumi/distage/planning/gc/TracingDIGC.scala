package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.definition.SimpleModuleDef
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, SemiPlan}
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.annotation.tailrec
import scala.collection.mutable

object TracingDIGC extends DIGarbageCollector {
  override def gc(plan: SemiPlan, isRoot: GCRootPredicate): SemiPlan = {
    val toLeave = mutable.HashSet[DIKey]()
    toLeave ++= plan.steps.map(_.target).filter(isRoot.isRoot)
    allDeps(plan.steps.map(v => v.target -> v).toMap, toLeave.toSet, toLeave)
    val oldDefn = plan.definition.bindings
    val updatedDefn = SimpleModuleDef(oldDefn.filter(b => toLeave.contains(b.key)))

    val steps = plan.steps
      .map {
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


          val (referencedWeaks, unreferencedWeaks) = weakMembers.partition(kv => toLeave.contains(kv._2.key))

          toLeave ++= referencedWeaks.map(_._1)

          val referencedMembers = c.members
            .diff(unreferencedWeaks.map(_._1))
            .intersect(toLeave)

          c.copy(members = referencedMembers)
        case o => o
      }
      .filter(s => toLeave.contains(s.target))

    SemiPlan(updatedDefn, steps)
  }

  @tailrec
  private def allDeps(index: Map[DIKey, ExecutableOp], toTrace: Set[DIKey], reachable: mutable.HashSet[DIKey]): Unit = {
    // TODO: inefficient

    val newDeps = toTrace.map(index.apply).flatMap {
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

      case p: InitProxy =>
        p.dependencies
      case _: MakeProxy =>
        Seq.empty
      case _: ImportDependency =>
        Seq.empty
      case _ =>
        Seq.empty
    }

    if (newDeps.nonEmpty) {
      reachable ++= newDeps
      allDeps(index, newDeps, reachable)
    }
  }
}
