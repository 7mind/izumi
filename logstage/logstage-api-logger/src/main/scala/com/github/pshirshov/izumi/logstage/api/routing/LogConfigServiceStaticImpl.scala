package com.github.pshirshov.izumi.logstage.api.routing

import com.github.pshirshov.izumi.logstage.api.config.{LogConfigService, LoggerConfig}

// TODO :remove it!
class LogConfigServiceStaticImpl(
                                  override val loggerConfig: LoggerConfig
                                ) extends LogConfigService {
}
