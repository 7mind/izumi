package com.github.pshirshov.izumi.logstage.api.config

import com.github.pshirshov.izumi.logstage.api.Log


trait LogConfigService extends AutoCloseable {
  def loggerConfig: LoggerConfig

  def threshold(e: Log.LoggerId): Log.Level = {
    configFor(e) match {
      case Some(config) =>
        config.threshold
      case _ =>
        loggerConfig.root.threshold
    }
  }

  def config(e: Log.Entry): LogEntryConfig = {
    configFor(e.context.static.id) match {
      case Some(config) =>
        LogEntryConfig(config.sinks)
      case _ =>
        LogEntryConfig(loggerConfig.root.sinks)
    }
  }

  private def configFor(e: Log.LoggerId) = {
    val parts = e.id.split('.')
    Stream
      .iterate(parts, parts.length)(_.init)
      .map(_.mkString("."))
      .map(id => loggerConfig.entries.get(id))
      .find(_.nonEmpty)
      .flatten
  }

  override def close(): Unit = {
    val allSinks = loggerConfig.entries.values.flatMap(_.sinks) ++ loggerConfig.root.sinks
    allSinks.foreach(_.close())
  }

}


