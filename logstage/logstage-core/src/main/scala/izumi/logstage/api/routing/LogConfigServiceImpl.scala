package izumi.logstage.api.routing

import izumi.fundamentals.collections.WildcardPrefixTree
import izumi.fundamentals.collections.nonempty.NEList
import izumi.logstage.api.Log
import izumi.logstage.api.config.*

class LogConfigServiceImpl(loggerConfig: LoggerConfig) extends LogConfigService {
  private val configTree = {

    val out = loggerConfig.entries.flatMap {
      rule =>
        val entries = rule.path.path.map {
          case LoggerPathElement.Pkg(name) => Some(name.theString)
          case LoggerPathElement.Wildcard => None
        }

        val paths = if (rule.path.lines.nonEmpty) {
          rule.path.lines.map {
            last =>
              entries ++ NEList(Some(s"line.$last"))
          }
        } else {
          List(entries ++ NEList(None))
        }

        paths.map {
          path =>
            (path.toList, rule.config)
        }

    }

    WildcardPrefixTree.build[String, LoggerPathConfig](out)
  }

  override def threshold(e: Log.LoggerId): Log.Level = {
    val query = e.id.split('.').toList
    val result = queryConfig(query)
    result.threshold
  }

  override def config(e: Log.Entry): LogEntryConfig = {
    val query = e.context.static.id.id.split('.').toSeq ++ Seq(s"line.${e.context.static.position.line}")

    val cfg = queryConfig(query)

    if (e.context.dynamic.level >= cfg.threshold) {
      LogEntryConfig(cfg.sinks)
    } else {
      LogEntryConfig(List.empty)
    }
  }

  private def queryConfig(query: Seq[String]) = {
    configTree.findSubtrees(query).flatMap(_.values).headOption match {
      case Some(value) =>
        value
      case None =>
        loggerConfig.root.config
    }
  }

  override def toString: String = {
    import izumi.fundamentals.platform.strings.WildcardPrefixTreeTools.*
    s"${super.toString}\ndefault: ${loggerConfig.root.config}\n${configTree.print}"
  }
}
