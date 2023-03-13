package izumi.distage.testkit.model

import izumi.distage.model.Locator
import izumi.distage.model.exceptions.planning.InjectorFailed
import izumi.distage.model.plan.Plan
import izumi.distage.testkit.runner.impl.TestPlanner.PlanningFailure
import izumi.distage.testkit.runner.impl.services.Timing
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.ResourceCheck

sealed trait TestStatus {
  def order: Int
}

object TestStatus {
  /** Failures that happened during environment preparation stage. The actual test didn't even attempt to run
    */
  sealed trait Setup extends TestStatus

  sealed trait Done extends TestStatus

  final case class FailedInitialPlanning(failure: PlanningFailure, throwableCause: Throwable, timing: Timing) extends Setup with Done {
    override def order: Int = 1000
  }

  final case class FailedRuntimePlanning(failure: EnvResult.RuntimePlanningFailure) extends Setup with Done {
    override def order: Int = 2000
  }

  final case class EarlyIgnoredByPrecondition(cause: GroupResult.EnvLevelFailure, checks: NonEmptyList[ResourceCheck.Failure]) extends Setup with Done {
    override def order: Int = 2100
  }

  final case class EarlyCancelled(cause: GroupResult.EnvLevelFailure, throwableCause: Throwable) extends Setup with Done {
    override def order: Int = 2200
  }

  final case class EarlyFailed(cause: GroupResult.EnvLevelFailure, throwableCause: Throwable) extends Setup with Done {
    override def order: Int = 2300
  }

  final case class FailedPlanning(timing: Timing, failure: InjectorFailed) extends Setup with Done {
    override def order: Int = 2400
  }

  sealed trait InProgress extends TestStatus

  case class Instantiating(plan: Plan, successfulPlanningTime: Timing, logPlan: Boolean) extends InProgress {
    override def order: Int = 3000
  }

  case class Running(locator: Locator, successfulPlanningTime: Timing, successfulProvTime: Timing) extends InProgress {
    override def order: Int = 4000
  }

  /** An integration check failed
    */
  final case class IgnoredByPrecondition(cause: IndividualTestResult.IndividualTestFailure, checks: NonEmptyList[ResourceCheck.Failure]) extends Done {
    override def order: Int = 5000
  }

  /** An outcome of a test which actually started
    */
  sealed trait Finished extends Done

  //  case class PlanningFailure(test: FullMeta, failedPlanningTiming: Timing, failure: InjectorFailed) extends IndividualTestFailure {
  //    override def totalTime: FiniteDuration = failedPlanningTiming.duration
  //  }

  final case class Cancelled(cause: IndividualTestResult.IndividualTestFailure, throwableCause: Throwable) extends Finished {
    override def order: Int = 6100
  }

  final case class Failed(cause: IndividualTestResult.IndividualTestFailure, throwableCause: Throwable) extends Finished {
    override def order: Int = 6200
  }

  final case class Succeed(result: IndividualTestResult.TestSuccess) extends Finished {
    override def order: Int = 6300
  }
}
