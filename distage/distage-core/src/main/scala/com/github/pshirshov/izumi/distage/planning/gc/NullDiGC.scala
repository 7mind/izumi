package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

object NullDiGC extends DIGarbageCollector {
  override def gc(plan: FinalPlan, isRoot: GCRootPredicate): FinalPlan = {
    Quirks.discard(isRoot)
    plan
  }
}
