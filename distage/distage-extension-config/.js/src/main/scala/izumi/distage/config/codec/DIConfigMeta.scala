package izumi.distage.config.codec

trait DIConfigMeta[A] {
  def tpe: ConfigMetaType
}

object DIConfigMeta {
  implicit def derived[A]: DIConfigMeta[A] = new DIConfigMeta[A] {
    override def tpe: ConfigMetaType = ConfigMetaType.TUnknown()
  }
}
