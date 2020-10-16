package izumi.distage.model.exceptions

import izumi.distage.model.definition.Axis.AxisPoint
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.reflection.DIKey

sealed trait SetAxisIssue

case class UnconfiguredSetElementAxis(set: DIKey, element: DIKey, pos: OperationOrigin, unconfigured: Set[String]) extends SetAxisIssue
case class InconsistentSetElementAxis(set: DIKey, element: DIKey, problems: List[Set[AxisPoint]]) extends SetAxisIssue

class BadSetAxis(message: String, val problems: List[SetAxisIssue]) extends DIException(message)
