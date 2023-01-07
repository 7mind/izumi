package izumi.distage.model.exceptions.planning

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.planning.AxisPoint
import izumi.distage.model.reflection.DIKey

@deprecated("Needs to be removed", "20/10/2021")
class BadSetAxis(message: String, val problems: List[SetAxisIssue]) extends DIException(message)

sealed trait SetAxisIssue
final case class UnconfiguredSetElementAxis(set: DIKey, element: DIKey, pos: OperationOrigin, unconfigured: Set[String]) extends SetAxisIssue
final case class InconsistentSetElementAxis(set: DIKey, element: DIKey, problems: List[Set[AxisPoint]]) extends SetAxisIssue
