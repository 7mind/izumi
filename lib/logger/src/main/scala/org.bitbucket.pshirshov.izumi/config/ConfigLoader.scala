package org.bitbucket.pshirshov.izumi.config

import com.ratoshniuk.izumi.Log
import com.ratoshniuk.izumi.Models.{LoggingConfig, LoggingRule}


trait ConfigLoader {

  lazy val loggingConfig: LoggingConfig = LoggingConfig(rules = Seq(LoggingRule("", Set(Log.Level.Debug))))
}