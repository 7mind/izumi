package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.{LogRouter, LogSink}
import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink

import scala.language.implicitConversions

class IzLogger
(
  override val router: LogRouter
, override val customContext: Log.CustomContext
) extends RoutingLogger {

  def apply(context: CustomContext): IzLogger = withCustomContext(context)
  def apply(context: Map[String, Any]): IzLogger = withMapAsCustomContext(context)
  def apply(context: (String, Any)*): IzLogger = withMapAsCustomContext(context.toMap)

  implicit override def withCustomContext(context: CustomContext): IzLogger = {
    new IzLogger(router, customContext + context)
  }

  implicit def withMapAsCustomContext(context: Map[String, Any]): IzLogger = {
    withCustomContext(CustomContext(context))
  }

}

object IzLogger {

  val Level: Log.Level.type = Log.Level

  /**
    * By default, a basic colored console logger with global [[Level.Trace]] minimum threshold
    */
  final def apply(threshold: Log.Level = IzLogger.Level.Trace, sink: LogSink = ConsoleSink.ColoredConsoleSink, levels: Map[String, Log.Level] = Map.empty): IzLogger = {
    val r = ConfigurableLogRouter(threshold, sink, levels)
    new IzLogger(r, CustomContext.empty)
  }

  final def apply(threshold: Log.Level, sinks: Seq[LogSink]): IzLogger = {
    val r = ConfigurableLogRouter(threshold, sinks)
    new IzLogger(r, CustomContext.empty)
  }

  final def apply(threshold: Log.Level, sinks: Seq[LogSink], levels: Map[String, Log.Level]): IzLogger = {
    val r = ConfigurableLogRouter(threshold, sinks, levels)
    new IzLogger(r, CustomContext.empty)
  }

  final def apply(receiver: LogRouter): IzLogger = {
    new IzLogger(receiver, CustomContext.empty)
  }

  final def apply(receiver: LogRouter, customContext: CustomContext): IzLogger = {
    new IzLogger(receiver, customContext)
  }

  /**
    * Ignores all log messages
    */
  final lazy val NullLogger = new IzLogger(LogRouter.nullRouter, CustomContext.empty)

  /**
    * Prints log messages as-is, suitable for logger debugging only
    */
  final lazy val DebugLogger = new IzLogger(LogRouter.debugRouter, CustomContext.empty)

  def parseLevel(v: String): Log.Level = {
    v.charAt(0).toLower match {
      case 't' => Log.Level.Trace
      case 'd' => Log.Level.Debug
      case 'i' => Log.Level.Info
      case 'w' => Log.Level.Warn
      case 'e' => Log.Level.Error
      case 'c' => Log.Level.Crit
    }
  }
}
