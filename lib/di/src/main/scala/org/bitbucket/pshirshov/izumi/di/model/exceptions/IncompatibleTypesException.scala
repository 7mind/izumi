package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.model.EqualitySafeType

class IncompatibleTypesException(message: String, expected: EqualitySafeType, got: EqualitySafeType) extends DIException(message, null)
