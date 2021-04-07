package izumi.distage.model.definition.errors

import izumi.distage.model.definition.conflicts.{Annotated, MutSel, Node}
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.InstantiationOp
import izumi.distage.model.planning.AxisPoint
import izumi.distage.model.reflection.DIKey

sealed trait DIError

sealed trait LoopResolutionError extends DIError

object LoopResolutionError {
  final case class BUG_UnableToFindLoop(predcessors: Map[DIKey, Set[DIKey]]) extends LoopResolutionError
  final case class BUG_BestLoopResolutionIsNotSupported(op: ExecutableOp.SemiplanOp) extends LoopResolutionError
  final case class BestLoopResolutionCannotBeProxied(op: InstantiationOp) extends LoopResolutionError
}

sealed trait ConflictResolutionError[N, +V] extends DIError

object ConflictResolutionError {
  final case class ConflictingAxisChoices[N](issues: Map[String, Set[AxisPoint]]) extends ConflictResolutionError[N, Nothing]
  final case class ConflictingDefs[N, V](defs: Map[MutSel[N], Set[(Set[AxisPoint], Node[N, V])]]) extends ConflictResolutionError[N, V]
  final case class UnsolvedConflicts[N](defs: Map[MutSel[N], Set[Annotated[N]]]) extends ConflictResolutionError[N, Nothing]
}

