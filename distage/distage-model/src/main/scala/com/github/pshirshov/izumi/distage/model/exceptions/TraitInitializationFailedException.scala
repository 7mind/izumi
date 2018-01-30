package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

class TraitInitializationFailedException(message: String, val tpe: RuntimeUniverse.TypeFull, cause: Throwable) extends DIException(message, cause)
