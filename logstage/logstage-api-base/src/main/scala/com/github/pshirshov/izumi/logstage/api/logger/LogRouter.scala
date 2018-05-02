package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.logstage.api.Log

trait LogRouter {
  private val fallback = TrivialLogger.make[LogRouter](LogRouter.fallbackPropertyName, forceLog = true)

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
}
