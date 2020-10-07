package izumi.logstage.api.routing

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.console.TrivialLogger.Config
import izumi.logstage.DebugProperties
import izumi.logstage.api.Log
import izumi.logstage.api.config.{LogConfigService, LoggerConfig, LoggerPathConfig}
import izumi.logstage.api.logger.{LogRouter, LogSink}
import izumi.logstage.sink.{ConsoleSink, FallbackConsoleSink}

import scala.annotation.nowarn
import scala.util.control.NonFatal

class ConfigurableLogRouter(
  logConfigService: LogConfigService
) extends LogRouter {
  private final val fallback = TrivialLogger.make[FallbackConsoleSink](DebugProperties.`izumi.logstage.routing.log-failures`.name, Config(forceLog = true))

  override def log(entry: Log.Entry): Unit = {
    val sinks = logConfigService
      .config(entry)
      .sinks

    sinks.foreach {
      sink =>
        try {
          sink.flush(entry)
        } catch {
          case NonFatal(e) =>
            fallback.log(s"Log sink $sink failed", e)
        }
    }
  }

  override def close(): Unit = {
    logConfigService.close()
  }

  override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = {
    logConfigService.threshold(id) <= messageLevel
  }
}

object ConfigurableLogRouter {
  def apply(threshold: Log.Level = Log.Level.Trace, sink: LogSink = ConsoleSink.ColoredConsoleSink, levels: Map[String, Log.Level] = Map.empty): ConfigurableLogRouter = {
    ConfigurableLogRouter(threshold, Seq(sink), levels)
  }

  def apply(threshold: Log.Level, sinks: Seq[LogSink]): ConfigurableLogRouter = {
    ConfigurableLogRouter(threshold, sinks, Map.empty[String, Log.Level])
  }

  @nowarn("msg=Unused import")
  def apply(threshold: Log.Level, sinks: Seq[LogSink], levels: Map[String, Log.Level]): ConfigurableLogRouter = {
    import scala.collection.compat._

    val rootConfig = LoggerPathConfig(threshold, sinks)
    val levelConfigs = levels.view.mapValues(lvl => LoggerPathConfig(lvl, sinks)).toMap

    val configService = new LogConfigServiceImpl(LoggerConfig(rootConfig, levelConfigs))

    ConfigurableLogRouter(configService)
  }

  def apply(logConfigService: LogConfigService): ConfigurableLogRouter = new ConfigurableLogRouter(logConfigService)
}
