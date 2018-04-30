package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

trait DIGarbageCollector {
  def gc(plan: FinalPlan, roots: Set[RuntimeDIUniverse.DIKey]): FinalPlan
}

object NullDiGC extends DIGarbageCollector {
  override def gc(plan: FinalPlan, roots: Set[universe.RuntimeDIUniverse.DIKey]): FinalPlan = {
    Quirks.discard(roots)
    plan
  }
}


object TracingDIGC extends DIGarbageCollector {
  override def gc(plan: FinalPlan, roots: Set[universe.RuntimeDIUniverse.DIKey]): FinalPlan = ???
}
