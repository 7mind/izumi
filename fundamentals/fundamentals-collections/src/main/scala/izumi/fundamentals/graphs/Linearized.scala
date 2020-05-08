package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.GraphImpl.{DirectedGraphPred, DirectedGraphSucc}
import izumi.fundamentals.graphs.GraphProperty.DirectedGraph
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.{Toposort, ToposortLoopBreaker}

final case class Linearized[N, +M](
  nodes: Seq[N],
  meta: GraphMeta[N, M],
) extends AbstractGraph[N, M]
  with DirectedGraph[N, M]
  with DirectedGraphSucc[N, M]
  with DirectedGraphPred[N, M] {
  override lazy val successors: IncidenceMatrix[N] = IncidenceMatrix.linear(nodes)

  override lazy val predcessors: IncidenceMatrix[N] = IncidenceMatrix.linear(nodes.reverse)
}

object Linearized {
  def fromDag[N, M](dag: DAG[N, M]): Linearized[N, M] = {
    new Toposort().cycleBreaking(dag.predcessors, ToposortLoopBreaker.dontBreak) match {
      case Left(value) =>
        throw new IllegalStateException(s"Non-linerizable DAG, this can't be. Error: $value; $dag")
      case Right(value) =>
        Linearized(value, dag.meta)
    }
  }

  def from[N, M](dg: DG[N, M], breaker: ToposortLoopBreaker[N]): Either[ToposortError[N], Linearized[N, M]] = {
    for {
      sorted <- new Toposort().cycleBreaking(dg.predcessors, breaker)
    } yield {
      Linearized(sorted, dg.meta)
    }
  }
}
