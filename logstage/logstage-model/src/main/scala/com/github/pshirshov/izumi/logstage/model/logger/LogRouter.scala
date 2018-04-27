package com.github.pshirshov.izumi.logstage.model.logger

import com.github.pshirshov.izumi.logstage.model.Log

trait LogRouter {
  def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean

  final def log(entry: Log.Entry): Unit = {
    try  {
      doLog(entry)
    } catch {
      case e: Throwable =>
        FallbackLogOutput.flush("Log router failed", e)
    }
  }

  protected def doLog(entry: Log.Entry): Unit
}

