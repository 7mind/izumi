package izumi.logstage.api.config

import izumi.fundamentals.collections.nonempty.{NEList, NEString}
import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink

//final case class LoggerPathRule(config: LoggerPathConfig, sinks: Seq[LogSink])
//
//final case class LoggerPath(id: String, line: Option[Int])
//
//final case class LoggerPathConfig(threshold: Log.Level)

sealed trait LoggerPathElement

object LoggerPathElement {
  final case class Pkg(name: NEString) extends LoggerPathElement
  final case object Wildcard extends LoggerPathElement
}

final case class LoggerPath(path: NEList[LoggerPathElement], lines: Set[Int])

final case class LoggerPathConfig(threshold: Log.Level, sinks: Seq[LogSink])

final case class LoggerRule(path: LoggerPath, config: LoggerPathConfig)

final case class LoggerConfig(entries: List[LoggerRule], root: LoggerRule)

object LoggerPathForLines {

  def parse(cfg: String): Option[LoggerPath] = {
    val li = cfg.lastIndexOf(':')

    val (path, lines) = if (li > 0 && li + 1 < cfg.length) {
      val spec = cfg.substring(li + 1, cfg.length)
      val elems = spec.split(',').map(toInt).collect {
        case Some(i) =>
          i
      }

      if (elems.nonEmpty) {
        (cfg.substring(0, li), elems.toSet)
      } else {
        (cfg, Set.empty[Int])
      }
    } else {
      (cfg, Set.empty[Int])
    }

    parsePath(path).map(path => LoggerPath(path, lines))
  }

  private def parsePath(s: String): Option[NEList[LoggerPathElement]] = {
    val els = s.split('.').filterNot(_.isEmpty).toList
    if (els.exists(_.isEmpty)) {
      None
    } else {
      NEList.from(els).map {
        elements =>
          elements.map {
            case "*" =>
              LoggerPathElement.Wildcard
            case o =>
              LoggerPathElement.Pkg(NEString.unsafeFrom(o))
          }
      }
    }
  }

  private def toInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case _: NumberFormatException => None
    }
  }

}
