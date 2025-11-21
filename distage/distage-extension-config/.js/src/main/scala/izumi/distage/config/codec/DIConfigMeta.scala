package izumi.distage.config.codec

trait DIConfigMeta[A] {
  def tpe: ConfigMetaType
}

object DIConfigMeta {
  def apply[T](configMetaType: ConfigMetaType): DIConfigMeta[T] = new DIConfigMeta[T] {
    override def tpe: ConfigMetaType = configMetaType
  }

  implicit def derived[A]: DIConfigMeta[A] = empty

  def empty[A]: DIConfigMeta[A] = DIConfigMeta(ConfigMetaType.TUnknown())
}
