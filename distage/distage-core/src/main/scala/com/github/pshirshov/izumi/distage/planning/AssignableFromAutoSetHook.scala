package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class AssignableFromAutoSetHook[T : RuntimeDIUniverse.Tag]() extends AutoSetHook {
  override def elementOf(b: Binding): Option[universe.RuntimeDIUniverse.DIKey] = {
    val mirror = RuntimeDIUniverse.mirror

    val keyClass = mirror.runtimeClass(b.key.tpe.tpe)
    val tClass = mirror.runtimeClass(RuntimeDIUniverse.u.typeTag[T].tpe)

    if (keyClass.isAssignableFrom(tClass)) {
      Some(RuntimeDIUniverse.DIKey.get[Set[T]])
    } else {
      None
    }
  }
}
