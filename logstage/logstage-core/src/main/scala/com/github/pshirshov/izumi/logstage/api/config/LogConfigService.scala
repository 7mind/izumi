package com.github.pshirshov.izumi.logstage.api.config

import java.util.concurrent.ConcurrentHashMap
import java.util.function

import com.github.pshirshov.izumi.logstage.api.Log


trait LogConfigService extends AutoCloseable {
  def loggerConfig: LoggerConfig

  def threshold(e: Log.LoggerId): Log.Level = {
    configFor(e).threshold
  }

  def config(e: Log.Entry): LogEntryConfig = {
    LogEntryConfig(configFor(e.context.static.id).sinks)
  }

  // this should be efficient but may take some memory. Most likely we should use prefix tree here
  private[this] val configCache = new ConcurrentHashMap[String, LoggerPathConfig](1024)


  private[this] val findConfig: function.Function[String, LoggerPathConfig] = {
    id: String =>
      val parts = id.split('.')
      Stream
        .iterate(parts, parts.length)(_.init)
        .map(_.mkString("."))
        .map(id => loggerConfig.entries.get(id))
        .find(_.nonEmpty)
        .flatten.getOrElse(loggerConfig.root)
  }

  @inline private[this] def configFor(e: Log.LoggerId): LoggerPathConfig = {
    configCache.computeIfAbsent(e.id, findConfig)
  }

  override def close(): Unit = {
    (loggerConfig.root.sinks ++ loggerConfig.entries.values.flatMap(_.sinks)).foreach(_.close())
    configCache.clear()
  }
}
