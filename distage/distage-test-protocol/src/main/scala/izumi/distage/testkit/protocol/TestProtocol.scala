package izumi.distage.testkit.protocol

final case class BuildId(value: String) extends AnyVal
final case class CatalogueId(value: String) extends AnyVal
final case class BuildTargetId(value: String) extends AnyVal
final case class SuiteId(value: String) extends AnyVal
final case class RunId(value: String) extends AnyVal
final case class AxisId(value: String) extends AnyVal
final case class AxisValue(value: String) extends AnyVal

final case class CatalogueIdentity(build: BuildId, target: BuildTargetId, catalogue: CatalogueId)
final case class TestId(target: BuildTargetId, suite: SuiteId, path: Vector[String], variant: Option[String])
final case class AxisChoice(axis: AxisId, value: AxisValue)
final case class EffectiveSettings(axes: Vector[AxisChoice], memoization: Boolean)

sealed trait MemoizationOverride
object MemoizationOverride {
  case object Inherit extends MemoizationOverride
  case object Enabled extends MemoizationOverride
  case object Disabled extends MemoizationOverride
}

sealed trait Selection
object Selection {
  case object All extends Selection
  final case class Only(suites: Vector[SuiteId], tests: Vector[TestId]) extends Selection
}

final case class RunOverrides(axes: Vector[AxisChoice], axisFilters: Vector[AxisChoice], memoization: MemoizationOverride)
final case class RunRequest(identity: CatalogueIdentity, selection: Selection, overrides: RunOverrides)

sealed trait SourceLocation
object SourceLocation {
  final case class Known(path: String, line: Int, column: Option[Int]) extends SourceLocation
  case object Unavailable extends SourceLocation
}

final case class SuiteDescriptor(id: SuiteId, displayName: String)
final case class TestDescriptor(id: TestId, displayName: String, location: SourceLocation, settings: EffectiveSettings)
final case class Catalogue(identity: CatalogueIdentity, suites: Vector[SuiteDescriptor], tests: Vector[TestDescriptor])

sealed trait FailurePhase
object FailurePhase {
  case object Discovery extends FailurePhase
  case object Selection extends FailurePhase
  case object Planning extends FailurePhase
  case object Setup extends FailurePhase
  case object Test extends FailurePhase
  case object Finalization extends FailurePhase
  case object Transport extends FailurePhase
}

sealed trait ObservedValue
object ObservedValue {
  final case class Evaluated(value: String) extends ObservedValue
  case object NotEvaluated extends ObservedValue
  final case class RenderingFailed(exceptionClass: String, message: String) extends ObservedValue
}

final case class DiagnosticObservation(expression: Option[String], location: SourceLocation, value: ObservedValue)
final case class AssertionDiagnostic(location: SourceLocation, expression: Option[String], observations: Vector[DiagnosticObservation])
final case class Failure(
  phase: FailurePhase,
  exceptionClass: String,
  message: String,
  stack: Vector[String],
  causes: Vector[Failure],
  assertion: Option[AssertionDiagnostic],
)

sealed trait TestStatus
object TestStatus {
  case object Succeeded extends TestStatus
  case object Failed extends TestStatus
  case object Cancelled extends TestStatus
  case object Skipped extends TestStatus
}

final case class TestResult(id: TestId, status: TestStatus, failure: Option[Failure], durationNanos: Long)
final case class RunOutcome(run: RunId, results: Vector[TestResult], failures: Vector[Failure], cancelled: Boolean) {
  def successful: Boolean = !cancelled && failures.isEmpty && results.forall { result =>
    result.failure.isEmpty && (result.status == TestStatus.Succeeded || result.status == TestStatus.Skipped)
  }
}

sealed trait RunEvent { def run: RunId }
object RunEvent {
  final case class Started(run: RunId) extends RunEvent
  final case class TestStarted(run: RunId, test: TestId) extends RunEvent
  final case class TestCompleted(run: RunId, result: TestResult) extends RunEvent
  final case class PhaseFailed(run: RunId, failure: Failure) extends RunEvent
  final case class Finished(run: RunId, outcome: RunOutcome) extends RunEvent
}

sealed trait RequestOperation
object RequestOperation {
  case object Resolve extends RequestOperation
  case object Plan extends RequestOperation
  case object Execute extends RequestOperation
}

sealed trait ProtocolMessage
object ProtocolMessage {
  final case class Discover(run: RunId, build: BuildId, target: BuildTargetId) extends ProtocolMessage
  final case class Request(operation: RequestOperation, run: RunId, request: RunRequest) extends ProtocolMessage
  final case class Cancel(run: RunId) extends ProtocolMessage
  final case class Discovered(run: RunId, catalogue: Catalogue) extends ProtocolMessage
  final case class Event(sequence: Long, event: RunEvent) extends ProtocolMessage
  final case class Completed(outcome: RunOutcome) extends ProtocolMessage
  final case class Rejected(run: RunId, failure: Failure) extends ProtocolMessage
}

final case class ProtocolDecodeError(message: String)
