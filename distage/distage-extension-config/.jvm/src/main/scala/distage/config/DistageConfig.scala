package distage.config

import izumi.distage.config.{codec, model}

trait DistageConfig {

  type AppConfig = model.AppConfig
  val AppConfig: model.AppConfig.type = model.AppConfig

  type ConfigModuleDef = izumi.distage.config.ConfigModuleDef

  type AppConfigModule = izumi.distage.config.AppConfigModule
  val AppConfigModule: izumi.distage.config.AppConfigModule.type = izumi.distage.config.AppConfigModule

  type DIConfigReader[T] = codec.DIConfigReader[T]
  val DIConfigReader: codec.DIConfigReader.type = codec.DIConfigReader

}
