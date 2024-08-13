package izumi.logstage.api.config

final case class LoggerConfig(root: LoggerPathRule, entries: Map[LoggerPath, LoggerPathRule])
