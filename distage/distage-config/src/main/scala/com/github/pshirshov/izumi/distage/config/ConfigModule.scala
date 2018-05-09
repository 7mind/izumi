package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook

class ConfigModule(config: AppConfig, reader: ConfigInstanceReader) extends ModuleDef {
  make[AppConfig].from(config)
  make[ConfigInstanceReader].from(reader)
  many[PlanningHook]
    .add[ConfigReferenceExtractor]

  many[PlanningHook]
    .add[ConfigProvider]
}
