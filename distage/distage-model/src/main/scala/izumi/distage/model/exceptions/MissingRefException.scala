package izumi.distage.model.exceptions

import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class MissingRefException(message: String, val missing: Set[RuntimeDIUniverse.DIKey], val topology: Option[PlanTopology]) extends DIException(message, null)
