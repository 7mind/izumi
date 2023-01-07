package izumi.distage.model.exceptions.runtime

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.reflection.DIKey

class MissingRefException(message: String, val missing: Set[DIKey], val topology: Option[PlanTopology]) extends DIException(message)
