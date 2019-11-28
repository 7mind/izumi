package izumi.distage.model.reflection.universe

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType

trait MirrorProvider {
  def runtimeClass(tpe: SafeType): Option[Class[_]]
}

object MirrorProvider {
  object Impl extends MirrorProvider {
    override def runtimeClass(tpe: SafeType): Option[Class[_]] = {
      // FIXME: add ClassTag ???
//      tpe.use(runtimeClass)
      None
    }
  }
}
