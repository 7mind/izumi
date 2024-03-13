package izumi.distage.model.definition.errors

import izumi.distage.model.definition.conflicts.{Annotated, MutSel, Node}
import izumi.distage.model.planning.{ActivationChoices, AxisPoint}
import izumi.fundamentals.collections.nonempty.NEList

sealed trait ConflictResolutionError[N, +V]

object ConflictResolutionError {

  final case class ConflictingAxisChoices[N](issues: Map[String, Set[AxisPoint]]) extends ConflictResolutionError[N, Nothing]

  final case class ConflictingDefs[N, V](defs: Map[MutSel[N], Set[(Set[AxisPoint], Node[N, V])]], activations: ActivationChoices) extends ConflictResolutionError[N, V]

  final case class UnsolvedConflicts[N](defs: Map[MutSel[N], Set[Annotated[N]]]) extends ConflictResolutionError[N, Nothing]

  final case class CannotProcessLocalContext[N](problems: NEList[LocalContextPlanningFailure]) extends ConflictResolutionError[N, Nothing]

  final case class UnconfiguredAxisInMutators[N](problems: NEList[UnconfiguredMutatorAxis]) extends ConflictResolutionError[N, Nothing]

  final case class SetAxisProblem[N](problems: NEList[SetAxisIssue]) extends ConflictResolutionError[N, Nothing]

}
