package com.github.pshirshov.izumi.fundamentals.collections

import scala.collection.IterableLike
import scala.language.implicitConversions

object IzCollections {
  implicit def toRich[A, B, Repr](xs: Iterable[(A, B)]): IzMappings[A, B] = new IzMappings(xs)
  implicit def toRich[A, Repr](xs: IterableLike[A, Repr]): IzIterable[A, Repr] = new IzIterable(xs)
}
