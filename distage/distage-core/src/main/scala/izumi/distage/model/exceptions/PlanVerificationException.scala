package izumi.distage.model.exceptions

import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult

class PlanVerificationException(message: String, val cause: Either[Throwable, PlanVerifierResult.Incorrect]) extends DIException(message, cause.left.toOption.orNull)
