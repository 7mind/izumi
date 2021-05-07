package izumi.fundamentals.graphs

import GraphProperty._
import GraphImpl._
import izumi.fundamentals.graphs.struct.IncidenceMatrix

final case class DG[N, M] private[izumi] (
  successors: IncidenceMatrix[N],
  predecessors: IncidenceMatrix[N],
  meta: GraphMeta[N, M],
) extends AbstractGraph[N, M]
  with DirectedGraph[N, M]
  with DirectedGraphSucc[N, M]
  with DirectedGraphPred[N, M]

object DG extends GraphSyntax[DG] {

  def fromSucc[N, M](successors: IncidenceMatrix[N], meta: GraphMeta[N, M]): DG[N, M] = {
    fromPred(successors.transposed, meta)
  }

  def fromPred[N, M](predecessors: IncidenceMatrix[N], meta: GraphMeta[N, M]): DG[N, M] = {
    unsafeFactory(predecessors, meta)
  }

  override protected def unsafeFactory[N, M](predecessors: IncidenceMatrix[N], meta: GraphMeta[N, M]): DG[N, M] = {
    new DG(predecessors.transposed, predecessors, meta.only(predecessors.links.keySet))
  }

}
