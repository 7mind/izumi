package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{RuntimeConfigReader, RuntimeConfigReaderCodecs, RuntimeConfigReaderDefaultImpl}

case class ConfigInjectorConfig(
                                 enableScalars: Boolean = false
                                 , transformer: ConfigValueTransformer = ConfigValueTransformer.Null
                               )

class ConfigModule(config: AppConfig, configInjectorConfig: ConfigInjectorConfig = ConfigInjectorConfig()) extends BootstrapModuleDef {

  make[ConfigInjectorConfig].from(configInjectorConfig)

  make[AppConfig].from(config)

  many[PlanningHook]
    .add[ConfigReferenceExtractor]
    .add[ConfigProvider]

  many[RuntimeConfigReaderCodecs]
    .add(RuntimeConfigReaderCodecs.default)
  make[RuntimeConfigReader]
    .from(RuntimeConfigReaderDefaultImpl.apply _)
}
