package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.config.codec.{RuntimeConfigReader, RuntimeConfigReaderDefaultImpl}
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook

case class ConfigInjectorConfig(enableScalars: Boolean = true)

class ConfigModule(config: AppConfig, configInjectorConfig: ConfigInjectorConfig = ConfigInjectorConfig()) extends ModuleDef {
  make[ConfigInjectorConfig].from(configInjectorConfig)
  make[AppConfig].from(config)
  make[RuntimeConfigReader].from[RuntimeConfigReaderDefaultImpl]
  many[PlanningHook]
    .add[ConfigReferenceExtractor]

  many[PlanningHook]
    .add[ConfigProvider]
}
