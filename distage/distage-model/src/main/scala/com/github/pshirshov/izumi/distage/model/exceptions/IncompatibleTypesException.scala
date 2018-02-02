package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.fundamentals.reflection.RuntimeUniverse

class IncompatibleTypesException
(message: String, val expected: RuntimeUniverse.SafeType, val got: RuntimeUniverse.SafeType) extends DIException(message, null)
