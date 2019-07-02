package com.github.pshirshov.izumi.fundamentals.collections

class IzTraversables[A](list: TraversableOnce[A]) {
  def maxOr(default: A)(implicit cmp: Ordering[A]): A = {
    if (list.nonEmpty) {
      list.max(cmp)
    } else {
      default
    }
  }
  def minOr(default: A)(implicit cmp: Ordering[A]): A = {
    if (list.nonEmpty) {
      list.min(cmp)
    } else {
      default
    }
  }

}
