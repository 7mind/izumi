package izumi.logstage.api.routing

import izumi.fundamentals.collections.WildcardPrefixTree
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.language.CodePosition
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
          List(entries, entries :+ None)
        }

        paths.map {
          path =>
            (path.toList, rule.config)
        }
    } ++ List((List.empty, loggerConfig.root.config))

    val result = WildcardPrefixTree.build[String, LoggerPathConfig](out)
    assert(result.values.nonEmpty)
    result
  }

  override def acceptable(id: Log.LoggerId, logLevel: Log.Level): Boolean = {
    val query = id.id.split('.')
    val cfg = queryConfig(query)
    logLevel >= cfg.threshold
  }

  override def acceptable(position: CodePosition, logLevel: Log.Level): Boolean = {
    val query = position.applicationPointId.split('.') :+ lineSegment(position.position.line)
    val cfg = queryConfig(query)
    logLevel >= cfg.threshold
  }

  override def config(e: Log.Entry): LogEntryConfig = {
    val query = e.context.static.pos.applicationPointId.split('.') :+ lineSegment(e.context.static.pos.position.line)

    val cfg = queryConfig(query)

    if (e.context.dynamic.level >= cfg.threshold) {
      LogEntryConfig(cfg.sinks)
    } else {
      LogEntryConfig(List.empty)
    }
  }

  @inline private def queryConfig(query: Array[String]): LoggerPathConfig = {
    val result = configTree.findBestMatch(query.toSeq).found.values

    if (result.length == 1) {
      result.head
    } else if (result.length > 1) {
      result.minBy(_.threshold)
    } else {
      // this should NOT happen, the root of the tree must be always configured
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
