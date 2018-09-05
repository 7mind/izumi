package com.github.pshirshov.izumi.logstage.api.routing

import java.util.concurrent.ConcurrentHashMap
import java.util.function

import com.github.pshirshov.izumi.logstage.api.config.LogConfigService
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.config.{LogEntryConfig, LoggerConfig}

class LogConfigServiceStaticImpl(
                                  loggerConfigs: Map[String, LoggerConfig]
                                  , rootConfig: LoggerConfig
                                ) extends LogConfigService {
  def threshold(e: Log.LoggerId): Log.Level = {
    configFor(e).threshold
  }

  def config(e: Log.Entry): LogEntryConfig = {
    LogEntryConfig(configFor(e.context.static.id).sinks)
  }

  // this should be efficient but may take some memory. Most likely we should use prefix tree here
  private[this] val configCache = new ConcurrentHashMap[String, LoggerConfig](1024)

  import scala.compat.java8.FunctionConverters._

  private[this] val findConfig: function.Function[String, LoggerConfig] = {
    id: String =>
      val parts = id.split('.')
      Stream
        .iterate(parts, parts.length)(_.init)
        .map(_.mkString("."))
        .map(id => loggerConfigs.get(id))
        .find(_.nonEmpty)
        .flatten.getOrElse(rootConfig)
  }.asJava

  @inline private[this] def configFor(e: Log.LoggerId): LoggerConfig = {
    configCache.computeIfAbsent(e.id, findConfig)
  }

  override def close(): Unit = {
    (loggerConfigs.values.flatMap(_.sinks) ++ rootConfig.sinks).foreach(_.close())
    configCache.clear()
  }
}
