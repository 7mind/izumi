package com.github.pshirshov.izumi.logstage.api.config

import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink

final case class LoggerConfig(threshold: Log.Level, sinks: Seq[LogSink])
