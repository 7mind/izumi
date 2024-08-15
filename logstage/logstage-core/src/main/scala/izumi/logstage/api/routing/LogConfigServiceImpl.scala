package izumi.logstage.api.routing

import izumi.fundamentals.collections.WildcardPrefixTree
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.logstage.api.Log
import izumi.logstage.api.config.*
import izumi.fundamentals.platform.strings.WildcardPrefixTreeTools.*

class LogConfigServiceImpl(loggerConfig: LoggerConfig) extends LogConfigService {
  private val configTree = {

    val out = loggerConfig.entries.flatMap {
      rule =>
        val entries = rule.path.path.toSeq.map {
          case LoggerPathElement.Pkg(name) => Some(name.theString)
          case LoggerPathElement.Wildcard => None
        }

        val paths = if (rule.path.lines.nonEmpty) {
          rule.path.lines.map {
            line =>
              entries :+ Some(lineSegment(line))
          }
        } else {
          List(entries :+ None)
        }

        paths.map {
          path =>
            (path.toList, rule.config)
        }
    }

    WildcardPrefixTree.build[String, LoggerPathConfig](out)
  }

  override def acceptable(id: Log.LoggerId, logLevel: Log.Level): Boolean = {
    val query = id.id.split('.')
    val cfg = queryConfig(query)
    logLevel >= cfg.threshold
  }

  override def acceptable(id: Log.LoggerId, line: Int, logLevel: Log.Level): Boolean = {
    val query = id.id.split('.') :+ lineSegment(line)
    val cfg = queryConfig(query)
    logLevel >= cfg.threshold
  }

  override def config(e: Log.Entry): LogEntryConfig = {
    val query = e.context.static.id.id.split('.') :+ lineSegment(e.context.static.position.line)

    val cfg = queryConfig(query)

    if (e.context.dynamic.level >= cfg.threshold) {
      LogEntryConfig(cfg.sinks)
    } else {
      LogEntryConfig(List.empty)
    }
  }

  @inline private def queryConfig(query: Array[String]): LoggerPathConfig = {
    val result = configTree.findSubtree(query.toSeq).map(_.values)
    result match {
      case Some(value) =>
        if (value.length == 1) {
          value.head
        } else if (value.isEmpty) {
          loggerConfig.root.config
        } else {
          value.minBy(_.threshold)
        }
      case None =>
        loggerConfig.root.config
    }
  }

  override def toString: String = {
    s"${super.toString}\ndefault: ${loggerConfig.root.config}\n${configTree.print}"
  }

  @inline private def lineSegment(line: Int): String = {
    s"line.$line"
  }

  override def validate(fallback: TrivialLogger): Unit = {
    if (configTree.maxValues > 1) {
      fallback.err(s"Logger config contains contradictive entries in $this")
    }
  }

}
