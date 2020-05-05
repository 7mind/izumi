package izumi.fundamentals.graphs

import scala.collection.compat._

case class GraphMeta[N, +M](meta: Map[N, M]) extends AnyVal {
  def without(nodes: Set[N]): GraphMeta[N, M] = GraphMeta(meta.view.filterKeys(k => !nodes.contains(k)).toMap)

  def only(nodes: Set[N]): GraphMeta[N, M] = GraphMeta(meta.view.filterKeys(k => nodes.contains(k)).toMap)

  def mapNodes[N1](f: N => N1): GraphMeta[N1, M] = {
    GraphMeta(meta.map {
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
