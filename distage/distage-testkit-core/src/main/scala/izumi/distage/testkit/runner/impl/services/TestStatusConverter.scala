package izumi.distage.testkit.runner.impl.services

import izumi.distage.model.exceptions.runtime.ProvisioningIntegrationException
import izumi.distage.testkit.model.{EnvResult, GroupResult, IndividualTestResult, TestStatus}

class TestStatusConverter(
  isTestSkipException: Throwable => Boolean
) {

  def failLevelInstantiation(cause: GroupResult.EnvLevelFailure): TestStatus.Setup = {
    cause.failure.toThrowable match {
      case s if isTestSkipException(s) =>
        TestStatus.EarlyCancelled(cause, s)
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
    fail(t, t.failure)
  }

  def failInstantiation(t: IndividualTestResult.InstantiationFailure): TestStatus.Done = {
    fail(t, t.failure.toThrowable)
  }

  private def fail(cause: IndividualTestResult.IndividualTestFailure, t: Throwable): TestStatus.Done = t match {
    case s if isTestSkipException(s) =>
      TestStatus.Cancelled(cause, s)
    case ProvisioningIntegrationException(failures) =>
      TestStatus.IgnoredByPrecondition(cause, failures)
    case t =>
      TestStatus.Failed(cause, t)
  }

  def success(s: IndividualTestResult.TestSuccess): TestStatus.Succeed = {
    TestStatus.Succeed(s)
  }
}
