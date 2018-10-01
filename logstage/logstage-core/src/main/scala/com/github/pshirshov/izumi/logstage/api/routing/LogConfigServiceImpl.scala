package com.github.pshirshov.izumi.logstage.api.routing

import com.github.pshirshov.izumi.logstage.api.config.{LogConfigService, LoggerConfig}

class LogConfigServiceImpl(override val loggerConfig: LoggerConfig) extends LogConfigService
