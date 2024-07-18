package izumi.logstage.api.config

import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink

final case class LoggerPath(id: String)

final case class LoggerPathConfig(threshold: Log.Level, lines: Set[Int])

final case class LoggerPathRule(config: LoggerPathConfig, sinks: Seq[LogSink])

final case class LoggerPathForLines(id: String, lines: Set[Int])

object LoggerPathForLines {
  def parse(cfg: String): LoggerPathForLines = {
    def toInt(s: String): Option[Int] = {
      try {
        Some(s.toInt)
      } catch {
        case _: NumberFormatException => None
      }
    }

    val li = cfg.lastIndexOf(':')

    if (li > 0 && li + 1 < cfg.length) {
      val spec = cfg.substring(li + 1, cfg.length)
      val elems = spec.split(',').map(toInt).collect {
        case Some(i) =>
          i
      }
      if (elems.nonEmpty) {
        LoggerPathForLines(cfg.substring(0, li), elems.toSet)
      } else {
        LoggerPathForLines(cfg, Set.empty)
      }

    } else {
      LoggerPathForLines(cfg, Set.empty)
    }
  }
}
