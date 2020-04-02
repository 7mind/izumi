package logstage

import izumi.logstage.{api, sink}

trait LogStage {
  type IzLogger = api.IzLogger
  val IzLogger: api.IzLogger.type = api.IzLogger

  type ConsoleSink = sink.ConsoleSink
  val ConsoleSink: sink.ConsoleSink.type = sink.ConsoleSink

  type QueueingSink = sink.QueueingSink
  val QueueingSink: sink.QueueingSink.type = sink.QueueingSink

  type ConfigurableLogRouter = api.routing.ConfigurableLogRouter
  val ConfigurableLogRouter: api.routing.ConfigurableLogRouter.type = api.routing.ConfigurableLogRouter

  type LogRouter = api.logger.LogRouter
  val LogRouter: api.logger.LogRouter.type = api.logger.LogRouter

  type StaticLogRouter = api.routing.StaticLogRouter
  val StaticLogRouter: api.routing.StaticLogRouter.type = api.routing.StaticLogRouter

  type LogstageCodec[T] = izumi.logstage.api.rendering.LogstageCodec[T]
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
}
