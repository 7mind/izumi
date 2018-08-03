package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

class AssignableFromEarlyAutoSetHook[T: Tag] extends EarlyAutoSetHook {
  protected val setClass: Class[_] = mirror.runtimeClass(Tag[T].tag.tpe)

  override def elementOf(b: Binding): Option[universe.RuntimeDIUniverse.DIKey] = {
    val bindingClass = mirror.runtimeClass(b.key.tpe.tpe)

    if (setClass.isAssignableFrom(bindingClass)) {
      Some(DIKey.get[Set[T]])
    } else {
      None
    }
  }
}
