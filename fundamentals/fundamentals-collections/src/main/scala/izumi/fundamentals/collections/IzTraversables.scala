package izumi.fundamentals.collections

import izumi.functional.Lub

import scala.collection.compat._

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

  def ifEmptyOr[E, N, L](a: => E)(l: IterableOnce[A] => N)(implicit ev: Lub[E, N, L]): L = {
    if (list.iterator.isEmpty) {
      ev.fst(a)
    } else {
      ev.snd(l(list))
    }
  }

  def ifNonEmptyOr[E, N, L](l: IterableOnce[A] => N)(a: => E)(implicit ev: Lub[E, N, L]): L = {
    if (list.iterator.isEmpty) {
      ev.fst(a)
    } else {
      ev.snd(l(list))
    }
  }
}
