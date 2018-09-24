package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.config.ConfigProvider.ConfigImport
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{RuntimeConfigReader, RuntimeConfigReaderCodecs, RuntimeConfigReaderDefaultImpl}

case class ConfigInjectionOptions(
                                 enableScalars: Boolean = false
                                 , transformer: ConfigValueTransformer = ConfigValueTransformer.Null
                               )

object ConfigInjectionOptions {
  def make(
             transformer: PartialFunction[(ConfigImport, Any), Any]
           ): ConfigInjectionOptions = new ConfigInjectionOptions(transformer = new ConfigValueTransformer {
    override def transform: PartialFunction[(ConfigImport, Any), Any] = transformer
  })
}

class ConfigModule(config: AppConfig, configInjectorConfig: ConfigInjectionOptions = ConfigInjectionOptions()) extends BootstrapModuleDef {

  make[ConfigInjectionOptions].from(configInjectorConfig)

  make[AppConfig].from(config)

  many[PlanningHook]
    .add[ConfigReferenceExtractor]
    .add[ConfigProvider]

  many[RuntimeConfigReaderCodecs]
    .add(RuntimeConfigReaderCodecs.default)
  make[RuntimeConfigReader]
    .from(RuntimeConfigReaderDefaultImpl.apply _)
}
