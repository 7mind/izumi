package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.TypeFull

class UnsupportedWiringException(message: String, val tpe: TypeFull) extends DIException(message, null)
