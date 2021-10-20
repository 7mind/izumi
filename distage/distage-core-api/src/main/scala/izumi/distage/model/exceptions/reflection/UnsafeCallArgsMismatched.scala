package izumi.distage.model.exceptions.reflection

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.SafeType

class UnsafeCallArgsMismatched(message: String, val expected: Seq[SafeType], val actual: Seq[SafeType], val actualValues: Seq[Any]) extends DIException(message)
