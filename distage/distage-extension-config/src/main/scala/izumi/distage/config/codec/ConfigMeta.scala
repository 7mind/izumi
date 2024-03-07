package izumi.distage.config.codec

sealed trait ConfigMeta
object ConfigMeta {
  final case class ConfigMetaCaseClass(fields: Seq[(String, ConfigMeta)]) extends ConfigMeta
  final case class ConfigMetaSealedTrait(branches: Set[(String, ConfigMeta)]) extends ConfigMeta
  final case class ConfigMetaEmpty() extends ConfigMeta
  final case class ConfigMetaUnknown() extends ConfigMeta
}
