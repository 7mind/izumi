package izumi.distage.testkit.runner.impl.services

import izumi.distage.model.exceptions.runtime.ProvisioningIntegrationException
import izumi.distage.testkit.model.{EnvResult, GroupResult, IndividualTestResult, TestStatus}
import izumi.functional.bio.Exit

class TestStatusConverter(
  isTestSkipException: Throwable => Boolean
) {

  def failLevelInstantiation(cause: GroupResult.EnvLevelFailure): TestStatus.Setup = {
    cause.failure.toThrowable match {
      case s if isTestSkipException(s) => // can't match, s is always a ProvisioningException
        TestStatus.EarlyCancelled(cause, s) // this never happens right now
      case ProvisioningIntegrationException(failures) =>
        TestStatus.EarlyIgnoredByPrecondition(cause, failures)
      case t =>
        TestStatus.EarlyFailed(cause, t)
    }
  }

  def failRuntimePlanning(result: EnvResult.RuntimePlanningFailure): TestStatus.FailedRuntimePlanning = {
    TestStatus.FailedRuntimePlanning(result)
  }

  def failExecution(t: IndividualTestResult.ExecutionFailure): TestStatus.Done = {
    fail(t, t.failure, t.trace)
  }

  def failInstantiation(t: IndividualTestResult.InstantiationFailure): TestStatus.Done = {
    val throwable = t.failure.toThrowable
    fail(t, throwable, Exit.Trace.ThrowableTrace(throwable))
  }

  private def fail(cause: IndividualTestResult.IndividualTestFailure, exception: Throwable, trace: Exit.Trace[Any]): TestStatus.Done = {
    exception match {
      case s if isTestSkipException(s) =>
        TestStatus.Cancelled(cause, s, trace)
      case ProvisioningIntegrationException(failures) =>
        TestStatus.IgnoredByPrecondition(cause, failures)
      case e =>
        TestStatus.Failed(cause, e, trace)
    }
  }

  def success(s: IndividualTestResult.TestSuccess): TestStatus.Succeed = {
    TestStatus.Succeed(s)
  }
}
