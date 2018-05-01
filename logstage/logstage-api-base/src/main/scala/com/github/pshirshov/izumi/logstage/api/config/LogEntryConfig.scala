package com.github.pshirshov.izumi.logstage.api.config

import com.github.pshirshov.izumi.logstage.api.logger.LogSink

final case class LogEntryConfig(sinks: Seq[LogSink])


