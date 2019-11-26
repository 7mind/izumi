package izumi.distage.planning.gc

import izumi.distage.model.GCMode
import izumi.distage.model.definition.Module
import izumi.distage.model.exceptions.UnsupportedOpException
import izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, MonadicOp, ProxyOp, WiringOp}
import izumi.distage.model.plan.{ExecutableOp, SemiPlan}
import izumi.distage.model.planning.DIGarbageCollector
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.fundamentals.graphs.AbstractGCTracer

import scala.collection.mutable

class TracingDIGC(
  roots: Set[DIKey],
  fullIndex: Map[DIKey, ExecutableOp],
  override val ignoreMissingDeps: Boolean,
) extends AbstractGCTracer[DIKey, ExecutableOp] {

  @inline
  override protected def extractDependencies(index: Map[DIKey, ExecutableOp], node: ExecutableOp): Set[DIKey] = {
    node match {
      case op: ExecutableOp.InstantiationOp =>
        op match {
          case w: WiringOp =>
            w.wiring.requiredKeys
          case w: MonadicOp =>
            w.effectWiring.requiredKeys
          case c: CreateSet =>
            c.members.filterNot {
              key =>
                index
                  .get(key)
                  .collect {
                    case o: ExecutableOp.WiringOp =>
                      o.wiring
                    case o: ExecutableOp.MonadicOp =>
                      o.effectWiring
                  }
                  .exists {
                    case r: Wiring.SingletonWiring.Reference =>
                      r.weak
                    case _ =>
                      false
                  }
            }
        }
      case _: ImportDependency =>
        Set.empty
      case p: ProxyOp =>
        throw new UnsupportedOpException(s"Garbage collector didn't expect a proxy operation; proxies can't exist at this stage", p)
    }
  }

  /** This method removes unreachable weak set members. */
  @inline
  override protected def prePrune(pruned: Pruned): Pruned = {
    val newTraced = new mutable.HashSet[DIKey]()
    newTraced ++= pruned.reachable

    val prefiltered = pruned.nodes.map {
      case c: CreateSet =>
        val weakMembers = c.members.map {
          m =>
            val setMemberOp = if (ignoreMissingDeps) {
              fullIndex.get(m)
            } else {
              Some(fullIndex(m))
            }
            (m, setMemberOp)
        }.collect {
          case (k, Some(op: ExecutableOp.WiringOp)) =>
            (k, op.wiring)
          case (k, Some(op: ExecutableOp.MonadicOp)) =>
            (k, op.effectWiring)
        }.collect {
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

    Pruned(prefiltered, newTraced.toSet)
  }

  override protected def isRoot(node: DIKey): Boolean = roots.contains(node)

  override protected def id(node: ExecutableOp): DIKey = node.target
}

object TracingDIGC extends DIGarbageCollector {
  override def gc(plan: SemiPlan): SemiPlan = {
    plan.gcMode match {
      case GCMode.GCRoots(roots) =>
        assert(roots.nonEmpty)
        val collected = new TracingDIGC(roots, plan.index, ignoreMissingDeps = false).gc(plan.steps)

        val updatedDefn = {
          val oldDefn = plan.definition.bindings
          val reachable = collected.reachable
          Module.make(oldDefn.filter(reachable contains _.key))
        }

        SemiPlan(updatedDefn, collected.nodes, plan.gcMode)

      case GCMode.NoGC =>
        plan
    }
  }
}
