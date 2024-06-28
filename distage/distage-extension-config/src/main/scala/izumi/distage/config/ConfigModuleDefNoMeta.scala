package izumi.distage.config

import izumi.distage.config.codec.{ConfigMetaType, DIConfigMeta}

trait ConfigModuleDefNoMeta extends ConfigModuleDef with DisableConfigMeta

trait DisableConfigMeta {
  implicit final def disableMeta[T]: DIConfigMeta[T] = new DIConfigMeta[T] {
    override def tpe: ConfigMetaType = ConfigMetaType.TUnknown()
  }
}
