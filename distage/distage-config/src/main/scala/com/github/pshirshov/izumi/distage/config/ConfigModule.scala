package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook

class ConfigModule(config: AppConfig) extends ModuleDef {
  make[AppConfig].from(config)
  make[RuntimeConfigReader].from[RuntimeConfigReaderDefaultImpl]
  many[PlanningHook]
    .add[ConfigReferenceExtractor]

  many[PlanningHook]
    .add[ConfigProvider]
}
