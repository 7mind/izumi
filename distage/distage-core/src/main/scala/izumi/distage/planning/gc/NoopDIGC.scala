package izumi.distage.planning.gc

import izumi.distage.model.plan.SemiPlan
import izumi.distage.model.planning.DIGarbageCollector

object NoopDIGC extends DIGarbageCollector {
  override def gc(plan: SemiPlan): SemiPlan = plan
}
