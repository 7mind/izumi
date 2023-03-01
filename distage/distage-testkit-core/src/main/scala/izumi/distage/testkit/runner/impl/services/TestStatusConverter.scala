package izumi.distage.testkit.runner.impl.services

import izumi.distage.model.exceptions.runtime.ProvisioningIntegrationException
import izumi.distage.testkit.model.TestStatus

import scala.concurrent.duration.FiniteDuration

class TestStatusConverter[F[_]](
  isTestSkipException: Throwable => Boolean
) {
  def fail(duration: FiniteDuration, t: Throwable): TestStatus.Done = t match {
    case s if isTestSkipException(s) =>
      TestStatus.Cancelled(s.getMessage, duration)
    case ProvisioningIntegrationException(failures) =>
      TestStatus.Ignored(failures)
    case t =>
      TestStatus.Failed(t, duration)
  }
}
