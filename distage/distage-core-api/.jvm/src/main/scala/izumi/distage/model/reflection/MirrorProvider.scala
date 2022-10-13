package izumi.distage.model.reflection

import izumi.fundamentals.reflection.TypeUtil

import java.lang.reflect.Modifier

trait MirrorProvider {
  def runtimeClass(tpe: SafeType): Option[Class[?]]
  def runtimeClassCompatible(tpe: SafeType, value: Any): Boolean
  def canBeProxied(tpe: SafeType): Boolean
}

object MirrorProvider {
  object Impl extends MirrorProvider {
    override def runtimeClass(tpe: SafeType): Option[Class[?]] = {
      if (tpe.hasPreciseClass) Some(tpe.cls) else None
    }
    override def canBeProxied(tpe: SafeType): Boolean = {
      runtimeClass(tpe).exists(c => !Modifier.isFinal(c.getModifiers))
    }
    override def runtimeClassCompatible(tpe: SafeType, value: Any): Boolean = {
      runtimeClass(tpe).forall(TypeUtil.isAssignableFrom(_, value))
    }
  }
}
