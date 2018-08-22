package com.github.pshirshov.izumi.logstage.config.modules

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.logstage.api.config.{LogConfigService, LoggerConfig}
import com.github.pshirshov.izumi.logstage.api.routing.LogConfigServiceImpl

class LogConfigModule extends ModuleDef {
  make[LogConfigService].from {
    config: LoggerConfig@ConfPath("logstage") =>
      new LogConfigServiceImpl(config)
  }
}
