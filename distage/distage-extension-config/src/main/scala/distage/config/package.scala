package distage

import izumi.distage.config.{codec, extractor, model}

package object config extends DistageConfig {

  override type AppConfig = model.AppConfig
  override val AppConfig: model.AppConfig.type = model.AppConfig

  override type ConfigModuleDef = izumi.distage.config.ConfigModuleDef

  override type AppConfigModule = izumi.distage.config.AppConfigModule
  override val AppConfigModule: izumi.distage.config.AppConfigModule.type = izumi.distage.config.AppConfigModule

  override type ConfigReader[T] = codec.ConfigReader[T]
  override val ConfigReader: codec.ConfigReader.type = codec.ConfigReader

  override type ConfigPathExtractorModule = extractor.ConfigPathExtractorModule

}
