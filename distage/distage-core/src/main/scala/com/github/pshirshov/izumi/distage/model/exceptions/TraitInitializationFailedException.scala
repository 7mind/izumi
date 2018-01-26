package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.TypeFull

class TraitInitializationFailedException(message: String, val tpe: TypeFull, cause: Throwable) extends DIException(message, cause)
