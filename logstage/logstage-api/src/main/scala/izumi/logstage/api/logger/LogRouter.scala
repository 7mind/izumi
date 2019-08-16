package izumi.logstage.api.logger

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.console.TrivialLogger.Config
import izumi.logstage.api.Log

trait LogRouter extends AutoCloseable {

  def log(entry: Log.Entry): Unit

  def acceptable(id: Log.LoggerId, logLevel: Log.Level): Boolean

  override def close(): Unit = {}
}

object LogRouter {
  final val fallbackPropertyName = "izumi.logstage.routing.log-failures"

  final val nullRouter: LogRouter = new LogRouter {
    override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = false

    override def log(entry: Log.Entry): Unit = {}
  }

  final val debugRouter: LogRouter = new LogRouter {
    private val fallback: TrivialLogger = TrivialLogger.make[LogRouter](LogRouter.fallbackPropertyName, Config(forceLog = true))

    override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = true

    override def log(entry: Log.Entry): Unit = {
      fallback.log(entry.message.template.raw(entry.message.args.map(_.value) :_*) + s"\n{{ ${entry.toString} }}\n")
    }
  }
}
