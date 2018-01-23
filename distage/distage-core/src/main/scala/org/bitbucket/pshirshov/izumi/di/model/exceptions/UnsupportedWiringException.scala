package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.TypeFull

class UnsupportedWiringException(message: String, val tpe: TypeFull) extends DIException(message, null)
