package org.bitbucket.pshirshov.izumi.config

import org.bitbucket.pshirshov.izumi.logger.Log
import org.bitbucket.pshirshov.izumi.logger.Models.{LoggingConfig, LoggingRule}

trait ConfigLoader {

  lazy val loggingConfig: LoggingConfig = LoggingConfig(rules = Seq(LoggingRule("", Set(Log.Level.Debug))))
}