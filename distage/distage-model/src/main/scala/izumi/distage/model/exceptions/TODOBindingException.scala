package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.fundamentals.reflection.CodePositionMaterializer

class TODOBindingException(message: String, val target: RuntimeDIUniverse.DIKey, val sourcePosition: CodePositionMaterializer) extends DIException(message, null)
