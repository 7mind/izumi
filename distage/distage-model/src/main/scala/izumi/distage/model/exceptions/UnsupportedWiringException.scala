package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.DIUniverse

class UnsupportedWiringException(message: String, val tpe: DIUniverse#SafeType) extends DIException(message, null)
