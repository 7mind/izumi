package izumi.distage.model.exceptions.reflection

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.universe.DIUniverse

class UnsupportedWiringException(message: String, val tpe: DIUniverse#SafeType) extends DIException(message)
