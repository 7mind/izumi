package com.github.pshirshov.izumi.fundamentals.collections

import scala.collection.IterableLike
import scala.language.implicitConversions

object IzCollections {
  implicit def toRich[A, B, Repr](xs: IterableLike[(A, B), Repr]): IzMappings[A, B, Repr] = new IzMappings(xs)
  implicit def toRich[A, Repr](xs: IterableLike[A, Repr]): IzIterable[A, Repr] = new IzIterable(xs)
}
