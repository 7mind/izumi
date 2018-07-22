package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.definition.SimpleModuleDef
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, SemiPlan}
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.annotation.tailrec
import scala.collection.mutable

object TracingDIGC extends DIGarbageCollector {
  override def gc(plan: SemiPlan, isRoot: GCRootPredicate): SemiPlan = {
    val toLeave = mutable.HashSet[RuntimeDIUniverse.DIKey]()
    toLeave ++= plan.steps.map(_.target).filter(isRoot.isRoot)
    allDeps(plan.steps.map(v => v.target -> v).toMap, toLeave.toSet, toLeave)
    val original = plan.definition.bindings
    val refinedPlan = SimpleModuleDef(original.filter(b => toLeave.contains(b.key)))
    //println(s"${original.size - refinedPlan.bindings.size} component(s) collected by gc")
    val steps = plan.steps.filter(s => toLeave.contains(s.target))
    SemiPlan(refinedPlan, steps)
  }

  @tailrec
  private def allDeps(ops: Map[RuntimeDIUniverse.DIKey, ExecutableOp], depsToTrace: Set[RuntimeDIUniverse.DIKey], deps: mutable.HashSet[RuntimeDIUniverse.DIKey]): Unit = {
    // TODO: inefficient

    val newDeps = depsToTrace.map(ops.apply).flatMap {
      case w: WiringOp =>
        w.wiring.associations.map(_.wireWith)
      case c: CreateSet =>
        c.members
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
      deps ++= newDeps
      allDeps(ops, newDeps, deps)
    }
  }
}
