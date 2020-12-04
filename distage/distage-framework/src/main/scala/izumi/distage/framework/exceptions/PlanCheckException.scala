package izumi.distage.framework.exceptions

import izumi.distage.model.exceptions.DIException
import izumi.distage.planning.solver.PlanVerifier.PlanIssue
import izumi.distage.plugins.load.LoadedPlugins
import izumi.fundamentals.collections.nonempty.NonEmptySet

class PlanCheckException(message: String, val loadedPlugins: LoadedPlugins, val cause: Either[Throwable, NonEmptySet[PlanIssue]])
  extends DIException(message, cause.left.toOption.orNull)
