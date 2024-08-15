package izumi.fundamentals.collections

import izumi.fundamentals.collections
import izumi.fundamentals.collections.WildcardPrefixTree.PathElement

import scala.annotation.tailrec

final case class WildcardPrefixTree[K, V](values: Seq[V], children: Map[PathElement[K], WildcardPrefixTree[K, V]]) {
  @tailrec
  def findSubtree(prefix: Iterable[K]): Option[WildcardPrefixTree[K, V]] = {
    if (prefix.isEmpty) {
      Some(this)
    } else {
      val exact = children.get(PathElement.Value(prefix.head))
      val wildcard = children.get(PathElement.Wildcard)
      exact.orElse(wildcard) match {
        case Some(value) =>
          value.findSubtree(prefix.tail)
        case None =>
          None
      }
    }
  }

  def findSubtrees(prefix: Iterable[K]): Seq[WildcardPrefixTree[K, V]] = {
    if (prefix.isEmpty) {
      Seq(this)
    } else {
      val exact = children.get(PathElement.Value(prefix.head))
      val wildcard = children.get(PathElement.Wildcard)
      (exact.toSeq ++ wildcard.toSeq).flatMap(_.findSubtrees(prefix.tail))
    }
  }

  def allValuesFromSubtrees: Seq[V] = {
    values ++ children.values.flatMap(_.allValuesFromSubtrees)
  }

  def maxValues: Int = {
    (Seq(values.size) ++ children.values.map(_.maxValues)).max
  }
}

object WildcardPrefixTree {
  sealed trait PathElement[+V]
  object PathElement {
    final case class Value[V](value: V) extends PathElement[V]
    case object Wildcard extends PathElement[Nothing]
  }

  def build[P, V](pairs: Seq[(Seq[Option[P]], V)]): WildcardPrefixTree[P, V] = {
    build(pairs, identity)
  }

  def build[P, V](pairs: Seq[(Seq[Option[P]], V)], map: (Seq[V] => Seq[V])): WildcardPrefixTree[P, V] = {
    val (currentValues, subValues) = pairs.partition(_._1.isEmpty)

    val next = subValues
      .collect {
        case (k :: tail, v) =>
          (k, (tail, v))
      }
      .groupBy(_._1)
      .toSeq
      .map {
        case (k, group) =>
          val wk: PathElement[P] = k match {
            case Some(value) =>
              PathElement.Value(value)
            case None =>
              PathElement.Wildcard
          }
          wk -> build(group.map(_._2), map)
      }
      .toMap

    val values = map(currentValues.map(_._2))

    collections.WildcardPrefixTree(values, next)
  }
}
