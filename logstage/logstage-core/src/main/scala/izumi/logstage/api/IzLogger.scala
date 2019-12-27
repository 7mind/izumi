package izumi.logstage.api

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{LogRouter, LogSink}
import izumi.logstage.api.routing.ConfigurableLogRouter
import izumi.logstage.sink.ConsoleSink

import scala.language.implicitConversions

class IzLogger
(
  override val router: LogRouter
, override val customContext: Log.CustomContext
) extends RoutingLogger {

  override def withCustomContext(context: CustomContext): IzLogger = new IzLogger(router, customContext + context)
  final def withCustomContext(context: (String, Any)*): IzLogger = withCustomContext(context.toMap)
  final def withCustomContext(context: Map[String, Any]): IzLogger = withCustomContext(CustomContext(context))

  final def apply(context: CustomContext): IzLogger = withCustomContext(context)
  final def apply(context: (String, Any)*): IzLogger = withCustomContext(context.toMap)
  final def apply(context: Map[String, Any]): IzLogger = withCustomContext(context)

}

object IzLogger {

  final val Level: Log.Level.type = Log.Level

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

}
