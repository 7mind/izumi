package izumi.distage.testkit.runner.impl.services

import izumi.distage.model.definition.Id
import izumi.distage.model.exceptions.runtime.{IntegrationCheckException, NonCriticalIntegrationFailure, ProvisioningException}
import izumi.distage.testkit.model.{EnvResult, GroupResult, IndividualTestResult, TestStatus}
import izumi.functional.bio.Exit
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.integration.ResourceCheck

class TestStatusConverter(
  isTestSkipException: Throwable => Boolean,
  skipDockerFailures: Boolean @Id("izumi.distage.testkit.skip.docker.failures"),
) {
  private class ExceptionMatcher[A](matcher: PartialFunction[Throwable, List[A]]) {
    def unapply(arg: ProvisioningException): Option[NEList[A]] = {
      val suppressed = arg.getSuppressed.toVector

      NEList
        .from(suppressed.collect(matcher).flatten.toList)
        .filter(_.size == suppressed.size) // only if all the underlying exceptions are of this type

    }
  }

  private val ProvisioningIntegrationException: ExceptionMatcher[ResourceCheck.Failure] = new ExceptionMatcher({
    case i: IntegrationCheckException => i.failures.toList
  })
  private val ProvisioningNonCritIntegrationException: ExceptionMatcher[Throwable] = {
    if (skipDockerFailures) {
      new ExceptionMatcher({
        case i: NonCriticalIntegrationFailure => List(i.asThrowable)
      })
    } else {
      new ExceptionMatcher(PartialFunction.empty)
    }
  }
  private val TestCancellationException: ExceptionMatcher[Throwable] = new ExceptionMatcher({
    case i if isTestSkipException(i) => List(i)
  })

  def success(s: IndividualTestResult.TestSuccess): TestStatus.Succeed = {
    TestStatus.Succeed(s)
  }

  def failExecution(t: IndividualTestResult.ExecutionFailure): TestStatus.Done = {
    fail(t, t.failure, t.trace)
  }

  def failInstantiation(t: IndividualTestResult.InstantiationFailure): TestStatus.Done = {
    val throwable = t.failure.toThrowable
    fail(t, throwable, Exit.Trace.ThrowableTrace(throwable))
  }

  def failRuntimePlanning(result: EnvResult.RuntimePlanningFailure): TestStatus.FailedRuntimePlanning = {
    TestStatus.FailedRuntimePlanning(result)
  }

  def failLevelInstantiation(cause: GroupResult.EnvLevelFailure): TestStatus.Setup = {
    val asThrowable = cause.failure.toThrowable
    asThrowable match {
      case TestCancellationException(_) => // can't match, s is always a ProvisioningException
        TestStatus.EarlyCancelled(cause, asThrowable) // this never happens right now

      case ProvisioningIntegrationException(failures) =>
        TestStatus.EarlyIgnoredByPrecondition(cause, failures)

      case ProvisioningNonCritIntegrationException(_) =>
        TestStatus.EarlyCancelled(cause, asThrowable)

      case t =>
        TestStatus.EarlyFailed(cause, t)
    }
  }

  private def fail(cause: IndividualTestResult.IndividualTestFailure, exception: Throwable, trace: Exit.Trace[Any]): TestStatus.Done = {
    exception match {
      case s if isTestSkipException(s) =>
        TestStatus.Cancelled(cause, s, trace)

      case i: IntegrationCheckException =>
        TestStatus.IgnoredByPrecondition(cause, i.failures)
      case ProvisioningIntegrationException(failures) => // TODO: can this match here?..
        TestStatus.IgnoredByPrecondition(cause, failures)

      case _: NonCriticalIntegrationFailure if skipDockerFailures =>
        TestStatus.Cancelled(cause, exception, trace)
      case ProvisioningNonCritIntegrationException(_) if skipDockerFailures => // TODO: can this match here?..
        TestStatus.Cancelled(cause, exception, trace)

      case e =>
        TestStatus.Failed(cause, e, trace)
    }
  }

}
