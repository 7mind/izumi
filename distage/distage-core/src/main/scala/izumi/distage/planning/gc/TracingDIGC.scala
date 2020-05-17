package izumi.distage.planning.gc

import izumi.distage.model.plan.ExecutableOp._
import izumi.distage.model.plan.{ExecutableOp, Roots, SemiPlan, Wiring}
import izumi.distage.model.planning.DIGarbageCollector
import izumi.distage.model.reflection._
import izumi.fundamentals.graphs.AbstractGCTracer

import scala.collection.mutable

class TracingDIGC[OpType <: ExecutableOp]
(
  roots: Set[DIKey],
  fullIndex: Map[DIKey, OpType],
  override val ignoreMissingDeps: Boolean,
) extends AbstractGCTracer[DIKey, OpType] {

  @inline
  override protected def extractDependencies(index: Map[DIKey, OpType], node: OpType): Set[DIKey] = {
    node match {
      case op: ExecutableOp.InstantiationOp =>
        op match {
          case w: WiringOp =>
            w.wiring.requiredKeys
          case w: MonadicOp =>
            Set(w.effectKey)
          case c: CreateSet =>
            c.members.filterNot {
              index.get(_).exists {
                case ExecutableOp.WiringOp.ReferenceKey(_, Wiring.SingletonWiring.Reference(_, _, weak), _) =>
                  weak
                case _ =>
                  false
              }
            }
        }
      case _: ImportDependency =>
        Set.empty
    }
  }

  /** This method removes unreachable weak set members. */
  @inline
  override protected def prePrune(pruned: Pruned): Pruned = {
    val newTraced = new mutable.HashSet[DIKey]()
    newTraced ++= pruned.reachable

    val prefiltered = pruned.nodes.map {
      case c: CreateSet =>
        val weakMembers = c.members
          .map {
            m =>
              val setMemberOp = if (ignoreMissingDeps) {
                fullIndex.get(m)
              } else {
                Some(fullIndex(m))
              }
              (m, setMemberOp)
          }
          .collect {
            case (k, Some(op: ExecutableOp.WiringOp)) =>
              (k, op.wiring)
          }
          .collect {
            case (k, r: Wiring.SingletonWiring.Reference) if r.weak =>
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

    Pruned(prefiltered.map(_.asInstanceOf[OpType]), newTraced.toSet)
  }

  override protected def isRoot(node: DIKey): Boolean = roots.contains(node)

  override protected def id(node: OpType): DIKey = node.target
}

object TracingDIGC extends DIGarbageCollector {
  override def gc(plan: SemiPlan): SemiPlan = {
    plan.roots match {
      case Roots.Of(roots) =>
        val collected = new TracingDIGC(roots.toSet, plan.index, ignoreMissingDeps = false).gc(plan.steps)
        SemiPlan(collected.nodes, plan.roots)

      case Roots.Everything =>
        plan
    }
  }
}
