package izumi.distage.model.reflection

import java.lang.reflect.Modifier

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import izumi.fundamentals.reflection.TypeUtil

trait MirrorProvider {
  def runtimeClass(tpe: SafeType): Option[Class[_]]
  def runtimeClassCompatible(tpe: SafeType, value: Any): Boolean
  def canBeProxied(tpe: SafeType): Boolean
}

object MirrorProvider {
  object Impl extends MirrorProvider {
    override def runtimeClass(tpe: SafeType): Option[Class[_]] = {
      if (tpe.hasPreciseClass) Some(tpe.cls) else None
    }
    override def canBeProxied(tpe: SafeType): Boolean = {
      false
    }
    override def runtimeClassCompatible(tpe: SafeType, value: Any): Boolean = {
      runtimeClass(tpe).forall(TypeUtil.isAssignableFrom(_, value))
    }
  }
}
