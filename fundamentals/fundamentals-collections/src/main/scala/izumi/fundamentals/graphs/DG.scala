package izumi.fundamentals.graphs

import GraphProperty._
import GraphImpl._
import izumi.fundamentals.graphs.struct.IncidenceMatrix

final case class DG[N, M] private (
  successors: IncidenceMatrix[N],
  predcessors: IncidenceMatrix[N],
  meta: GraphMeta[N, M],
) extends AbstractGraph[N, M]
  with DirectedGraph[N, M]
  with DirectedGraphSucc[N, M]
  with DirectedGraphPred[N, M]

object DG extends GraphSyntax[DG] {

  def fromSucc[N, M](successors: IncidenceMatrix[N], meta: GraphMeta[N, M]): DG[N, M] = {
    fromPred(successors.transposed, meta)
  }

  def fromPred[N, M](predcessors: IncidenceMatrix[N], meta: GraphMeta[N, M]): DG[N, M] = {
    unsafeFactory(predcessors, meta)
  }

  override protected def unsafeFactory[N, M](predcessors: IncidenceMatrix[N], meta: GraphMeta[N, M]): DG[N, M] = {
    new DG(predcessors.transposed, predcessors, meta.only(predcessors.links.keySet))
  }

}
