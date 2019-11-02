package izumi.logstage.distage

import izumi.distage.model.LoggerHook
import izumi.logstage.api.IzLogger

class LoggerHookLoggingImpl(log: IzLogger) extends LoggerHook {
  override def log(message: => String): Unit = {
    log.debug(s"DIStage message: $message")
  }
}
