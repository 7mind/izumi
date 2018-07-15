package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate, PlanningHook}

class GCHook(gc: DIGarbageCollector, isRoot: GCRootPredicate) extends PlanningHook {
  override def phase10PostFinalization(plan: FinalPlan): FinalPlan = {
    gc.gc(plan, isRoot)
  }
}
