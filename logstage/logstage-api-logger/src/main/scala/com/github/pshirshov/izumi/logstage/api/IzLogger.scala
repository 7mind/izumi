package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.Log.{CustomContext, LogArg}
import com.github.pshirshov.izumi.logstage.api.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.api.logger.{LogRouter, LogSink}
import com.github.pshirshov.izumi.logstage.api.routing.{ConfigurableLogRouter, LogConfigServiceStaticImpl}
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink

import scala.language.implicitConversions


class IzLogger
(
  override val receiver: LogRouter
  , override val contextCustom: Log.CustomContext
) extends LoggingMacro
  with AbstractLogger {

  implicit def withCustomContext(newCustomContext: CustomContext): IzLogger = {
    new IzLogger(receiver, contextCustom + newCustomContext)
  }

  implicit def withMapAsCustomContext(map: Map[String, Any]): IzLogger = {
    withCustomContext(CustomContext(map.map(kv => LogArg(Seq(kv._1), kv._2, hidden = false)).toList))
  }

  def apply[V](conv: Map[String, V]): IzLogger = conv

  def apply[V](elems: (String, V)*): IzLogger = elems.toMap

}

object IzLogger {

  val Level: Log.Level.type = Log.Level

  /**
    * Ingores all the log messages
    */
  final lazy val NullLogger = new IzLogger(LogRouter.nullRouter, CustomContext.empty)

  /**
    * Prints log messages as-is, suitable for logger debugging only
    */
  final lazy val DebugLogger = new IzLogger(LogRouter.debugRouter, CustomContext.empty)

  /**
    * Configures basic console logger with global level threshold
    */
  final def basic(threshold: Log.Level = IzLogger.Level.Trace): IzLogger = basic(threshold, ConsoleSink.ColoredConsoleSink)

  def basic(threshold: Log.Level, sink: LogSink, sinks: LogSink*): IzLogger = {
    val router: ConfigurableLogRouter = basicRouter(threshold, sink +: sinks :_*)
    new IzLogger(router, CustomContext.empty)
  }

  def basicRouter(threshold: Log.Level, sinks: LogSink*): ConfigurableLogRouter = {
    val configService = new LogConfigServiceStaticImpl(Map.empty, LoggerConfig(threshold, sinks))
    val router = new ConfigurableLogRouter(configService)
    router
  }
}
