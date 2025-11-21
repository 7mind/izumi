package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.GraphImpl.*
import izumi.fundamentals.graphs.GraphProperty.*
import izumi.fundamentals.graphs.struct.{AdjacencyPredList, AdjacencySuccList}

/**
  * @param successors Dependees as values, dependencies as keys (e.g. `before -> Set(after)`
  * @param predecessors Dependencies as values, dependees as keys (e.g. `after -> Set(before)`)
  */
final case class DG[N, M] private[izumi] (
  successors: AdjacencySuccList[N],
  predecessors: AdjacencyPredList[N],
  meta: GraphMeta[N, M],
) extends AbstractGraph[N, M]
  with DirectedGraph[N, M]
  with DirectedGraphSucc[N, M]
  with DirectedGraphPred[N, M] {

  override def transposed: DirectedGraph[N, M] = {
    new DG(predecessors.transposed, successors.transposed, meta)
  }
}

object DG extends GraphSyntax[DG] {

  def fromSucc[N, M](successors: AdjacencySuccList[N], meta: GraphMeta[N, M]): DG[N, M] = {
    fromPred(successors.transposed, meta)
  }

  def fromPred[N, M](predecessors: AdjacencyPredList[N], meta: GraphMeta[N, M]): DG[N, M] = {
    unsafeFactory(predecessors, meta)
  }

  override protected def unsafeFactory[N, M](predecessors: AdjacencyPredList[N], meta: GraphMeta[N, M]): DG[N, M] = {
    new DG(predecessors.transposed, predecessors, meta.only(predecessors.links.keySet))
  }

}
