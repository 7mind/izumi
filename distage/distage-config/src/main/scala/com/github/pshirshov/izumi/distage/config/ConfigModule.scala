package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.definition.ModuleBuilder
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook

class ConfigModule(config: AppConfig, reader: ConfigInstanceReader) extends ModuleBuilder {
  bind[AppConfig].as(config)
  bind[ConfigInstanceReader].as(reader)
  set[PlanningHook]
    .element[ConfigReferenceExtractor]

  set[PlanningHook]
    .element[ConfigProvider]
}
