package izumi.logstage.api.logger

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.console.TrivialLogger.Config
import izumi.logstage.DebugProperties
import izumi.logstage.api.Log
import izumi.logstage.api.routing.ConfigurableLogRouter
import izumi.logstage.sink.ConsoleSink

trait LogRouter extends AutoCloseable {
  def log(entry: Log.Entry): Unit
  def acceptable(id: Log.LoggerId, logLevel: Log.Level): Boolean

  override def close(): Unit = {}
}

object LogRouter {
  def apply(
    threshold: Log.Level = Log.Level.Trace,
    sink: LogSink = ConsoleSink.ColoredConsoleSink,
    levels: Map[String, Log.Level] = Map.empty,
    buffer: LogQueue = LogQueue.Immediate,
  ): ConfigurableLogRouter = {
    ConfigurableLogRouter(threshold, Seq(sink), levels, buffer)
  }

  lazy val debugRouter: LogRouter = new LogRouter {
    private val fallback: TrivialLogger = TrivialLogger.make[LogRouter](DebugProperties.`izumi.logstage.routing.log-failures`.name, Config(forceLog = true))

    override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = true

    override def log(entry: Log.Entry): Unit = {
      fallback.log(entry.message.template.raw(entry.message.args.map(_.value): _*) + s"\n{{ ${entry.toString} }}\n")
    }
  }

  lazy val nullRouter: LogRouter = new LogRouter {
    override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = false
    override def log(entry: Log.Entry): Unit = {}
  }
}
