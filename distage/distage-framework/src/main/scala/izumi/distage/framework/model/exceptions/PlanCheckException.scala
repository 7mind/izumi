package izumi.distage.framework.model.exceptions

import scala.annotation.nowarn
import izumi.distage.model.exceptions.PlanVerificationException
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.distage.plugins.load.LoadedPlugins

@nowarn("msg=shadows field")
class PlanCheckException(message: String, cause: Either[Throwable, PlanVerifierResult.Incorrect], val loadedPlugins: LoadedPlugins, val visitedKeys: Set[DIKey])
  extends PlanVerificationException(message, cause)
