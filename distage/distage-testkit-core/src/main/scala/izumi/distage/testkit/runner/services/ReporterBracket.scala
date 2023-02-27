package izumi.distage.testkit.runner.services

import izumi.distage.model.exceptions.runtime.ProvisioningIntegrationException
import izumi.distage.testkit.model.TestStatus

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class ReporterBracket[F[_]](
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

  @deprecated
  def fail(before: Long)(t: Throwable, trace: () => Throwable): TestStatus.Done = (t, trace) match {
    case (s, _) if isTestSkipException(s) =>
      TestStatus.Cancelled(s.getMessage, testDuration(before))
    case (ProvisioningIntegrationException(failures), _) =>
      TestStatus.Ignored(failures)
    case (_, getTrace) =>
      TestStatus.Failed(getTrace(), testDuration(before))

  }

  private[this] def testDuration(before: Long): FiniteDuration = {
    val after = System.nanoTime()
    FiniteDuration(after - before, TimeUnit.NANOSECONDS)
  }
}
