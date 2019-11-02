package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class TraitInitializationFailedException(message: String, val tpe: RuntimeDIUniverse.SafeType, cause: Throwable) extends DIException(message, cause)
