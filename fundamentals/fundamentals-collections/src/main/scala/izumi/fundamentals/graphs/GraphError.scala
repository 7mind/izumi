package izumi.fundamentals.graphs

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

sealed trait ToposortError[N] extends GraphTraversalError[N]
object ToposortError {
  final case class UnexpectedLoop[N](done: Seq[N], matrix: IncidenceMatrix[N]) extends ToposortError[N]
  final case class InconsistentInput[N](issues: IncidenceMatrix[N]) extends ToposortError[N]
}
