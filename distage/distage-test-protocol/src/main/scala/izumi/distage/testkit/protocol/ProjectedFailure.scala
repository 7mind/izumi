package izumi.distage.testkit.protocol

final class ProjectedFailure private (val failure: Failure, message: String)
  extends RuntimeException(message, failure.causes.headOption.map(ProjectedFailure.child).orNull) {
  failure.causes.drop(1).foreach(cause => addSuppressed(ProjectedFailure.child(cause)))
  failure.suppressed.foreach(cause => addSuppressed(ProjectedFailure.child(cause)))
  failure.captureErrors.foreach(error => addSuppressed(new ProjectedCaptureError(error)))
}

object ProjectedFailure {
  def root(failure: Failure): ProjectedFailure = new ProjectedFailure(failure, diagnostic(failure))
  private def child(failure: Failure): ProjectedFailure = new ProjectedFailure(failure, summary(failure))
  private def summary(failure: Failure): String = s"${failure.phase}: ${failure.exceptionClass}: ${failure.message}"

  // Forked SBT 2 can retain only the top-level message of the captured exception graph.
  private def diagnostic(failure: Failure): String = {
    val causes = failure.causes.map(value => "Caused by: " + diagnostic(value))
    val suppressed = failure.suppressed.map(value => "Suppressed: " + diagnostic(value))
    val captureErrors = failure.captureErrors.map(error => s"Cannot capture failure field ${error.field}: ${error.exceptionClass}")
    (Vector(summary(failure)) ++ causes ++ suppressed ++ captureErrors).mkString("\n")
  }
}

private[protocol] final class ProjectedCaptureError(val error: FailureCaptureError)
  extends RuntimeException(s"Cannot capture failure field ${error.field}: ${error.exceptionClass}")
