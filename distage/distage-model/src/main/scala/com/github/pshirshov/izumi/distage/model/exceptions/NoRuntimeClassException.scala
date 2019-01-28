package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class NoRuntimeClassException(
                               val operation: RuntimeDIUniverse.DIKey
                               , val key: RuntimeDIUniverse.TypeNative
                             ) extends DIException(
  s"Cannot build proxy for operation $operation: runtime class is not available for $key"
  , null
) {
  def this(operation: RuntimeDIUniverse.DIKey) = this(operation, operation.tpe.tpe)
}
