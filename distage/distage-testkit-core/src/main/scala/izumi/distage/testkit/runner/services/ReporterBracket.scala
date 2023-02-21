package izumi.distage.testkit.runner.services

import izumi.distage.testkit.model.{DistageTest, TestStatus}
import izumi.distage.testkit.runner.api.TestReporter

import scala.concurrent.duration.Duration

class ReporterBracket[F[_]](reporter: TestReporter) {
  def withRecoverFromFailedExecution[A](allTests: Seq[DistageTest[F]])(f: => A)(onError: => A): A = {
    try {
      f
    } catch {
      case t: Throwable =>
        // fail all tests (if an exception reaches here, it must have happened before the runtime was successfully produced)
        allTests.foreach {
          test => reporter.testStatus(test.meta, TestStatus.Failed(t, Duration.Zero))
        }
        reporter.onFailure(t)
        onError
    }
  }
}
