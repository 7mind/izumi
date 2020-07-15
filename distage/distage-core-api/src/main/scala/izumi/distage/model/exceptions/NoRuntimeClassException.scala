package izumi.distage.model.exceptions

import izumi.distage.model.reflection.{DIKey, SafeType}

class NoRuntimeClassException(
  val operation: DIKey,
  val key: SafeType,
) extends DIException(
    s"Cannot build proxy for operation $operation: runtime class is not available for $key"
  ) {
  def this(operation: DIKey) = this(operation, operation.tpe)
}
