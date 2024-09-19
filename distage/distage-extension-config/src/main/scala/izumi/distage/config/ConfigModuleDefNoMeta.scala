package izumi.distage.config

import izumi.distage.config.codec.{ConfigMetaType, DIConfigMeta}

/**
  * Disable generation of [[DIConfigMeta]]'s used to generate JSON Schema for config in [[izumi.distage.roles.bundled.ConfigWriter]]
  *
  * Use this if DIConfigMeta generation fails for your types and/or you don't use config JSON Schema / ConfigWriter.
  *
  * @see [[izumi.distage.roles.bundled.JsonSchemaGenerator]]
  */
trait ConfigModuleDefNoMeta extends ConfigModuleDef with DisableConfigMeta

trait DisableConfigMeta {
  implicit final def disableMeta[T]: DIConfigMeta[T] = new DIConfigMeta[T] {
    override def tpe: ConfigMetaType = ConfigMetaType.TUnknown()
  }
}
