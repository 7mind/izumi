package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

class AssignableFromEarlyAutoSetHook[T: Tag] extends EarlyAutoSetHook {
  protected val setElementType: SafeType = SafeType.get[T]

  override def elementOf(b: Binding): Option[universe.RuntimeDIUniverse.DIKey] = {
    val bindingType = b.key.tpe

    if (bindingType weak_<:< setElementType) {
      Some(DIKey.get[Set[T]])
    } else {
      None
    }
  }
}
