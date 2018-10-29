package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.reflection
import com.github.pshirshov.izumi.distage.model.reflection.universe

trait MirrorProvider {
  def runtimeClass(tpe: RuntimeDIUniverse.SafeType): Class[_]
  def runtimeClass(tpe: RuntimeDIUniverse.TypeNative): Class[_]

}

object MirrorProvider {
  object Impl extends MirrorProvider {
    private val mirror = RuntimeDIUniverse.mirror

    override def runtimeClass(tpe: universe.RuntimeDIUniverse.SafeType): Class[_] = {
      runtimeClass(tpe.tpe)
    }

    override def runtimeClass(tpe: reflection.universe.RuntimeDIUniverse.TypeNative): Class[_] = {
      mirror.runtimeClass(tpe)
    }
  }

}
