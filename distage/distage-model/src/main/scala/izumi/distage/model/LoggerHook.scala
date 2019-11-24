package izumi.distage.model

import izumi.fundamentals.platform.console.TrivialLogger

trait LoggerHook {
  def log(message: => String): Unit
}

object LoggerHook {
  object Null extends LoggerHook {
    override def log(message: => String): Unit = ()
  }
  final class Debug(logger: TrivialLogger) extends LoggerHook {
    override def log(message: => String): Unit = logger.log(message)
  }
}
