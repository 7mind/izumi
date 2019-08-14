package com.github.pshirshov.izumi.fundamentals

package object collections {
  import scala.collection.mutable
  type MutableMultiMap[A, B] = mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B]
  type ImmutableMultiMap[A, B] = Map[A, Set[B]]
}
