package com.github.pshirshov.izumi.distage.planning.gc

import com.github.pshirshov.izumi.distage.model.plan.SemiPlan
import com.github.pshirshov.izumi.distage.model.planning.DIGarbageCollector

object NoopDIGC extends DIGarbageCollector {
  override def gc(plan: SemiPlan): SemiPlan = plan
}
