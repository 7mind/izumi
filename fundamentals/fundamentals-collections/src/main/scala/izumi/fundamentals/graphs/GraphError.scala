package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.tools.mutations.MutationResolver.{Annotated, AxisPoint, MutSel, Node}
import izumi.fundamentals.graphs.struct.IncidenceMatrix

sealed trait AbstractGraphError

sealed trait GraphError[N] extends AbstractGraphError

sealed trait DAGError[N] extends GraphError[N]
object DAGError {
  final case class LoopBreakerFailed[N](loopMember: N) extends DAGError[N]
  final case class UnexpectedLoops[N]() extends DAGError[N]
}

sealed trait GraphTraversalError[N] extends GraphError[N]
object GraphTraversalError {
  final case class UnrecoverableLoops[N]() extends GraphTraversalError[N]
}

sealed trait ConflictResolutionError[N, +V] extends GraphTraversalError[N]
object ConflictResolutionError {
  final case class AmbigiousActivationsSet[N](issues: Map[String, Set[AxisPoint]]) extends ConflictResolutionError[N, Nothing]
  final case class ConflictingDefs[N, V](defs: Map[MutSel[N], Set[(Set[AxisPoint], Node[N, V])]]) extends ConflictResolutionError[N, V]
  final case class UnsolvedConflicts[N](defs: Map[MutSel[N], Set[Annotated[N]]]) extends ConflictResolutionError[N, Nothing]
}

sealed trait ToposortError[N] extends GraphTraversalError[N]
object ToposortError {
  final case class UnexpectedLoop[N](done: Seq[N], matrix: IncidenceMatrix[N]) extends ToposortError[N]
  final case class InconsistentInput[N](issues: IncidenceMatrix[N]) extends ToposortError[N]
}
