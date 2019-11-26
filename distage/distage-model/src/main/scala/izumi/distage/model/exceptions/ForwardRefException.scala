package izumi.distage.model.exceptions

import izumi.distage.model.plan.PlanTopology

class ForwardRefException(message: String, val topology: PlanTopology) extends DIException(message, null)
