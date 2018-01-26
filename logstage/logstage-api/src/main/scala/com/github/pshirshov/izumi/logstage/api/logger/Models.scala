package com.github.pshirshov.izumi.logstage.api.logger

object Models {

  case class LoggingConfig(rules: Seq[LoggingRule] = Seq.empty, default: Set[Log.Level] = Set(Log.Level.Debug))

  case class LoggingRule(packageName: String = "", levels: Set[Log.Level])

}
