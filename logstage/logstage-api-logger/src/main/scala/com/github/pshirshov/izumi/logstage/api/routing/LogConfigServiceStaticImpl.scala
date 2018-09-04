package com.github.pshirshov.izumi.logstage.api.routing

import java.util.concurrent.ConcurrentHashMap

import com.github.pshirshov.izumi.logstage.api.config.LogConfigService
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.config.{LogEntryConfig, LoggerConfig}

class LogConfigServiceStaticImpl(
                            loggerConfigs: Map[String, LoggerConfig]
                            , rootConfig: LoggerConfig
                          ) extends LogConfigService {
  override def threshold(e: Log.LoggerId): Log.Level = {
    configFor(e).threshold
  }

  override def config(e: Log.Entry): LogEntryConfig = {
    LogEntryConfig(configFor(e.context.static.id).sinks)
  }

  // this should be efficient but may take some memory. Most likely we should use prefix tree here
  protected val configCache = new ConcurrentHashMap[String, LoggerConfig](1024)

  protected def configFor(e: Log.LoggerId): LoggerConfig = {
    import scala.compat.java8.FunctionConverters._

    configCache.computeIfAbsent(e.id, {
      id: String =>
        val parts = id.split('.')
        Stream
          .iterate(parts, parts.length)(_.init)
          .map(_.mkString("."))
          .map(id => loggerConfigs.get(id))
          .find(_.nonEmpty)
          .flatten.getOrElse(rootConfig)
    }.asJava)
  }

  override def close(): Unit = {
    (loggerConfigs.values.flatMap(_.sinks) ++ rootConfig.sinks).foreach(_.close())
  }
}
