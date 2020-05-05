package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.GraphImpl.{DirectedGraphPred, DirectedGraphSucc}
import izumi.fundamentals.graphs.GraphProperty.DirectedAcyclicGraph
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.{CycleEraser, LoopBreaker}

final case class DAG[N, M] private
(
  successors: IncidenceMatrix[N],
  predcessors: IncidenceMatrix[N],
  meta: GraphMeta[N, M]
)
  extends AbstractGraph[N, M]
    with DirectedAcyclicGraph[N, M]
    with DirectedGraphSucc[N, M]
    with DirectedGraphPred[N, M]

object DAG extends GraphSyntax[DAG] {

  def fromSucc[N, M](successors: IncidenceMatrix[N], meta: GraphMeta[N, M], breaker: LoopBreaker[N] = LoopBreaker.terminating[N]): Either[DAGError[N], DAG[N, M]] = {
    for {
      predcessorsWithoutLoops <- new CycleEraser[N](successors.transposed, breaker)
        .run()
    } yield {
      unsafeFactory(predcessorsWithoutLoops, meta)
    }
  }

  def fromPred[N, M](predcessors: IncidenceMatrix[N], meta: GraphMeta[N, M], breaker: LoopBreaker[N] = LoopBreaker.terminating[N]): Either[DAGError[N], DAG[N, M]] = {
    fromSucc(predcessors, meta, breaker)
  }

  override protected def unsafeFactory[N, M](predcessors: IncidenceMatrix[N], meta: GraphMeta[N, M]): DAG[N, M] = {
    new DAG(predcessors.transposed, predcessors, meta.only(predcessors.links.keySet))
  }

}





