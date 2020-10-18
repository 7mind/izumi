package logstage

import izumi.logstage.api.rendering.AnyEncoded
import izumi.logstage.{api, sink}

trait LogStage {
  type IzLogger = api.IzLogger
  val IzLogger: api.IzLogger.type = api.IzLogger

  type ConsoleSink = sink.ConsoleSink
  val ConsoleSink: sink.ConsoleSink.type = sink.ConsoleSink

  type LogIORaw[F[_], E <: AnyEncoded] = izumi.logstage.api.logger.LogIORaw[F, E]

  type QueueingSink = sink.QueueingSink
  val QueueingSink: sink.QueueingSink.type = sink.QueueingSink

  type ConfigurableLogRouter = api.routing.ConfigurableLogRouter
  val ConfigurableLogRouter: api.routing.ConfigurableLogRouter.type = api.routing.ConfigurableLogRouter

  type LogRouter = api.logger.LogRouter
  val LogRouter: api.logger.LogRouter.type = api.logger.LogRouter

  type StaticLogRouter = api.routing.StaticLogRouter
  val StaticLogRouter: api.routing.StaticLogRouter.type = api.routing.StaticLogRouter

  type LogstageCodec[-T] = izumi.logstage.api.rendering.LogstageCodec[T]
  val LogstageCodec: izumi.logstage.api.rendering.LogstageCodec.type = izumi.logstage.api.rendering.LogstageCodec

  type Log = api.Log.type
  val Log: api.Log.type = api.Log

  type Level = api.Log.Level
  val Level: api.Log.Level.type = api.Log.Level

  val Trace: api.Log.Level.Trace.type = api.Log.Level.Trace
  val Debug: api.Log.Level.Debug.type = api.Log.Level.Debug
  val Info: api.Log.Level.Info.type = api.Log.Level.Info
  val Warn: api.Log.Level.Warn.type = api.Log.Level.Warn
  val Error: api.Log.Level.Error.type = api.Log.Level.Error
  val Crit: api.Log.Level.Crit.type = api.Log.Level.Crit

  @deprecated("renamed to `LogIO3Ask.LogIO3AskImpl`", "1.0")
  type LogBIOEnvInstance[F[-_, +_, +_]] = LogIO3Ask.LogIO3AskImpl[F]

  @deprecated("moved to `izumi.logstage.api.logger.AbstractLogIO`", "1.0")
  type AbstractLogIO[F[_]] = izumi.logstage.api.logger.AbstractLogIO[F]
  @deprecated("moved to `izumi.logstage.api.logger.EncodingAwareAbstractLogIO`", "1.0")
  type EncodingAwareAbstractLogIO[F[_], -E <: AnyEncoded] = izumi.logstage.api.logger.EncodingAwareAbstractLogIO[F, E]

  @deprecated("renamed to LogIO2", "1.0")
  type LogBIO[F[_, _]] = LogIO2[F]
  @deprecated("renamed to LogIO2", "1.0")
  lazy val LogBIO: LogIO2.type = LogIO2

  @deprecated("renamed to LogIO3", "1.0")
  type LogBIO3[F[_, _, _]] = LogIO3[F]
  @deprecated("renamed to LogIO3", "1.0")
  lazy val LogBIO3: LogIO3.type = LogIO3

  @deprecated("renamed to LogIO3Ask", "1.0")
  type LogBIOEnv[F[_, _, _]] = LogIO3Ask[F]
  @deprecated("renamed to LogIO3Ask", "1.0")
  lazy val LogBIOEnv: LogIO3Ask.type = LogIO3Ask

  @deprecated("renamed to LogCreateIO2", "1.0")
  type LogCreateBIO[F[_, _]] = LogCreateIO2[F]
  @deprecated("renamed to LogCreateIO2", "1.0")
  lazy val LogCreateBIO: LogCreateIO2.type = LogCreateIO2

  @deprecated("renamed to LogCreateIO3", "1.0")
  type LogCreateBIO3[F[_, _, _]] = LogCreateIO3[F]
  @deprecated("renamed to LogCreateIO3", "1.0")
  lazy val LogCreateBIO3: LogCreateIO3.type = LogCreateIO3

  @deprecated("renamed to UnsafeLogIO2", "1.0")
  type UnsafeLogBIO[F[_, _]] = UnsafeLogIO2[F]
  @deprecated("renamed to UnsafeLogIO2", "1.0")
  lazy val UnsafeLogBIO: UnsafeLogIO2.type = UnsafeLogIO2

  @deprecated("renamed to UnsafeLogIO3", "1.0")
  type UnsafeLogBIO3[F[_, _, _]] = UnsafeLogIO3[F]
  @deprecated("renamed to UnsafeLogIO3", "1.0")
  lazy val UnsafeLogBIO3: UnsafeLogIO3.type = UnsafeLogIO3

  @deprecated("renamed to LogZIO", "1.0")
  lazy val LogstageZIO: LogZIO.type = LogZIO
}
