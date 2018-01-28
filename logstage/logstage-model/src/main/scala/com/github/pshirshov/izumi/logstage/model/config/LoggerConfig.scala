package com.github.pshirshov.izumi.logstage.model.config

import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.logger.LogSink

case class LoggerConfig(threshold: Log.Level, sinks: Seq[LogSink])
