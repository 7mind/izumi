package izumi.distage.framework.exceptions

import izumi.distage.model.exceptions.PlanVerificationException
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.distage.plugins.load.LoadedPlugins

class PlanCheckException(message: String, cause: Either[Throwable, PlanVerifierResult.Incorrect], val loadedPlugins: LoadedPlugins, val visitedKeys: Set[DIKey])
  extends PlanVerificationException(message, cause)
