package izumi.logstage.api.routing

import izumi.logstage.api.Log
import izumi.logstage.api.config.{LogConfigService, LogEntryConfig, LoggerConfig, LoggerPath, LoggerPathRule}
import izumi.logstage.api.routing.LogConfigServiceImpl.{ConfiguredLogTreeNode, LogTreeNode}

import scala.annotation.tailrec

class LogConfigServiceImpl(loggerConfig: LoggerConfig) extends LogConfigService {
  override def threshold(e: Log.LoggerId): Log.Level = {
    configFor(e, -1).config.threshold
  }

  override def config(e: Log.Entry): LogEntryConfig = {
    val config = configFor(e.context.static.id, e.context.static.position.line)
    if (e.context.dynamic.level >= config.config.threshold) {
      LogEntryConfig(config.sinks)
    } else {
      LogEntryConfig(List.empty)
    }
  }

  private val configTree = LogConfigServiceImpl.build(loggerConfig)

  @inline private def configFor(e: Log.LoggerId, line: Int): LoggerPathRule = {
    val configPath = findConfig(e.id.split('.').toList, line, List.empty, configTree)

    configPath
      .collect {
        case c: ConfiguredLogTreeNode => c
      }.last.config
  }

  @tailrec
  private final def findConfig(outPath: List[String], line: Int, inPath: List[LogTreeNode], current: LogTreeNode): List[LogTreeNode] = {
    outPath match {
      case head :: tail =>
        current match {
          case _ =>
            current.sub.get(LoggerPath(head, Some(line))).orElse(current.sub.get(LoggerPath(head, None))) match {
              case Some(value) =>
                findConfig(tail, line, inPath :+ current, value)
              case None =>
                inPath :+ current
            }
        }

      case Nil =>
        inPath :+ current
    }
  }

  private def print(node: LogTreeNode, level: Int): String = {
    val sub = node.sub.values.map(s => print(s, level + 1))

    def reprCfg(cfg: LoggerPathRule) = {
      s"${cfg.config.threshold} -> ${cfg.sinks}"
    }

    val repr = node match {
      case LogConfigServiceImpl.LogTreeRootNode(config, _) =>
        s"[${reprCfg(config)}]"
      case LogConfigServiceImpl.LogTreeEmptyNode(id, _) =>
        s"$id"
      case LogConfigServiceImpl.LogTreeMainNode(id, line, config, _) =>
        s"$id[L=$line]: ${reprCfg(config)}"
    }

    val out = (List(repr) ++ sub).mkString("\n")

    import izumi.fundamentals.platform.strings.IzString.*
    out.shift(2 * level)
  }

  override def toString: String = {
    s"""Logger configuration (${this.hashCode()}):
       |${print(configTree, 0)}
       |""".stripMargin

  }
}

object LogConfigServiceImpl {
  sealed trait LogTreeNode {
    def sub: Map[LoggerPath, IdentifiedLogTreeNode]
  }
  sealed trait IdentifiedLogTreeNode extends LogTreeNode {
    def id: String
  }

  sealed trait ConfiguredLogTreeNode extends LogTreeNode {
    def config: LoggerPathRule
  }

  case class LogTreeRootNode(config: LoggerPathRule, sub: Map[LoggerPath, IdentifiedLogTreeNode]) extends LogTreeNode with ConfiguredLogTreeNode
  case class LogTreeEmptyNode(id: String, sub: Map[LoggerPath, IdentifiedLogTreeNode]) extends IdentifiedLogTreeNode
  case class LogTreeMainNode(id: String, line: Option[Int], config: LoggerPathRule, sub: Map[LoggerPath, IdentifiedLogTreeNode])
    extends IdentifiedLogTreeNode
    with ConfiguredLogTreeNode

  def build(config: LoggerConfig): LogTreeRootNode = {
    val p = config.entries.iterator.map {
      case (k, v) =>
        val parts = k.id.split('.').toList
        (parts.init.map(p => LoggerPath(p, None)) ++ List(LoggerPath(parts.last, k.line)), v)
    }.toList
    LogTreeRootNode(config.root, buildLookupSubtree(p))
  }

  private def buildLookupSubtree(entries: List[(List[LoggerPath], LoggerPathRule)]): Map[LoggerPath, IdentifiedLogTreeNode] = {
    buildSubtrees(entries).map {
      case m: LogTreeMainNode =>
        (LoggerPath(m.id, m.line), m)
      case o =>
        (LoggerPath(o.id, None), o)
    }.toMap
  }

  private def buildSubtrees(entries: List[(List[LoggerPath], LoggerPathRule)]): List[IdentifiedLogTreeNode] = {
    entries
      .groupBy(_._1.head).map {
        case (cp, entries) =>
          val truncatedEntries = entries.map { case (p, c) => (p.tail, c) }
          val (current, sub) = truncatedEntries.partition(_._1.isEmpty)
          val subTree: Map[LoggerPath, IdentifiedLogTreeNode] = if (sub.isEmpty) Map.empty else buildLookupSubtree(sub)
          current match {
            case Nil =>
              LogTreeEmptyNode(cp.id, subTree)
            case head :: Nil =>
              LogTreeMainNode(cp.id, cp.line, head._2, subTree)
            case list =>
              throw new RuntimeException(s"BUG: More than one logger config bound to one path at $cp: $list")
          }
      }.toList
  }
}
