package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

private[runner] final class ProviderEventEmitter(run: RunId, tests: Vector[TestDescriptor], session: RunEventEmitter) {
  private val selected = tests.map(_.id).toSet
  private var started = Set.empty[TestId]
  private var completed = Vector.empty[TestResult]
  private var reportedFailures = Vector.empty[Failure]
  private var errors = Vector.empty[Failure]
  private var finished = false

  def emit(event: ProviderEvent): Unit = synchronized {
    if (finished) reject("Provider emitted an event after its execution completed")
    event match {
      case ProviderEvent.TestStarted(test) =>
        if (!selected.contains(test) || started.contains(test) || completed.exists(_.id == test)) reject(s"Invalid test-start event: $test")
        started += test
        session.emit(RunEvent.TestStarted(run, test))
      case ProviderEvent.TestCompleted(result) =>
        validateResult(result).foreach(reject)
        if (completed.exists(_.id == result.id)) reject(s"Duplicate test-completion event: ${result.id}")
        if (!started.contains(result.id) && result.status != TestStatus.Cancelled && result.status != TestStatus.Skipped) reject(s"Test completed without a start event: ${result.id}")
        completed :+= result
        session.emit(RunEvent.TestCompleted(run, result))
      case ProviderEvent.PhaseFailed(failure) =>
        ProtocolCodec.validate(ProtocolMessage.Rejected(run, failure)).left.foreach(error => reject(s"Invalid phase failure: ${error.message}"))
        reportedFailures :+= failure
        session.emit(RunEvent.PhaseFailed(run, failure))
    }
  }

  def reconcile(outcome: ProviderOutcome): ProviderOutcome = synchronized {
    finished = true
    val valid = outcome.results.filter { result =>
      validateResult(result) match {
        case Some(reason) => errors :+= RunnerFailure.message(FailurePhase.Transport, reason); false
        case None => true
      }
    }
    if (valid.map(_.id).toSet != selected || valid.map(_.id).distinct.size != valid.size) {
      errors :+= RunnerFailure.message(FailurePhase.Transport, "Provider terminal results differ from its selected test set")
    }
    if (valid.exists(result => !completed.contains(result)) || completed.exists(result => !valid.contains(result))) {
      errors :+= RunnerFailure.message(FailurePhase.Transport, "Provider terminal results differ from its reported test completions")
    }
    val failures = outcome.failures.flatMap { failure =>
      ProtocolCodec.validate(ProtocolMessage.Rejected(run, failure)) match {
        case Left(error) => Vector(RunnerFailure.message(FailurePhase.Transport, s"Invalid provider failure: ${error.message}"))
        case Right(_) => Vector(failure)
      }
    }
    ProviderOutcome(valid, reportedFailures ++ RunnerFailure.unreported(reportedFailures, failures) ++ errors, outcome.cancelled)
  }

  private def validateResult(result: TestResult): Option[String] = {
    if (!selected.contains(result.id)) Some(s"Provider returned an unselected test: ${result.id}")
    else ProtocolCodec.validate(ProtocolMessage.Event(0L, RunEvent.TestCompleted(run, result))).left.toOption.map(error => s"Invalid terminal result for ${result.id}: ${error.message}")
  }

  private def reject(reason: String): Nothing = {
    errors :+= RunnerFailure.message(FailurePhase.Transport, reason)
    throw new IllegalStateException(reason)
  }
}
