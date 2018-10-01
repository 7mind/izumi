package logstage

import com.github.pshirshov.izumi.logstage.{api, sink}

import scala.language.implicitConversions

trait LogStage {
  type IzLogger = api.IzLogger
  val IzLogger: api.IzLogger.type = api.IzLogger

  type ConsoleSink = sink.ConsoleSink
  val ConsoleSink: sink.ConsoleSink.type = sink.ConsoleSink

  type QueueingSink = sink.QueueingSink
  val QueueingSink: sink.QueueingSink.type = sink.QueueingSink

  type ConfigurableLogRouter = api.routing.ConfigurableLogRouter
  val ConfigurableLogRouter: api.routing.ConfigurableLogRouter.type = api.routing.ConfigurableLogRouter

  type Log = api.Log.type
  val Log: api.Log.type = api.Log

  type Level = api.Log.Level
  val Level: api.Log.Level.type = api.Log.Level

  val Trace: Level.Trace.type = Level.Trace
  val Debug: Level.Debug.type = Level.Debug
  val Info: Level.Info.type = Level.Info
  val Warn: Level.Warn.type = Level.Warn
  val Error: Level.Error.type = Level.Error
  val Crit: Level.Crit.type = Level.Crit

  @inline implicit def ToLogMessageMacroCtor(log: api.Log.Message.type): LogMessageMacroCtor = new LogMessageMacroCtor(log)
}
