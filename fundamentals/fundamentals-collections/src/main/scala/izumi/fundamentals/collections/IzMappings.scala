package izumi.fundamentals.collections

import scala.collection.{Iterable, mutable}

final class IzMappings[A, B](private val list: Iterable[(A, B)]) extends AnyVal {
  def toMutableMultimap: MutableMultiMap[A, B] = {
    list.foldLeft(new mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B]) {
      (acc, pair) =>
        acc.addBinding(pair._1, pair._2)
    }
  }

  def toMultimap: ImmutableMultiMap[A, B] = toMutableMultimap.map { case (k, v) => (k, v.toSet) }.toMap
}
