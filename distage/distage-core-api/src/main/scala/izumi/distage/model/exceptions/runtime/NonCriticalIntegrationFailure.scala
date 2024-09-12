package izumi.distage.model.exceptions.runtime

trait NonCriticalIntegrationFailure { this: Throwable =>
  def asThrowable: Throwable = this
}
