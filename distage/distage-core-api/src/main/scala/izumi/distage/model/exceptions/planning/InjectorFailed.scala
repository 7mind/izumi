package izumi.distage.model.exceptions.planning

import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.exceptions.DIException

class InjectorFailed(message: String, val errors: List[DIError]) extends DIException(message)
