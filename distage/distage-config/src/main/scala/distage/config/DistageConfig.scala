package distage.config

import izumi.distage.config.{codec, model}

trait DistageConfig {

  type AppConfig = model.AppConfig
  val AppConfig: model.AppConfig.type = model.AppConfig

  type ConfigModuleDef = izumi.distage.config.ConfigModuleDef
  type AppConfigModule = izumi.distage.config.AppConfigModule

  type ConfigReader[T] = codec.ConfigReader[T]
  val ConfigReader: codec.ConfigReader.type = codec.ConfigReader

  type ConfigPathExtractorModule = izumi.distage.config.ConfigPathExtractorModule

}
