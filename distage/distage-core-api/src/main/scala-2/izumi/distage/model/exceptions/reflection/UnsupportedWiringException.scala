package izumi.distage.model.exceptions.reflection

import izumi.distage.model.exceptions.DIException
import izumi.distage.reflection.macros.universe.impl.DIUniverse

class UnsupportedWiringException(message: String, val tpe: DIUniverse#MacroSafeType) extends DIException(message)
