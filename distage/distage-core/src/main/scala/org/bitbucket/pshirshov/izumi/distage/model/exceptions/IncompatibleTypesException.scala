package org.bitbucket.pshirshov.izumi.distage.model.exceptions

import org.bitbucket.pshirshov.izumi.distage.model.EqualitySafeType

class IncompatibleTypesException(message: String, expected: EqualitySafeType, got: EqualitySafeType) extends DIException(message, null)
