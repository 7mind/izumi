package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.exceptions.DIException

@deprecated("Needs to be removed", "20/10/2021")
class InvalidPlanException(message: String) extends DIException(message, null)
