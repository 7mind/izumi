package izumi.distage.model.exceptions

import izumi.distage.model.reflection.SafeType

class UnsafeCallArgsMismatched(message: String, val expected: Seq[SafeType], val actual: Seq[SafeType], val actualValues: Seq[Any]) extends DIException(message)
