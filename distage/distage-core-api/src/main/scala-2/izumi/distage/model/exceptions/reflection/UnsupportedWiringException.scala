package izumi.distage.model.exceptions.reflection

import izumi.distage.model.exceptions.DIException
import izumi.distage.reflection.macros.universe.basicuniverse.MacroSafeType

class UnsupportedWiringException(message: String, val tpe: MacroSafeType) extends DIException(message)
