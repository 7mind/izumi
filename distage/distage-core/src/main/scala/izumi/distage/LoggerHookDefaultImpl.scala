package izumi.distage

import izumi.distage.model.LoggerHook

class LoggerHookDefaultImpl extends LoggerHook {
  override def log(message: => String): Unit = {}
}

