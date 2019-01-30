package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.plan.SemiPlan
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

object NoopDIGC extends DIGarbageCollector {
  override def gc(plan: SemiPlan, isRoot: GCRootPredicate): SemiPlan = {
    isRoot.discard()
    plan
  }
}
