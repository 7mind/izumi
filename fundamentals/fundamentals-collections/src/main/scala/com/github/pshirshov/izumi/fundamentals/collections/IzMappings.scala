package com.github.pshirshov.izumi.fundamentals.collections

import scala.collection.{IterableLike, mutable}

class IzMappings[A, B, Repr](list: IterableLike[(A, B), Repr]) {
  def toMutableMultimap: MutableMultiMap[A, B] = {
    list.foldLeft(new mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B]) {
      (acc, pair) =>
        acc.addBinding(pair._1, pair._2)
    }
  }

  def toMultimap: ImmutableultiMap[A, B] = toMutableMultimap.map { case (k, v) => (k, v.toSet) }.toMap
}
