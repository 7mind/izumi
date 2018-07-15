package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.plan.{FinalPlan, ReplanningContext}
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate, PlanningHook, ExtendedFinalPlan}

class GCHook(gc: DIGarbageCollector, isRoot: GCRootPredicate) extends PlanningHook {
  override def hookResolved(context: ReplanningContext, plan: FinalPlan): ExtendedFinalPlan = {
    firstOnly(context, plan) {
      plan =>
        ExtendedFinalPlan(gc.gc(plan, isRoot), replanningRequested = false)
    }
  }
}
