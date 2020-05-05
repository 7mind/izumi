package izumi.fundamentals.graphs.traverse

import izumi.fundamentals.graphs.traverse.DAGTraverser.{Marking, TraverseFailure, TraverseState}

trait ResumableDAGTraverser[F[_], Node, Trace, Progress] extends DAGTraverser[F, Node, Trace, Progress] {
  def continueTraversal(initial: TraverseState[F, Node, Trace, Progress]): F[Either[TraverseFailure[F, Node, Trace, Progress], Marking[Node, Trace]]]
}
