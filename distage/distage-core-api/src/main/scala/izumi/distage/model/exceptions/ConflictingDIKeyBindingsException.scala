package izumi.distage.model.exceptions

import izumi.distage.model.planning.PlanMergingPolicy.DIKeyConflictResolution
import izumi.distage.model.reflection.DIKey

class ConflictingDIKeyBindingsException(message: String, val conflicts: Map[DIKey, DIKeyConflictResolution.Failed]) extends DIException(message)
