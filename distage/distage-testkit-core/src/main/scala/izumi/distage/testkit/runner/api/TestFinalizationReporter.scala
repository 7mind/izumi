package izumi.distage.testkit.runner.api

trait TestFinalizationReporter {
  def failure(cause: Throwable): Unit
}

object TestFinalizationReporter {
  final class Rethrowing extends TestFinalizationReporter {
    override def failure(cause: Throwable): Unit = throw cause
  }
}
