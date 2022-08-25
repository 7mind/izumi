package izumi.distage.model.reflection

trait MirrorProvider {
  def runtimeClass(tpe: SafeType): Option[Class[?]]
  def runtimeClassCompatible(tpe: SafeType, value: Any): Boolean
  def canBeProxied(tpe: SafeType): Boolean
}

object MirrorProvider {
  object Impl extends MirrorProvider {
    override def runtimeClass(tpe: SafeType): Option[Class[?]] = ???
    override def canBeProxied(tpe: SafeType): Boolean = ???
    override def runtimeClassCompatible(tpe: SafeType, value: Any): Boolean = ???
  }
}
