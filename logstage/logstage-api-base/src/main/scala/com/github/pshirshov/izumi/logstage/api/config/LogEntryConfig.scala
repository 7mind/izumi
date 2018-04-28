package com.github.pshirshov.izumi.logstage.api.config

import com.github.pshirshov.izumi.logstage.api.logger.LogSink

case class LogEntryConfig(sinks: Seq[LogSink])


