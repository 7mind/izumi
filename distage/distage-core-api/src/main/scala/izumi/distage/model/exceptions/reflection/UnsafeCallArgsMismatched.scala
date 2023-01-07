package izumi.distage.model.exceptions.reflection

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.SafeType

@deprecated("Needs to be removed", "20/10/2021")
class UnsafeCallArgsMismatched(message: String, val expected: Seq[SafeType], val actual: Seq[SafeType], val actualValues: Seq[Any]) extends DIException(message)
