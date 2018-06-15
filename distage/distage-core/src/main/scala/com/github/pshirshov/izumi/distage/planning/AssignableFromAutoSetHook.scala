package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

class AssignableFromAutoSetHook[T: Tag] extends AutoSetHook {
  override def elementOf(b: Binding): Option[universe.RuntimeDIUniverse.DIKey] = {
    val keyClass = mirror.runtimeClass(b.key.tpe.tpe)
    val tClass = mirror.runtimeClass(Tag[T].tag.tpe)

    if (keyClass.isAssignableFrom(tClass)) {
      Some(DIKey.get[Set[T]])
    } else {
      None
    }
  }
}
