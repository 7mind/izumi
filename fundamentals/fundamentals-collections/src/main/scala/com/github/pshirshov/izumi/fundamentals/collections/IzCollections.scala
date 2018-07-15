package com.github.pshirshov.izumi.fundamentals.collections

import scala.language.implicitConversions
import scala.language.higherKinds

object IzCollections {
  implicit def toRich[A, B](xs: Iterable[(A, B)]): IzMappings[A, B] = new IzMappings(xs)
  implicit def toRich[A, Repr[_] <: Iterable[_]](xs: Repr[A]): IzIterable[A, Repr] = new IzIterable(xs)
}
