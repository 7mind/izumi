package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class TraitInitializationFailedException(message: String, val tpe: RuntimeDIUniverse.TypeFull, cause: Throwable) extends DIException(message, cause)
