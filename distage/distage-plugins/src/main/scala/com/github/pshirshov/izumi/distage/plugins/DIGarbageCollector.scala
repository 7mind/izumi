package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.TrivialModuleDef
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, FinalPlan, FinalPlanImmutableImpl}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

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



    val refinedPlan = TrivialModuleDef(plan.definition.bindings.filter(b => toLeave.contains(b.target)))
    val steps = mutable.ArrayBuffer[ExecutableOp]()
    FinalPlanImmutableImpl(refinedPlan)(steps)
  }
}
