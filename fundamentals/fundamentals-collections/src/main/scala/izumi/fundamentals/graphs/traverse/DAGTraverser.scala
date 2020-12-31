package izumi.fundamentals.graphs.traverse

import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.traverse.DAGTraverser.{Marking, TraverseFailure}

trait DAGTraverser[F[_], Node, Trace, Progress] {
  def traverse(predecessors: IncidenceMatrix[Node]): F[Either[TraverseFailure[F, Node, Trace, Progress], Marking[Node, Trace]]]
}

object DAGTraverser {

  sealed trait TraverseFailure[F[_], +N, +T, +P]

  case class Interrupted[F[_], N, T, P](state: TraverseState[F, N, T, P]) extends TraverseFailure[F, N, T, P]

  case class NonProgress[F[_], N, T](marking: Marking[N, T]) extends TraverseFailure[F, N, T, Nothing]

  case class NodeFailure[F[_], N, T](marking: Marking[N, T]) extends TraverseFailure[F, N, T, Nothing]

  case class Meta(generation: Long) extends AnyVal

  case class Marking[N, T](trace: Map[N, T], meta: Meta) {
    def contains(n: N): Boolean = trace.contains(n)

    def get(n: N): Option[T] = trace.get(n)
  }

  case class TraverseState[F[_], N, T, P](predecessors: IncidenceMatrix[N], marking: Marking[N, T], active: Map[N, MPromise[F, P, T]]) {
    def isFinished: Boolean = {
      active.isEmpty && marking.trace.keySet == predecessors.links.keySet
    }
  }

}
