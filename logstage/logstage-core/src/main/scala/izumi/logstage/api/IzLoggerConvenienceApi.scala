package izumi.logstage.api

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{LogQueue, LogRouter, LogSink, RoutingLogger}
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
  final def apply(
    threshold: Log.Level = Log.Level.Trace,
    sink: LogSink = ConsoleSink.ColoredConsoleSink,
    levels: Map[String, Log.Level] = Map.empty,
    buffer: LogQueue = LogQueue.Immediate,
  ): Logger = {
    make(ConfigurableLogRouter(threshold, sink, levels, buffer))
  }

  final def apply(threshold: Log.Level, sinks: Seq[LogSink]): Logger = {
    make(ConfigurableLogRouter(threshold, sinks, LogQueue.Immediate))
  }

  final def apply(threshold: Log.Level, sinks: Seq[LogSink], buffer: LogQueue): Logger = {
    make(ConfigurableLogRouter(threshold, sinks, buffer))
  }

  final def apply(threshold: Log.Level, sinks: Seq[LogSink], levels: Map[String, Log.Level], buffer: LogQueue): Logger = {
    make(ConfigurableLogRouter(threshold, sinks, levels, buffer))
  }

  final def apply(threshold: Log.Level, sinks: Seq[LogSink], levels: Map[String, Log.Level]): Logger = {
    make(ConfigurableLogRouter(threshold, sinks, levels, LogQueue.Immediate))
  }

  final def apply(router: LogRouter): Logger = {
    make(router)
  }

  final def apply(router: LogRouter, customContext: CustomContext): Logger = {
    make(router, customContext)
  }

  /**
    * Ignores all log messages
    */
  final lazy val NullLogger = make(LogRouter.nullRouter)

  /**
    * Prints log messages as-is, suitable for logger debugging only
    */
  final lazy val DebugLogger = make(LogRouter.debugRouter)

  protected[this] def make(r: LogRouter, context: CustomContext = CustomContext.empty): Logger
}
