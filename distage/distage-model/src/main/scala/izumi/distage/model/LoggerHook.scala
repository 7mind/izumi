package izumi.distage.model

trait LoggerHook {
  def log(message: => String): Unit
}
