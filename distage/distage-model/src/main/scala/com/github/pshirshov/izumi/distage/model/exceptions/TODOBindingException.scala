package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer

class TODOBindingException(message: String, val target: RuntimeDIUniverse.DIKey, val sourcePosition: CodePositionMaterializer) extends DIException(message, null)
