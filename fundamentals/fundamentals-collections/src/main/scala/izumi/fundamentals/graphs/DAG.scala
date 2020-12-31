package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.GraphImpl.{DirectedGraphPred, DirectedGraphSucc}
import izumi.fundamentals.graphs.GraphProperty.DirectedAcyclicGraph
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.cycles.{CycleEraser, LoopBreaker}

final case class DAG[N, M] private (
  successors: IncidenceMatrix[N],
  predecessors: IncidenceMatrix[N],
  meta: GraphMeta[N, M],
) extends AbstractGraph[N, M]
  with DirectedAcyclicGraph[N, M]
  with DirectedGraphSucc[N, M]
  with DirectedGraphPred[N, M]

object DAG extends GraphSyntax[DAG] {

  def fromSucc[N, M](successors: IncidenceMatrix[N], meta: GraphMeta[N, M], breaker: LoopBreaker[N] = LoopBreaker.terminating[N]): Either[DAGError[N], DAG[N, M]] = {
    new CycleEraser[N](successors.transposed, breaker)
      .run().map(unsafeFactory(_, meta))
  }

  def fromPred[N, M](predecessors: IncidenceMatrix[N], meta: GraphMeta[N, M], breaker: LoopBreaker[N] = LoopBreaker.terminating[N]): Either[DAGError[N], DAG[N, M]] = {
    fromSucc(predecessors, meta, breaker)
  }

  override protected def unsafeFactory[N, M](predecessors: IncidenceMatrix[N], meta: GraphMeta[N, M]): DAG[N, M] = {
    new DAG(predecessors.transposed, predecessors, meta.only(predecessors.links.keySet))
  }

}
