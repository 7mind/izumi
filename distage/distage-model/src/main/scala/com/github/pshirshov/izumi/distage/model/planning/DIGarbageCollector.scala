package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.SemiPlan

trait DIGarbageCollector {
  def gc(plan: SemiPlan, isRoot: GCRootPredicate): SemiPlan
}

