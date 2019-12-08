package izumi.distage.model.exceptions

import izumi.distage.model.planning.PlanMergingPolicy.DIKeyConflictResolution
import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class ConflictingDIKeyBindingsException(message: String, val conflicts: Map[RuntimeDIUniverse.DIKey, DIKeyConflictResolution.Failed]) extends DIException(message)
