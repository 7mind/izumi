package izumi.logstage.api.routing

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.console.TrivialLogger.Config
import izumi.logstage.DebugProperties
import izumi.logstage.api.Log
import izumi.logstage.api.config.{LogConfigService, LoggerConfig, LoggerPath, LoggerPathConfig, LoggerPathForLines, LoggerPathRule}
import izumi.logstage.api.logger.{LogQueue, LogRouter, LogSink}
import izumi.logstage.sink.{ConsoleSink, FallbackConsoleSink}

import scala.annotation.nowarn
import scala.util.control.NonFatal

class ConfigurableLogRouter(
  logConfigService: LogConfigService,
  buffer: LogQueue,
) extends LogRouter {
  private final val fallback = TrivialLogger.make[FallbackConsoleSink](DebugProperties.`izumi.logstage.routing.log-failures`.name, Config(forceLog = true))

  override def log(entry: Log.Entry): Unit = {
    val sinks = logConfigService
      .config(entry)
      .sinks

    sinks.foreach {
      sink =>
        try {
          buffer.append(entry, sink)
//          sink.flush(entry)
        } catch {
          case NonFatal(e) =>
            fallback.log(s"Log sink $sink failed", e)
        }
    }
  }

  override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = {
    logConfigService.threshold(id) <= messageLevel
  }
}

object ConfigurableLogRouter {
  def apply(
    threshold: Log.Level = Log.Level.Trace,
    sink: LogSink = ConsoleSink.ColoredConsoleSink,
    levels: Map[String, Log.Level] = Map.empty,
    buffer: LogQueue = LogQueue.Immediate,
  ): ConfigurableLogRouter = {
    ConfigurableLogRouter(threshold, Seq(sink), levels, buffer)
  }

  def apply(threshold: Log.Level, sinks: Seq[LogSink], buffer: LogQueue): ConfigurableLogRouter = {
    ConfigurableLogRouter(threshold, sinks, Map.empty[String, Log.Level], buffer)
  }

  @nowarn("msg=Unused import")
  def apply(threshold: Log.Level, sinks: Seq[LogSink], levels: Map[String, Log.Level], buffer: LogQueue): ConfigurableLogRouter = {
    import scala.collection.compat._

    val rootConfig = LoggerPathRule(LoggerPathConfig(threshold), sinks)
    val levelConfigs = levels.view.flatMap {
      case (id, lvl) =>
        val p = LoggerPathForLines.parse(id)
        if (p.lines.nonEmpty) {
          p.lines.map {
            line =>
              (LoggerPath(p.id, Some(line)), LoggerPathRule(LoggerPathConfig(lvl), sinks))
          }
        } else {
          Seq((LoggerPath(p.id, None), LoggerPathRule(LoggerPathConfig(lvl), sinks)))
        }

    }.toMap

    val configService = new LogConfigServiceImpl(LoggerConfig(rootConfig, levelConfigs))

    ConfigurableLogRouter(configService, buffer)
  }

  def apply(logConfigService: LogConfigService, buffer: LogQueue): ConfigurableLogRouter = new ConfigurableLogRouter(logConfigService, buffer)
}
