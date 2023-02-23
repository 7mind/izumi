package izumi.distage.testkit.runner.services

import izumi.distage.model.exceptions.runtime.ProvisioningIntegrationException
import izumi.distage.testkit.model.TestStatus

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class ReporterBracket[F[_]](
//   reporter: TestReporter,
  isTestSkipException: Throwable => Boolean
) {
  def done(before: Long): TestStatus.Succeed = TestStatus.Succeed(testDuration(before))

  def fail(before: Long)(t: Throwable, trace: () => Throwable): TestStatus.Done = (t, trace) match {
    case (s, _) if isTestSkipException(s) =>
      TestStatus.Cancelled(s.getMessage, testDuration(before))
    case (ProvisioningIntegrationException(failures), _) =>
      TestStatus.Ignored(failures)
    case (_, getTrace) =>
      TestStatus.Failed(getTrace(), testDuration(before))

  }

//  def reportFailure(test: DistageTest[F], startedAt: Long)(t: Throwable, trace: () => Throwable) = reporter.testStatus(test.meta, fail(startedAt)(t, trace))

  private[this] def testDuration(before: Long): FiniteDuration = {
    val after = System.nanoTime()
    FiniteDuration(after - before, TimeUnit.NANOSECONDS)
  }
}
