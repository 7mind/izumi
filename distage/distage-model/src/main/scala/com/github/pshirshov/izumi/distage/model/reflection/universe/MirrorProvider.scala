package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.reflection.RefinedTypeException

trait MirrorProvider {
  def runtimeClass(tpe: SafeType): Class[_]
  def runtimeClass(tpe: TypeNative): Class[_]
  def mirror: u.Mirror
}

object MirrorProvider {
  object Impl extends MirrorProvider {

    override val mirror: model.reflection.universe.RuntimeDIUniverse.u.Mirror = scala.reflect.runtime.currentMirror

    override def runtimeClass(tpe: SafeType): Class[_] = {
      runtimeClass(tpe.tpe)
    }

    override def runtimeClass(tpe: TypeNative): Class[_] = {
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
