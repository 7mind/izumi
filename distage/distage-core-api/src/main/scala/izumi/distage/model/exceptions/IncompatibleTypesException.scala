package izumi.distage.model.exceptions

import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class IncompatibleTypesException(
                                  message: String
                                  , val expected: RuntimeDIUniverse.SafeType
                                  , val got: RuntimeDIUniverse.SafeType
                                ) extends DIException(message)


