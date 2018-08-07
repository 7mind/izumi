package com.github.pshirshov.izumi.logstage.api.modules

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.logstage.api.config.LogConfigService
import com.github.pshirshov.izumi.logstage.api.routing.LogConfigServiceImpl

class LogConfigModule() extends ModuleDef {
  make[LogConfigService].from[LogConfigServiceImpl]
}
