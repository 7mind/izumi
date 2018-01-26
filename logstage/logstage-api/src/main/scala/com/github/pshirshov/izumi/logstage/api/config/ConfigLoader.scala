package com.github.pshirshov.izumi.logstage.api.config

import com.github.pshirshov.izumi.logstage.api.logger.Log
import com.github.pshirshov.izumi.logstage.api.logger.Models.{LoggingConfig, LoggingRule}


trait ConfigLoader {

  lazy val loggingConfig: LoggingConfig = LoggingConfig(rules = Seq(LoggingRule("", Set(Log.Level.Debug))))
}