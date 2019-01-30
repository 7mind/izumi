package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.plan.SemiPlan
import com.github.pshirshov.izumi.distage.model.planning.{DIGarbageCollector, GCRootPredicate, PlanningHook}

final class GCHook
(
  gc: DIGarbageCollector
  , isRoot: GCRootPredicate
) extends PlanningHook {

  override def phase10PostGC(plan: SemiPlan): SemiPlan = {
    gc.gc(plan, isRoot)
  }

}
