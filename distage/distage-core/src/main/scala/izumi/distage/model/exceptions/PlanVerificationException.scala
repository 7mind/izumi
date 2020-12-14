package izumi.distage.model.exceptions

import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.fundamentals.collections.nonempty.NonEmptySet

class PlanVerificationException(message: String, val cause: Either[Throwable, PlanVerifierResult.Incorrect]) extends DIException(message, cause.left.toOption.orNull) {
  def issues: Option[NonEmptySet[PlanVerifier.PlanIssue]] = cause.toOption.flatMap(_.issues)
}
