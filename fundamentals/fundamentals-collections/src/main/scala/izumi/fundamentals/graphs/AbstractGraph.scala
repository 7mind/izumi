package izumi.fundamentals.graphs

import scala.annotation.nowarn

@nowarn("msg=Unused import")
final case class GraphMeta[N, +M](nodes: Map[N, M]) extends AnyVal {
  import scala.collection.compat._

  def without(nodes: Set[N]): GraphMeta[N, M] = GraphMeta(this.nodes -- nodes)
  def only(nodes: Set[N]): GraphMeta[N, M] = GraphMeta(this.nodes.view.filterKeys(k => nodes.contains(k)).toMap)

  def mapNodes[N1](f: N => N1): GraphMeta[N1, M] = {
    GraphMeta(nodes.map {
      case (n, m) =>
        (f(n), m)
    })
  }
}

object GraphMeta {
  def empty[N]: GraphMeta[N, Nothing] = GraphMeta(Map.empty[N, Nothing])
}

trait AbstractGraph[N, +M] {
  def meta: GraphMeta[N, M]
}

final case class Edge[N](predecessor: N, successor: N)
final case class WeakEdge[N](predecessor: N, successor: N)
