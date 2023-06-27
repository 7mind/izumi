import izumi.logstage.api.rendering.AnyEncoded
import izumi.logstage.{api, sink}

package object logstage extends LogStage {

  type LogIO2[F[_, _]] = LogIO[F[Nothing, _]]
  type LogIO3[F[_, _, _]] = LogIO[F[Any, Nothing, _]]
  type LogIO3Ask[F[_, _, _]] = LogIO2[F[LogIO3[F], _, _]]
  type LogZIO = LogZIO.Service

  type LogCreateIO2[F[_, _]] = LogCreateIO[F[Nothing, _]]
  type LogCreateIO3[F[_, _, _]] = LogCreateIO[F[Any, Nothing, _]]

  type UnsafeLogIO2[F[_, _]] = UnsafeLogIO[F[Nothing, _]]
  type UnsafeLogIO3[F[_, _, _]] = UnsafeLogIO[F[Any, Nothing, _]]

  override type IzLogger = api.IzLogger
  override final val IzLogger: api.IzLogger.type = api.IzLogger

  override type ConsoleSink = sink.ConsoleSink
  override final val ConsoleSink: sink.ConsoleSink.type = sink.ConsoleSink

  override type LogIORaw[F[_], E <: AnyEncoded] = izumi.logstage.api.logger.LogIORaw[F, E]

  override type LogQueue = izumi.logstage.api.logger.LogQueue
  override final val LogQueue: izumi.logstage.api.logger.LogQueue.type = izumi.logstage.api.logger.LogQueue

  override type ThreadingLogQueue = sink.ThreadingLogQueue
  override final val ThreadingLogQueue: sink.ThreadingLogQueue.type = sink.ThreadingLogQueue

  override type ConfigurableLogRouter = api.routing.ConfigurableLogRouter
  override final val ConfigurableLogRouter: api.routing.ConfigurableLogRouter.type = api.routing.ConfigurableLogRouter

  override type LogRouter = api.logger.LogRouter
  override val LogRouter: api.logger.LogRouter.type = api.logger.LogRouter

  override type StaticLogRouter = api.routing.StaticLogRouter
  override val StaticLogRouter: api.routing.StaticLogRouter.type = api.routing.StaticLogRouter

  override type LogstageCodec[-T] = izumi.logstage.api.rendering.LogstageCodec[T]
  override val LogstageCodec: izumi.logstage.api.rendering.LogstageCodec.type = izumi.logstage.api.rendering.LogstageCodec

  override type Log = api.Log.type
  override final val Log: api.Log.type = api.Log

  override type Level = api.Log.Level
  override final val Level: api.Log.Level.type = api.Log.Level

  override final val Trace: api.Log.Level.Trace.type = api.Log.Level.Trace
  override final val Debug: api.Log.Level.Debug.type = api.Log.Level.Debug
  override final val Info: api.Log.Level.Info.type = api.Log.Level.Info
  override final val Warn: api.Log.Level.Warn.type = api.Log.Level.Warn
  override final val Error: api.Log.Level.Error.type = api.Log.Level.Error
  override final val Crit: api.Log.Level.Crit.type = api.Log.Level.Crit

}
