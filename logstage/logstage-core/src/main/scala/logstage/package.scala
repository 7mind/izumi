import com.github.pshirshov.izumi.logstage.{api, sink}

package object logstage extends LogStage {
  override type IzLogger = api.IzLogger
  override val IzLogger: api.IzLogger.type = api.IzLogger

  override type ConsoleSink = sink.ConsoleSink
  override val ConsoleSink: sink.ConsoleSink.type = sink.ConsoleSink

  override type QueueingSink = sink.QueueingSink
  override val QueueingSink: sink.QueueingSink.type = sink.QueueingSink

  override type ConfigurableLogRouter = api.routing.ConfigurableLogRouter
  override val ConfigurableLogRouter: api.routing.ConfigurableLogRouter.type = api.routing.ConfigurableLogRouter

  override type Log = api.Log.type
  override val Log: api.Log.type = api.Log

  override type Level = api.Log.Level
  override val Level: api.Log.Level.type = api.Log.Level

  override val Trace: Level.Trace.type = Level.Trace
  override val Debug: Level.Debug.type = Level.Debug
  override val Info: Level.Info.type = Level.Info
  override val Warn: Level.Warn.type = Level.Warn
  override val Error: Level.Error.type = Level.Error
  override val Crit: Level.Crit.type = Level.Crit
}
