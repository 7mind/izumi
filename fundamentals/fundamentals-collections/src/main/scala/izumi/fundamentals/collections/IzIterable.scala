package izumi.fundamentals.collections

import scala.collection.compat._
import scala.collection.mutable

final class IzIterable[A, Repr[X] <: Iterable[X]](private val xs: Repr[A]) extends AnyVal {

  def distinctBy[B, That](f: A => B)(implicit cbf: BuildFrom[Repr[A], A, That]): That = {
    val builder = cbf.newBuilder(xs)
    val i = xs.iterator
    val set = mutable.Set[B]()
    while (i.hasNext) {
      val o = i.next()
      val b = f(o)
      if (!set(b)) {
        set += b
        builder += o
      }
    }
    builder.result()
  }
}
