package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.logstage.api.Log

trait LogRouter {
  protected val fallback: TrivialLogger = TrivialLogger.make[LogRouter](LogRouter.fallbackPropertyName, forceLog = true)

  def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean

  final def log(entry: Log.Entry): Unit = {
    try  {
      doLog(entry)
    } catch {
      case e: Throwable =>
        fallback.log("Log router failed", e)
    }
  }

  protected def doLog(entry: Log.Entry): Unit
}

object LogRouter {
  final val fallbackPropertyName = "izumi.logstage.routing.log-failures"

  final val nullRouter = new LogRouter {
    override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = false

    override protected def doLog(entry: Log.Entry): Unit = {}
  }

  final val debugRouter = new LogRouter {
    override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = true

    override protected def doLog(entry: Log.Entry): Unit = fallback.log(entry.toString)
  }
}
