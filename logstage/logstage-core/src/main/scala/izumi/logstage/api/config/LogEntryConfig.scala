package izumi.logstage.api.config

import izumi.logstage.api.logger.LogSink

final case class LogEntryConfig(sinks: Seq[LogSink]) extends AnyVal
