package com.github.pshirshov.izumi.logstage.api.routing

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.config.{LogConfigService, LoggerConfig, LoggerPathConfig}
import com.github.pshirshov.izumi.logstage.api.logger.{LogRouter, LogSink}
import com.github.pshirshov.izumi.logstage.sink.{ConsoleSink, FallbackConsoleSink}

class ConfigurableLogRouter
(
  logConfigService: LogConfigService
) extends LogRouter {
  private final val fallback = TrivialLogger.make[FallbackConsoleSink](LogRouter.fallbackPropertyName, forceLog = true)

  override protected def doLog(entry: Log.Entry): Unit = {
    logConfigService
      .config(entry)
      .sinks
      .foreach {
        sink =>
          try  {
            sink.flush(entry)
          } catch {
            case e: Throwable =>
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
  final def apply(threshold: Log.Level, sink: LogSink = ConsoleSink.ColoredConsoleSink, levels: Map[String, Log.Level] = Map.empty): ConfigurableLogRouter = {
    apply(threshold, Seq(sink), levels)
  }

  final def apply(threshold: Log.Level, sinks: Seq[LogSink]): ConfigurableLogRouter = {
    apply(threshold, sinks, Map.empty[String, Log.Level])
  }

  final def apply(threshold: Log.Level, sinks: Seq[LogSink], levels: Map[String, Log.Level]): ConfigurableLogRouter = {
    val levelConfigs = levels.mapValues(lvl => LoggerPathConfig(lvl, sinks))
    val rootConfig = LoggerPathConfig(threshold, sinks)
    val configService = new LogConfigServiceImpl(LoggerConfig(rootConfig, levelConfigs))
    val router = new ConfigurableLogRouter(configService)
    router
  }
}
