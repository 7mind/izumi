package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.Log.{CustomContext, LogArg}
import com.github.pshirshov.izumi.logstage.api.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.api.logger.{LogRouter, LogSink}
import com.github.pshirshov.izumi.logstage.api.routing.{ConfigurableLogRouter, LogConfigServiceStaticImpl}
import com.github.pshirshov.izumi.logstage.sink.console.ConsoleSink

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

  final val NullLogger = new IzLogger(LogRouter.nullRouter, CustomContext.empty)
  final val DebugLogger = new IzLogger(LogRouter.debugRouter, CustomContext.empty)
  final val SimpleConsoleLogger = make(ConsoleSink.ColoredConsoleSink)

  def make(sinks: LogSink*): IzLogger = {
    val router: ConfigurableLogRouter = makeRouter(sinks :_*)
    new IzLogger(router, CustomContext.empty)
  }

  def makeRouter(sinks: LogSink*): ConfigurableLogRouter = {
    val configService = new LogConfigServiceStaticImpl(Map.empty, LoggerConfig(Log.Level.Trace, sinks))
    val router = new ConfigurableLogRouter(configService)
    router
  }
}
