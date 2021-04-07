package izumi.distage.model.planning

import izumi.distage.model.plan._

trait SanityChecker {
  def assertFinalPlanSane(plan: OrderedPlan): Unit
}
