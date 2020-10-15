package izumi.distage.model.exceptions

import izumi.distage.model.definition.conflicts.ConflictResolutionError
import izumi.distage.model.plan.ExecutableOp.InstantiationOp
import izumi.distage.model.reflection.DIKey

class ConflictResolutionException(message: String, val conflicts: List[ConflictResolutionError[DIKey, InstantiationOp]]) extends DIException(message)
