package izumi.distage.model.exceptions

import izumi.distage.model.planning.PlanIssue
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.fundamentals.collections.nonempty.NESet

class PlanVerificationException(message: String, val cause: Either[Throwable, PlanVerifierResult.Incorrect]) extends DIException(message, cause.left.toOption.orNull) {
  def issues: Option[NESet[PlanIssue]] = cause.toOption.flatMap(_.issues)
}
