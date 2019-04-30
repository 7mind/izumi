package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy.DIKeyConflictResolution
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class ConflictingDIKeyBindingsException(message: String, val conflicts: Map[RuntimeDIUniverse.DIKey, DIKeyConflictResolution.Failed]) extends DIException(message, null)
