package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.reflection
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.fundamentals.reflection.RefinedTypeException

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
      import RuntimeDIUniverse.u._
      tpe match {
        case RefinedType(types, scope) =>
          throw new RefinedTypeException(s"Failed to reflect class: Reflection requires runtime codegeneration for refined type $tpe with parents $types and scope $scope")

        case o =>
          mirror.runtimeClass(o)
      }
    }
  }

}
