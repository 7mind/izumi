package izumi.fundamentals.collections

import  scala.collection.compat._

final class IzTraversables[A](private val list: IterableOnce[A]) extends AnyVal {

  def maxOr(default: A)(implicit cmp: Ordering[A]): A = {
    val iterator = list.iterator
    if (iterator.nonEmpty) {
      iterator.max(cmp)
    } else {
      default
    }
  }

  def minOr(default: A)(implicit cmp: Ordering[A]): A = {
    val iterator = list.iterator
    if (iterator.nonEmpty) {
      iterator.min(cmp)
    } else {
      default
    }
  }

}
