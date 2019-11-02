package izumi.distage.model.reflection.universe

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{SafeType, TypeNative, u}

trait MirrorProvider {
  def runtimeClass(tpe: SafeType): Option[Class[_]]
  def runtimeClass(tpe: TypeNative): Option[Class[_]]
  def mirror: u.Mirror
}

object MirrorProvider {
  object Impl extends MirrorProvider {
    override val mirror: u.Mirror = scala.reflect.runtime.currentMirror

    override def runtimeClass(tpe: SafeType): Option[Class[_]] = {
      tpe.use(runtimeClass)
    }

    override def runtimeClass(tpe: TypeNative): Option[Class[_]] = {
      try {
        val rtc = mirror.runtimeClass(tpe.erasure)
        assert(rtc != null)
        Some(rtc)
      } catch {
        case _: ClassNotFoundException =>
          None
      }
    }
  }

}
