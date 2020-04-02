package izumi.logstage.api

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{LogRouter, LogSink, RoutingLogger}
import izumi.logstage.api.routing.ConfigurableLogRouter
import izumi.logstage.sink.ConsoleSink

trait IzLoggerConvenienceApi[Logger <: RoutingLogger] {
  final val Level: Log.Level.type = Log.Level

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.IzLogger.log
    *
    *   def fn(implicit logger: IzLogger): Unit = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline final def log(implicit izLogger: IzLogger): izLogger.type = izLogger

  /**
    * By default, a basic colored console logger with global [[Level.Trace]] minimum threshold
    */
  final def apply(threshold: Log.Level = Log.Level.Trace, sink: LogSink = ConsoleSink.ColoredConsoleSink, levels: Map[String, Log.Level] = Map.empty): Logger = {
    val r = ConfigurableLogRouter(threshold, sink, levels)
    make(r)
  }

  final def apply(threshold: Log.Level, sinks: Seq[LogSink]): Logger = {
    val r = ConfigurableLogRouter(threshold, sinks)
    make(r)
  }

  final def apply(threshold: Log.Level, sinks: Seq[LogSink], levels: Map[String, Log.Level]): Logger = {
    val r = ConfigurableLogRouter(threshold, sinks, levels)
    make(r)
  }

  final def apply(receiver: LogRouter): Logger = {
    make(receiver)
  }

  final def apply(receiver: LogRouter, customContext: CustomContext): Logger = {
    make(receiver, customContext)
  }

  /**
    * Ignores all log messages
    */
  final lazy val NullLogger = make(LogRouter.nullRouter)

  /**
    * Prints log messages as-is, suitable for logger debugging only
    */
  final lazy val DebugLogger = make(LogRouter.debugRouter)

  private[this] final def make(r: LogRouter): Logger = make(r, CustomContext.empty)
  protected def make(r: LogRouter, context: CustomContext): Logger
}
