package com.github.pshirshov.izumi.logstage.api.routing

import com.github.pshirshov.izumi.logstage.api.config.LogConfigService
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.config.{LogEntryConfig, LoggerConfig}

class LogConfigServiceStaticImpl(
                                  loggerConfigs: Map[String, LoggerConfig]
                                  , rootConfig: LoggerConfig
                                ) extends LogConfigService {
  override def threshold(e: Log.LoggerId): Log.Level = {
    configFor(e) match {
      case Some(config) =>
        config.threshold
      case _ =>
        rootConfig.threshold
    }
  }

  override def config(e: Log.Entry): LogEntryConfig = {
    configFor(e.context.static.id) match {
      case Some(config) =>
        LogEntryConfig(config.sinks)
      case _ =>
        LogEntryConfig(rootConfig.sinks)
    }
  }

  private def configFor(e: Log.LoggerId): Option[LoggerConfig] = {
    val parts = e.id.split('.')
    Stream
      .iterate(parts, parts.length)(_.init)
      .map(_.mkString("."))
      .map(id => loggerConfigs.get(id))
      .find(_.nonEmpty)
      .flatten
  }

  override def close(): Unit = {
    (loggerConfigs.values.flatMap(_.sinks) ++ rootConfig.sinks).foreach(_.close())
  }
}
