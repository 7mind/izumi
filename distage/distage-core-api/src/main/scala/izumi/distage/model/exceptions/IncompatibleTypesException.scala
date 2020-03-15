package izumi.distage.model.exceptions

import izumi.distage.model.reflection.SafeType

class IncompatibleTypesException(
                                  message: String,
                                  val expected: SafeType,
                                  val got: SafeType,
                                ) extends DIException(message)


