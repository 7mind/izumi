package izumi.distage.model.exceptions.planning

import izumi.distage.model.definition.conflicts.ConflictResolutionError
import izumi.distage.model.exceptions.DIException
import izumi.distage.model.plan.ExecutableOp.InstantiationOp
import izumi.distage.model.reflection.DIKey

@deprecated("Needs to be removed", "20/10/2021")
class ConflictResolutionException(message: String, val conflicts: List[ConflictResolutionError[DIKey, InstantiationOp]]) extends DIException(message)
