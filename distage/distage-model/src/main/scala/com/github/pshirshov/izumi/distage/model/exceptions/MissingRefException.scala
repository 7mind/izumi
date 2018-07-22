package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.plan.PlanTopology
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class MissingRefException(message: String, val missing: Set[RuntimeDIUniverse.DIKey], val topology: Option[PlanTopology]) extends DIException(message, null)
