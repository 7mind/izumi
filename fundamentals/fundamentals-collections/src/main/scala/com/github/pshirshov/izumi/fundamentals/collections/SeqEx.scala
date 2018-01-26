package com.github.pshirshov.izumi.fundamentals.collections

object SeqEx {

  import scala.collection.mutable

  type MutableMultiMap[A, B] = mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B]
  type ImmutableultiMap[A, B] = Map[A, Set[B]]

  implicit class SeqUtils[A, B](list: Seq[(A, B)]) {
    def toMutableMultimap: MutableMultiMap[A, B] = {
      list.foldLeft(new mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B]) {
        (acc, pair) =>
          acc.addBinding(pair._1, pair._2)
      }
    }

    def toMultimap: ImmutableultiMap[A, B] = toMutableMultimap.map { case (k, v) => (k, v.toSet) }.toMap
  }


}
