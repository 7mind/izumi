package izumi.fundamentals.graphs

sealed trait GraphProperty[N, +M] { this: AbstractGraph[N, M] => }

object GraphProperty {

  trait DirectedGraph[N, +M] extends GraphProperty[N, M] { this: AbstractGraph[N, M] =>
    def transposed: DirectedGraph[N, M]
  }

  trait DirectedAcyclicGraph[N, +M] extends DirectedGraph[N, M] { this: AbstractGraph[N, M] =>
    def toposorted: Seq[N]
  }
}
