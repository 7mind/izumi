package izumi.fundamentals.collections

import scala.language.implicitConversions

import scala.collection.compat._

object IzCollections {
  implicit def toRichTraversables[A](xs: IterableOnce[A]): IzTraversables[A] = new IzTraversables(xs)
  implicit def toRichMappings[A, B](xs: IterableOnce[(A, B)]): IzMappings[A, B] = new IzMappings(xs)
  implicit def toRichIterables[A, Repr[_] <: Iterable[_]](xs: Repr[A]): IzIterable[A, Repr] = new IzIterable(xs)
}
