package izumi.fundamentals.graphs.struct

import scala.collection.compat.*
import scala.collection.mutable

sealed trait AdjacencyList[N] {
  def links: Map[N, Set[N]]

  type Self[NN] <: AdjacencyList[NN]
  type Opposite[NN] <: AdjacencyList[NN]

  protected def factory[N1](links: Map[N1, Set[N1]]): Self[N1]

  def transposed: Opposite[N]

  def asSucc: AdjacencySuccList[N] = AdjacencySuccList.factory(links)
  def asPred: AdjacencyPredList[N] = AdjacencyPredList.factory(links)

  def apply(nodeId: N): Set[N] = links(nodeId)
  def get(nodeId: N): Option[Set[N]] = links.get(nodeId)

  protected def transposedList: Map[N, Set[N]] = {
    val output = mutable.HashMap.empty[N, mutable.LinkedHashSet[N]]
    links.foreach {
      case (n, linked) =>
        output.getOrElseUpdate(n, mutable.LinkedHashSet.empty[N])

        linked.foreach {
          l =>
            output.getOrElseUpdate(l, mutable.LinkedHashSet.empty[N]) += n
        }
    }
    output.view.mapValues(_.toSet).toMap
  }

  def map[N1](f: N => N1): Self[N1] = {
    factory(links.map {
      case (n, deps) =>
        (f(n), deps.map(f))
    })
  }

  def without(nodes: Set[N]): Self[N] = {
    factory(links.view.filterKeys(k => !nodes.contains(k)).mapValues(_.diff(nodes)).toMap)
  }

  def rewriteLinked(mapping: Map[N, N]): Self[N] = {
    factory(links.view.mapValues(_.map(d => mapping.getOrElse(d, d))).toMap)
  }

  def rewriteAll(mapping: Map[N, N]): Self[N] = {
    val rewritten = links.map {
      case (n, deps) =>
        val mappedKey = mapping.getOrElse(n, n)
        val mappedValue = deps.map(d => mapping.getOrElse(d, d))
        (mappedKey, mappedValue)
    }
    factory(rewritten)
  }
}

object AdjacencyList extends AdjListSyntax {
  override type Self[N] = AdjacencyList[N]

  final case class AdjacencyListUnknown[N] private[struct] (links: Map[N, Set[N]]) extends AdjacencyList[N] {
    override type Self[NN] = AdjacencyListUnknown[NN]
    override type Opposite[NN] = AdjacencyListUnknown[NN]

    def transposed: AdjacencyListUnknown[N] = new AdjacencyListUnknown[N](transposedList)

    override protected def factory[N1](links: Map[N1, Set[N1]]): AdjacencyListUnknown[N1] = new AdjacencyListUnknown(links)
  }

  override protected[struct] def factory[N1](alinks: Map[N1, Set[N1]]): AdjacencyList[N1] = new AdjacencyListUnknown(alinks)
}

trait AdjListSyntax {
  type Self[N]

  protected[struct] def factory[N1](links: Map[N1, Set[N1]]): Self[N1]

  def empty[N]: Self[N] = apply(Map.empty[N, Set[N]])

  def apply[N](links: (N, IterableOnce[N])*): Self[N] = {
    apply(links.toMap.view.mapValues(_.iterator.toSet).toMap)
  }

  def apply[N](links: Map[N, Set[N]]): Self[N] = {
    val missing = missingKeys(links)
    val normalized = links ++ missing.map(m => (m, Set.empty[N])).toMap
    factory(links ++ normalized)
  }

  def linear[N](ordered: Seq[N]): Self[N] = {
    factory(
      ordered
        .sliding(2)
        .flatMap {
          s =>
            if (s.size > 1) {
              Seq(s.head -> Set(s.last))
            } else {
              Seq.empty
            }
        }.toMap
    )
  }

  def missingKeys[N](links: Map[N, Set[N]]): Set[N] = {
    val allKeys = links.keySet ++ links.values.flatten
    val missing = allKeys -- links.keySet
    missing
  }
}

final case class AdjacencyPredList[N] private[struct] (links: Map[N, Set[N]]) extends AdjacencyList[N] {
  override type Self[NN] = AdjacencyPredList[NN]
  override type Opposite[NN] = AdjacencySuccList[NN]

  def transposed: AdjacencySuccList[N] = new AdjacencySuccList[N](transposedList)

  override protected def factory[N1](links: Map[N1, Set[N1]]): AdjacencyPredList[N1] = new AdjacencyPredList(links)
}

object AdjacencyPredList extends AdjListSyntax {
  override type Self[N] = AdjacencyPredList[N]

  override protected[struct] def factory[N1](links: Map[N1, Set[N1]]): AdjacencyPredList[N1] = new AdjacencyPredList[N1](links)
}

final case class AdjacencySuccList[N] private[struct] (links: Map[N, Set[N]]) extends AdjacencyList[N] {
  override type Self[NN] = AdjacencySuccList[NN]
  override type Opposite[NN] = AdjacencyPredList[NN]

  def transposed: AdjacencyPredList[N] = new AdjacencyPredList[N](transposedList)

  override protected[struct] def factory[N1](links: Map[N1, Set[N1]]): AdjacencySuccList[N1] = new AdjacencySuccList[N1](links)
}

object AdjacencySuccList extends AdjListSyntax {
  override type Self[N] = AdjacencySuccList[N]

  override protected[struct] def factory[N1](links: Map[N1, Set[N1]]): AdjacencySuccList[N1] = new AdjacencySuccList[N1](links)
}
