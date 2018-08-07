package com.github.pshirshov.izumi.logstage.api.routing

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.logstage.api.config.{LogConfigService, LoggerConfig}

class LogConfigServiceImpl
(
  @ConfPath("logstage") override val loggerConfig: LoggerConfig
) extends LogConfigService {
}
