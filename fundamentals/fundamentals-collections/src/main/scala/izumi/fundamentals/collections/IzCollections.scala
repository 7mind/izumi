package izumi.fundamentals.collections

import scala.language.implicitConversions

import scala.collection.compat._

object IzCollections {
  implicit def toRichTraversables[A](xs: IterableOnce[A]): IzTraversables[A] = new IzTraversables(xs)
  implicit def toRichMappingsIterOnce[A, B](xs: IterableOnce[(A, B)]): IzIterOnceMappings[A, B] = new IzIterOnceMappings(xs)
  implicit def toRichMappingsIter[A, B](xs: Iterable[(A, B)]): IzIterMappings[A, B] = new IzIterMappings(xs)
  implicit def toRichIterables[A, Repr[X] <: Iterable[X]](xs: Repr[A]): IzIterable[A, Repr] = new IzIterable(xs)
}
