package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan


trait DIGarbageCollector {
  def gc(plan: FinalPlan, isRoot: GCRootPredicate): FinalPlan
}

