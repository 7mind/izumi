package izumi.distage.model.exceptions.planning

import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.exceptions.DIException

@deprecated("Needs to be removed", "20/10/2021")
class InjectorFailed(message: String, val errors: List[DIError]) extends DIException(message)
