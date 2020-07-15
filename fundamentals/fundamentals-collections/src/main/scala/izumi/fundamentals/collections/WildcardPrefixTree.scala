package izumi.fundamentals.collections

import izumi.fundamentals.collections
import izumi.fundamentals.collections.WildcardPrefixTree.PathElement

case class WildcardPrefixTree[K, V](values: Seq[V], children: Map[PathElement[K], WildcardPrefixTree[K, V]]) {
  def findSubtrees(prefix: List[K]): Seq[WildcardPrefixTree[K, V]] = {
    prefix match {
      case Nil =>
        Seq(this)
      case head :: tail =>
        val exact = children.get(PathElement.Value(head))
        val wildcard = children.get(PathElement.Wildcard)
        (exact.toSeq ++ wildcard.toSeq).flatMap(_.findSubtrees(tail))
    }
  }

  def subtreeValues: Seq[V] = {
    values ++ children.values.flatMap(_.subtreeValues)
  }
}

object WildcardPrefixTree {
  sealed trait PathElement[+V]
  object PathElement {
    case class Value[V](value: V) extends PathElement[V]
    case object Wildcard extends PathElement[Nothing]
  }

  def build[P, V](pairs: Seq[(Seq[Option[P]], V)]): WildcardPrefixTree[P, V] = {
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
          wk -> build(group.map(_._2))
      }
      .toMap

    collections.WildcardPrefixTree(currentValues.map(_._2), next)
  }
}
