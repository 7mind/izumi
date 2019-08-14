package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class IncompatibleTypesException(
                                  message: String
                                  , val expected: RuntimeDIUniverse.SafeType
                                  , val got: RuntimeDIUniverse.SafeType
                                ) extends DIException(message, null)


