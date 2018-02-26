package com.github.pshirshov.izumi.fundamentals.collections

import scala.collection.{IterableLike, mutable}
import scala.collection.generic.CanBuildFrom


class IzIterable[A, Repr](xs: IterableLike[A, Repr]) {
  def distinctBy[B, That](f: A => B)(implicit cbf: CanBuildFrom[Repr, A, That]): That = {
    val builder = cbf(xs.repr)
    val i = xs.iterator
    val set = mutable.Set[B]()
    while (i.hasNext) {
      val o = i.next
      val b = f(o)
      if (!set(b)) {
        set += b
        builder += o
      }
    }
    builder.result
  }
}
