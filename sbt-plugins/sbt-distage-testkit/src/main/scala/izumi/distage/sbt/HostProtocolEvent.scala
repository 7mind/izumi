package izumi.distage.sbt

import izumi.distage.testkit.protocol.{ProjectedFailure, TestResult, TestStatus}
import sbt.testing.{Event, Fingerprint, OptionalThrowable, Selector, Status, SuiteSelector, TestSelector}

import java.util.concurrent.TimeUnit

private[sbt] final case class HostProtocolEvent(fullyQualifiedName: String, fingerprint: Fingerprint, selector: Selector, status: Status, throwable: OptionalThrowable, duration: Long) extends Event

private[sbt] object HostProtocolEvent {
  def result(name: String, fingerprint: Fingerprint, result: TestResult): Event = {
    val status = result.status match {
      case TestStatus.Succeeded => Status.Success
      case TestStatus.Failed => Status.Failure
      case TestStatus.Cancelled => Status.Canceled
      case TestStatus.Skipped => Status.Skipped
    }
    val cause = result.failure.fold(new OptionalThrowable)(failure => new OptionalThrowable(ProjectedFailure.root(failure)))
    HostProtocolEvent(name, fingerprint, new TestSelector(result.id.path.mkString(" ")), status, cause, TimeUnit.NANOSECONDS.toMillis(result.durationNanos))
  }

  def error(name: String, fingerprint: Fingerprint, cause: Throwable): Event =
    HostProtocolEvent(name, fingerprint, new SuiteSelector, Status.Error, new OptionalThrowable(cause), 0L)
}
