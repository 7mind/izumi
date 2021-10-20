package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.SafeType

@deprecated("Needs to be removed", "20/10/2021")
class IncompatibleTypesException(
  message: String,
  val expected: SafeType,
  val got: SafeType,
) extends DIException(message)
