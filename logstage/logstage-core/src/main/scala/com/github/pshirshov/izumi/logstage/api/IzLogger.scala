package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.Log.{CustomContext, LogArg}
import com.github.pshirshov.izumi.logstage.api.logger.{LogRouter, LogSink}
import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink

import scala.language.implicitConversions

class IzLogger
(
  override val receiver: LogRouter
  , override val contextCustom: Log.CustomContext
) extends LoggingMacro
  with AbstractLogger {

  final def log(entry: Log.Entry): Unit = receiver.log(entry)

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
    * By default, a basic colored console logger with global [[Level.Trace]] threshold
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
