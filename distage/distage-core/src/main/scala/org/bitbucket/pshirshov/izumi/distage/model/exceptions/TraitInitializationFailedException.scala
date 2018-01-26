package org.bitbucket.pshirshov.izumi.distage.model.exceptions

import org.bitbucket.pshirshov.izumi.distage.TypeFull

class TraitInitializationFailedException(message: String, val tpe: TypeFull, cause: Throwable) extends DIException(message, cause)
