package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.reflection.{DIKey, SafeType}

@deprecated("Needs to be removed", "20/10/2021")
class NoRuntimeClassException(
  val operation: DIKey,
  val key: SafeType,
) extends DIException(
    s"Cannot build proxy for operation $operation: runtime class is not available for $key"
  ) {
  def this(operation: DIKey) = this(operation, operation.tpe)
}
