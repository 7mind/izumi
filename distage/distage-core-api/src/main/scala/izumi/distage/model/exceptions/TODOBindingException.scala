package izumi.distage.model.exceptions

import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.language.CodePositionMaterializer

class TODOBindingException(message: String, val target: DIKey, val sourcePosition: CodePositionMaterializer) extends DIException(message)
