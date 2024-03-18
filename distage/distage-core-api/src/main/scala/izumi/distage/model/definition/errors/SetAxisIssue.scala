package izumi.distage.model.definition.errors

import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.planning.AxisPoint
import izumi.distage.model.reflection.DIKey

sealed trait SetAxisIssue

object SetAxisIssue {
  final case class UnconfiguredSetElementAxis(set: DIKey, element: DIKey, pos: OperationOrigin, unconfigured: Set[String]) extends SetAxisIssue

  final case class InconsistentSetElementAxis(set: DIKey, element: DIKey, problems: List[Set[AxisPoint]]) extends SetAxisIssue
}
