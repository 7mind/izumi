package com.github.pshirshov.izumi.logstage.model.config

import com.github.pshirshov.izumi.logstage.model.logger.LogSink

case class LogEntryConfig(sinks: Seq[LogSink])


