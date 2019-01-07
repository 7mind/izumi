package com.github.pshirshov.izumi.fundamentals.collections

import scala.collection.compat._
import scala.collection.mutable

class IzIterable[A, Repr[_] <: Iterable[_]](xs: Repr[A]) {

  def distinctBy[B, That](f: A => B)(implicit cbf: BuildFrom[Repr[A], A, That]): That = {
    val builder = cbf.newBuilder(xs)
    val i = xs.iterator.asInstanceOf[Iterator[A]] // 2.13 compat, dirty
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
