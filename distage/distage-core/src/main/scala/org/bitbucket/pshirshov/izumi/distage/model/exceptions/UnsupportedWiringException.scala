package org.bitbucket.pshirshov.izumi.distage.model.exceptions

import org.bitbucket.pshirshov.izumi.distage.TypeFull

class UnsupportedWiringException(message: String, val tpe: TypeFull) extends DIException(message, null)
