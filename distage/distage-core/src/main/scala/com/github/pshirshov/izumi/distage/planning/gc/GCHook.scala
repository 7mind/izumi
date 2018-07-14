package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.plan.{FinalPlan, ReplanningContext}
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate, PlanningHook}

class GCHook(gc: DIGarbageCollector, isRoot: GCRootPredicate) extends PlanningHook {
  override def hookResolved(context: ReplanningContext, plan: FinalPlan): FinalPlan = {
    firstOnly(context, plan) {
      plan =>
        gc.gc(plan, isRoot)
    }
  }
}
