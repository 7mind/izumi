package izumi.distage.testkit.runner.di

import izumi.distage.testkit.model.{FullMeta, IndividualTestResult, ScopeId, SuiteMeta, TestStatus as EngineStatus}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.{ProviderEvent, ProviderOutcome, RunExecutionContext, RunnerFailure}
import izumi.distage.testkit.runner.api.{TestFinalizationReporter, TestReporter}

import scala.util.control.NonFatal

private[distage] final class DistageProviderReporter(val tests: Vector[TestDescriptor]) extends TestReporter with TestFinalizationReporter {
  private var context = Option.empty[RunExecutionContext]
  private var results = Map.empty[TestId, TestResult]
  private var attempted = Set.empty[TestId]
  private var running = Set.empty[TestId]
  private var reportingFailures = Vector.empty[Throwable]
  private var finalizationFailures = Vector.empty[Failure]

  def begin(value: RunExecutionContext): Unit = synchronized {
    require(context.isEmpty, "Distage reporting has already started")
    context = Some(value)
  }

  def outcome(failures: Vector[Failure], cancelled: Boolean): ProviderOutcome = synchronized {
    val retained = failures ++ RunnerFailure.unreported(failures, finalizationFailures)
    ProviderOutcome(tests.flatMap(test => results.get(test.id)), retained ++ reportingFailures.map(RunnerFailure.fromThrowable(FailurePhase.Transport, _)), cancelled)
  }

  override def failure(cause: Throwable): Unit = synchronized {
    require(context.nonEmpty, "Engine finalized resources before provider reporting started")
    finalizationFailures :+= RunnerFailure.fromThrowable(FailurePhase.Finalization, cause)
  }

  def cancelled(): ProviderOutcome = synchronized {
    require(results.isEmpty, "Engine reported a repeated test completion")
    cancelRemaining(Vector.empty)
  }

  def cancelRemaining(failures: Vector[Failure]): ProviderOutcome = synchronized {
    val reporting = active
    tests.filterNot(test => results.contains(test.id)).foreach(test => complete(TestResult(test.id, TestStatus.Cancelled, None, 0L), reporting))
    outcome(failures, cancelled = true)
  }

  override def beginScope(id: ScopeId): Unit = ()
  override def endScope(id: ScopeId): Unit = ()
  override def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
  override def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
  override def beginSuite(scope: ScopeId, depth: Int, suite: SuiteMeta): Unit = ()
  override def endSuite(scope: ScopeId, depth: Int, suite: SuiteMeta): Unit = ()
  override def testSetupStatus(scope: ScopeId, depth: Int, meta: FullMeta, status: EngineStatus.Setup): Unit = report(meta, status)
  override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, status: EngineStatus): Unit = report(meta, status)

  private def report(meta: FullMeta, status: EngineStatus): Unit = synchronized {
    val reporting = active
    require(meta.test.uid >= 0L && meta.test.uid < tests.size.toLong, "Engine reported an unknown transient test identity")
    val id = tests(meta.test.uid.toInt).id
    status match {
      case _: EngineStatus.Running =>
        require(!running.contains(id) && !results.contains(id), "Engine reported a repeated test start")
        require(attempted.contains(id), "Engine reported a body before its test attempt")
        running += id
      case value: EngineStatus.Done => complete(convert(id, value), reporting)
      case _: EngineStatus.Instantiating => start(id, reporting)
    }
  }

  private def active: RunExecutionContext = context.getOrElse(throw new IllegalStateException("Engine reported execution before provider reporting started"))

  private def complete(result: TestResult, reporting: RunExecutionContext): Unit = {
    require(!results.contains(result.id), "Engine reported a repeated test completion")
    if (result.status == TestStatus.Failed && !attempted.contains(result.id)) start(result.id, reporting)
    results += result.id -> result
    emit(ProviderEvent.TestCompleted(result), reporting)
  }

  private def start(id: TestId, reporting: RunExecutionContext): Unit = {
    require(!attempted.contains(id) && !results.contains(id), "Engine reported a repeated test attempt")
    attempted += id
    emit(ProviderEvent.TestStarted(id), reporting)
  }

  private def emit(event: ProviderEvent, reporting: RunExecutionContext): Unit = {
    try reporting.emit(event)
    catch {
      case NonFatal(cause) =>
        reportingFailures :+= cause
    }
  }

  private def convert(id: TestId, status: EngineStatus.Done): TestResult = {
    def failed(phase: FailurePhase, cause: Throwable, duration: Long): TestResult =
      TestResult(id, TestStatus.Failed, Some(RunnerFailure.fromThrowable(phase, cause)), math.max(0L, duration))
    def skipped(duration: Long): TestResult =
      TestResult(id, TestStatus.Skipped, None, math.max(0L, duration))
    status match {
      case value: EngineStatus.Succeed => TestResult(id, TestStatus.Succeeded, None, math.max(0L, value.result.testTiming.duration.toNanos))
      case value: EngineStatus.Failed => failed(individualPhase(value.cause), value.throwableCause, value.cause.testTiming.duration.toNanos)
      case value: EngineStatus.Interrupted =>
        val failure = value.failure
        if (active.cancellation.isRequested) TestResult(id, TestStatus.Cancelled, Some(RunnerFailure.fromThrowable(individualPhase(failure.cause), failure.throwableCause)), math.max(0L, failure.cause.testTiming.duration.toNanos))
        else convert(id, failure)
      case value: EngineStatus.Cancelled => TestResult(id, TestStatus.Cancelled, Some(RunnerFailure.fromThrowable(individualPhase(value.cause), value.throwableCause)), math.max(0L, value.cause.testTiming.duration.toNanos))
      case value: EngineStatus.FailedInitialPlanning => failed(FailurePhase.Planning, value.throwableCause, value.timing.duration.toNanos)
      case value: EngineStatus.FailedRuntimePlanning => failed(FailurePhase.Planning, value.failure.failure.toThrowable, value.failure.timing.duration.toNanos)
      case value: EngineStatus.FailedPlanning => failed(FailurePhase.Planning, value.failure, value.timing.duration.toNanos)
      case value: EngineStatus.EarlyFailed => failed(FailurePhase.Setup, value.throwableCause, value.cause.instantiationTiming.duration.toNanos)
      case value: EngineStatus.EarlyCancelled => TestResult(id, TestStatus.Cancelled, Some(RunnerFailure.fromThrowable(FailurePhase.Setup, value.throwableCause)), math.max(0L, value.cause.instantiationTiming.duration.toNanos))
      case value: EngineStatus.EarlyIgnoredByPrecondition => skipped(value.cause.instantiationTiming.duration.toNanos)
      case value: EngineStatus.IgnoredByPrecondition => skipped(value.cause.testTiming.duration.toNanos)
    }
  }

  private def individualPhase(cause: IndividualTestResult.IndividualTestFailure): FailurePhase = cause match {
    case _: IndividualTestResult.InstantiationFailure => FailurePhase.Setup
    case _: IndividualTestResult.ExecutionFailure => FailurePhase.Test
  }
}
