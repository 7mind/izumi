package izumi.fundamentals.graphs.traverse

import izumi.fundamentals.graphs.traverse.DAGTraverser.TraverseState

trait AbstractDagTraverser[F[_], Node, Trace, Progress] extends ResumableDAGTraverser[F, Node, Trace, Progress] {

  def doStep(state: TraverseState[F, Node, Trace, Progress]): F[TraverseState[F, Node, Trace, Progress]]

}

