package izumi.distage.model.exceptions

import izumi.distage.model.plan.ExecutableOp.InstantiationOp
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.ConflictResolutionError

class ConflictResolutionException(message: String, val conflicts: List[ConflictResolutionError[DIKey, InstantiationOp]]) extends DIException(message)
