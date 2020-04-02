package izumi.logstage.api.routing

import java.util.function

import izumi.logstage.api.Log
import izumi.logstage.api.config.{LogConfigService, LogEntryConfig, LoggerConfig, LoggerPathConfig}

import scala.collection.mutable

class LogConfigServiceImpl(loggerConfig: LoggerConfig) extends LogConfigService {
  override def threshold(e: Log.LoggerId): Log.Level = {
    configFor(e).threshold
  }

  override def config(e: Log.Entry): LogEntryConfig = {
    LogEntryConfig(configFor(e.context.static.id).sinks)
  }

  @inline private[this] def configFor(e: Log.LoggerId): LoggerPathConfig = {
    configCache.getOrElseUpdate(e.id, findConfig(e.id))
  }

  // this should be efficient but may take some memory. Most likely we should use prefix tree here
  private[this] val configCache = new mutable.HashMap[String, LoggerPathConfig]()

  private[this] val findConfig: function.Function[String, LoggerPathConfig] = {
    id: String =>
      val parts = id.split('.')

      // this generates a list of all the prefixes, right to left (com.mycompany.lib.Class, com.mycompany.lib, ...)
      Stream
        .iterate(parts, parts.length)(_.init)
        .map(_.mkString("."))
        .map(id => loggerConfig.entries.get(id))
        .find(_.nonEmpty)
        .flatten
        .getOrElse(loggerConfig.root)
  }

  override def close(): Unit = {
    (loggerConfig.root.sinks ++ loggerConfig.entries.values.flatMap(_.sinks)).foreach(_.close())
    configCache.clear()
  }
}
