package izumi.distage.model.exceptions

import izumi.distage.model.plan.OrderedPlan

class InvalidPlanException(message: String, val plan: Option[OrderedPlan], val omitClassName: Boolean, captureStackTrace: Boolean)
  extends DIException(message, null, captureStackTrace) {
  def this(message: String, plan: Option[OrderedPlan] = None) = this(message, plan, false, true)
  override def toString: String = if (omitClassName) getMessage else super.toString
}
