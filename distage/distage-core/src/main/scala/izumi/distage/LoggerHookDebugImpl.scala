package izumi.distage

import izumi.fundamentals.platform.console.TrivialLogger

class LoggerHookDebugImpl(logger: TrivialLogger) extends LoggerHookDefaultImpl {
  override def log(message: => String): Unit =
    logger.log(message)
}
