package izumi.distage.framework.model

import izumi.distage.framework.model.exceptions.PlanCheckException
import izumi.distage.model.planning.PlanIssue
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.distage.plugins.load.LoadedPlugins
import izumi.fundamentals.collections.nonempty.NESet

sealed abstract class PlanCheckResult {
  def checkedPlugins: LoadedPlugins
  def visitedKeys: Set[DIKey]

  def maybeError: Option[Either[Throwable, PlanVerifierResult.Incorrect]]
  def maybeErrorMessage: Option[String]

  final def issues: Option[NESet[PlanIssue]] = maybeError.flatMap(_.toOption.flatMap(_.issues))
  final def verificationPassed: Boolean = maybeError.isEmpty
  final def verificationFailed: Boolean = maybeError.isDefined

  final def throwOnError(): Unit = this match {
    case _: PlanCheckResult.Correct =>
    case PlanCheckResult.Incorrect(loadedPlugins, visitedKeys, message, cause) => throw new PlanCheckException(message, cause, loadedPlugins, visitedKeys)
  }
}

object PlanCheckResult {
  final case class Correct(checkedPlugins: LoadedPlugins, visitedKeys: Set[DIKey]) extends PlanCheckResult {
    override def maybeError: None.type = None
    override def maybeErrorMessage: None.type = None
  }
  final case class Incorrect(checkedPlugins: LoadedPlugins, visitedKeys: Set[DIKey], message: String, cause: Either[Throwable, PlanVerifierResult.Incorrect])
    extends PlanCheckResult {
    override def maybeError: Some[Either[Throwable, PlanVerifierResult.Incorrect]] = Some(cause)
    override def maybeErrorMessage: Some[String] = Some(message)
  }
}
