package com.github.pshirshov.izumi.fundamentals.collections

class IzTraversables[A](list: TraversableOnce[A]) {
  def maxOr[B >: A](default: A)(implicit cmp: Ordering[B]): A = {
    if (list.nonEmpty) {
      list.max(cmp)
    } else {
      default
    }
  }
  def minOr[B >: A](default: A)(implicit cmp: Ordering[B]): A = {
    if (list.nonEmpty) {
      list.min(cmp)
    } else {
      default
    }
  }

}
