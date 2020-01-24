import izumi.logstage.{api, sink}

package object logstage extends LogStage {
  override type IzLogger = api.IzLogger
  override final val IzLogger: api.IzLogger.type = api.IzLogger

  override type ConsoleSink = sink.ConsoleSink
  override final val ConsoleSink: sink.ConsoleSink.type = sink.ConsoleSink

  override type QueueingSink = sink.QueueingSink
  override final val QueueingSink: sink.QueueingSink.type = sink.QueueingSink

  override type ConfigurableLogRouter = api.routing.ConfigurableLogRouter
  override final val ConfigurableLogRouter: api.routing.ConfigurableLogRouter.type = api.routing.ConfigurableLogRouter

  override type LogRouter = api.logger.LogRouter
  override val LogRouter: api.logger.LogRouter.type = api.logger.LogRouter

  override type StaticLogRouter = api.routing.StaticLogRouter
  override val StaticLogRouter: api.routing.StaticLogRouter.type = api.routing.StaticLogRouter

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

  type LogBIO[F[_, _]] = LogIO[F[Nothing, ?]]
  type LogBIOStrict[F[_, _]] = LogIOStrict[F[Nothing, ?]]

  type LogCreateBIO[F[_, _]] = LogCreateIO[F[Nothing, ?]]
  type UnsafeLogBIO[F[_, _]] = UnsafeLogIO[F[Nothing, ?]]
}
