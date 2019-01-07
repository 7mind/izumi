package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.logstage.api.Log

import scala.util.control.NonFatal

trait LogRouter extends AutoCloseable {
  private final val fallback: TrivialLogger = TrivialLogger.make[LogRouter](LogRouter.fallbackPropertyName, forceLog = true)

  final def log(entry: Log.Entry): Unit = {
    try {
      doLog(entry)
    } catch {
      case NonFatal(e) =>
        fallback.log("Log router failed", e)
    }
  }

  def acceptable(id: Log.LoggerId, logLevel: Log.Level): Boolean

  @inline protected def doLog(entry: Log.Entry): Unit

  override def close(): Unit = {}
}

object LogRouter {
  final val fallbackPropertyName = "izumi.logstage.routing.log-failures"

  final val nullRouter = new LogRouter {
    override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = false

    override protected def doLog(entry: Log.Entry): Unit = {}
  }

  final val debugRouter = new LogRouter {
    private val fallback: TrivialLogger = TrivialLogger.make[LogRouter](LogRouter.fallbackPropertyName, forceLog = true)

    override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = true

    override protected def doLog(entry: Log.Entry): Unit = {
      fallback.log(entry.message.template.raw(entry.message.args.map(_.value) :_*) + s"\n{{ ${entry.toString} }}\n")
    }
  }
}
