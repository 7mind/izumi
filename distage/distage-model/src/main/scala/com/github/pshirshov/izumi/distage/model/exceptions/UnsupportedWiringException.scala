package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.DIUniverse

class UnsupportedWiringException(message: String, val tpe: DIUniverse#SafeType) extends DIException(message, null)
