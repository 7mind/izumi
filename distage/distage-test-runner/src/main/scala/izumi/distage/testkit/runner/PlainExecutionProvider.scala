package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

final case class PlainRegisteredTest(descriptor: TestDescriptor, body: ExecutionContext => Future[Unit])

final class PlainExecutionProvider(executionContext: ExecutionContext) extends ExecutionProvider {
  private var registered = Vector.empty[PlainRegisteredTest]
  private var sequentialSuites = Set.empty[SuiteId]

  private[runner] def add(tests: Vector[PlainRegisteredTest]): Unit = {
    registered ++= tests
  }

  private[runner] def addSequential(tests: Vector[PlainRegisteredTest]): Unit = {
    sequentialSuites ++= tests.map(_.descriptor.id.suite)
    add(tests)
  }

  override def resolve(tests: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = {
    if (overrides.axes.nonEmpty || overrides.axisFilters.nonEmpty) Left(RunnerFailure.message(FailurePhase.Selection, "Plain suites have no activation axes"))
    else Right(tests.map { test =>
      val memoization = overrides.memoization match {
        case MemoizationOverride.Inherit => test.settings.memoization
        case MemoizationOverride.Enabled => true
        case MemoizationOverride.Disabled => false
      }
      test.copy(settings = test.settings.copy(memoization = memoization))
    })
  }

  override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = {
    val bodies = selected.map { descriptor =>
      val matching = registered.filter(_.descriptor.id == descriptor.id)
      require(matching.size == 1, s"Plain provider requires exactly one registration for ${descriptor.id}")
      matching.head.copy(descriptor = descriptor)
    }
    Future.successful(new ExecutionPlan {
      override val tests: Vector[TestDescriptor] = selected
      override val inspection: PlanInspection = PlanInspection.individualTests(selected.map(_.id))
      override def execute(context: RunExecutionContext): Future[ProviderOutcome] = {
        implicit val ec: ExecutionContext = executionContext
        val groups = bodies.groupBy(_.descriptor.id.suite).toVector.map { case (suite, tests) =>
          if (sequentialSuites.contains(suite)) {
            tests.foldLeft(Future.successful(Vector.empty[TestResult])) { (previous, test) =>
              previous.flatMap(results => runOne(test, context).map(results :+ _))
            }
          } else Future.sequence(tests.map(runOne(_, context)))
        }
        Future.sequence(groups).map { completed =>
          val byId = completed.flatten.map(result => result.id -> result).toMap
          val results = bodies.map(test => byId(test.descriptor.id))
          ProviderOutcome(results, Vector.empty, context.cancellation.isRequested)
        }
      }
    })
  }

  private def runOne(test: PlainRegisteredTest, context: RunExecutionContext): Future[TestResult] = {
    implicit val ec: ExecutionContext = executionContext
    if (context.cancellation.isRequested) {
      val result = TestResult(test.descriptor.id, TestStatus.Cancelled, None, 0L)
      context.emit(ProviderEvent.TestCompleted(result))
      Future.successful(result)
    } else {
      context.emit(ProviderEvent.TestStarted(test.descriptor.id))
      val started = System.nanoTime()
      val body = try test.body(executionContext) catch { case NonFatal(cause) => Future.failed(cause) }
      body.map(_ => TestResult(test.descriptor.id, TestStatus.Succeeded, None, math.max(0L, System.nanoTime() - started))).recover {
        case cancelled: TestCancelled => TestResult(test.descriptor.id, TestStatus.Cancelled, Some(RunnerFailure.fromThrowable(FailurePhase.Test, cancelled)), math.max(0L, System.nanoTime() - started))
        case NonFatal(cause) => TestResult(test.descriptor.id, TestStatus.Failed, Some(RunnerFailure.fromThrowable(FailurePhase.Test, cause)), math.max(0L, System.nanoTime() - started))
      }.map { result =>
        context.emit(ProviderEvent.TestCompleted(result))
        result
      }
    }
  }
}
