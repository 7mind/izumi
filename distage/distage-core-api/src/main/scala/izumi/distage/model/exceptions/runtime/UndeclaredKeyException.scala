package izumi.distage.model.exceptions.runtime

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.DIKey

class UndeclaredKeyException(message: String, val undeclared: DIKey) extends DIException(message)
