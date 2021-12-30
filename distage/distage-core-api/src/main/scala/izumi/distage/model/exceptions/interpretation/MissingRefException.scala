package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.reflection.DIKey

@deprecated("Needs to be removed", "20/10/2021")
class MissingRefException(message: String, val missing: Set[DIKey], val topology: Option[PlanTopology]) extends DIException(message)
