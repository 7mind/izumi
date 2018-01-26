package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.EqualitySafeType

class IncompatibleTypesException(message: String, val expected: EqualitySafeType, val got: EqualitySafeType) extends DIException(message, null)
