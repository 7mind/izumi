package izumi.distage.model.exceptions

import izumi.distage.model.reflection.SafeType

class TraitInitializationFailedException(message: String, val tpe: SafeType, cause: Throwable) extends DIException(message, cause)
