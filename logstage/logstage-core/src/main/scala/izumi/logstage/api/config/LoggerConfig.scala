package izumi.logstage.api.config

final case class LoggerConfig(root: LoggerPathConfig, entries: Map[String, LoggerPathConfig])
