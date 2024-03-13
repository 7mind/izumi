package izumi.distage.model.exceptions.runtime

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.SafeType

class UnsafeProviderCallArgsMismatched(message: String, val expected: Seq[SafeType], val actual: Seq[SafeType], val actualValues: Seq[Any]) extends DIException(message)
