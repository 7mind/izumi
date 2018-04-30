package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.TrivialModuleDef
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.SetOp.AddToSet
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, FinalPlan, FinalPlanImmutableImpl}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

import scala.annotation.tailrec
import scala.collection.mutable


trait DIGarbageCollector {
  def gc(plan: FinalPlan, isRoot: RuntimeDIUniverse.DIKey => Boolean): FinalPlan
}

object DIGarbageCollector {
  def isRoot(roots: Set[RuntimeDIUniverse.DIKey])(key: RuntimeDIUniverse.DIKey): Boolean = {
    roots.contains(key)
  }
}

object NullDiGC extends DIGarbageCollector {
  override def gc(plan: FinalPlan, isRoot: RuntimeDIUniverse.DIKey => Boolean): FinalPlan = {
    Quirks.discard(isRoot)
    plan
  }
}


object TracingDIGC extends DIGarbageCollector {
  override def gc(plan: FinalPlan, isRoot: RuntimeDIUniverse.DIKey => Boolean): FinalPlan = {
    val toLeave = mutable.HashSet[RuntimeDIUniverse.DIKey]()
    toLeave ++= plan.steps.map(_.target).filter(isRoot)
    allDeps(plan.steps.map(v => v.target -> v).toMap, toLeave)
    val refinedPlan = TrivialModuleDef(plan.definition.bindings.filter(b => toLeave.contains(b.target)))
    val steps = plan.steps.filter(s => toLeave.contains(s.target))
    FinalPlanImmutableImpl(refinedPlan)(steps)
  }

  @tailrec
  private def allDeps(ops: Map[RuntimeDIUniverse.DIKey, ExecutableOp], deps: mutable.HashSet[RuntimeDIUniverse.DIKey]): Unit = {
    val newDeps = deps.map(ops.apply).flatMap {
      case w: WiringOp =>
        w.wiring.associations.map(_.wireWith)
      case p: InitProxy =>
        p.dependencies
      case s: AddToSet =>
        Seq(s.element)
      case p: MakeProxy =>
        Seq.empty
      case i: ImportDependency =>
        Seq.empty
      case _ =>
        Seq.empty
    }

    if (newDeps.isEmpty) {
      ()
    } else {
      deps ++= newDeps
      allDeps(ops, deps)
    }
  }
}
