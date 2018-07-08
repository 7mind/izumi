package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.reflection.MacroUtil.EnclosingPosition

class TODOBindingException(message: String, val target: RuntimeDIUniverse.DIKey, val sourcePosition: EnclosingPosition) extends DIException(message, null)
