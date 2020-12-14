package izumi.distage.model.exceptions

import izumi.distage.model.plan.OrderedPlan

class InvalidPlanException(message: String, val plan: Option[OrderedPlan]) extends DIException(message, null)
