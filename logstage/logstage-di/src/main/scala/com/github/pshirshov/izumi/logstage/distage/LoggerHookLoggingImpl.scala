package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.LoggerHook
import com.github.pshirshov.izumi.logstage.api.IzLogger

class LoggerHookLoggingImpl(log: IzLogger) extends LoggerHook {
  override def log(message: => String): Unit = {
    log.debug(s"DIStage message: $message")
  }
}
