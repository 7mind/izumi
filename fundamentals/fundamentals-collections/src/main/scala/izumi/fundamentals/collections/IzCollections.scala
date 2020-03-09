package izumi.fundamentals.collections

import scala.language.implicitConversions

import scala.collection.compat._

object IzCollections {
  implicit def toRich[A](xs: IterableOnce[A]): IzTraversables[A] = new IzTraversables(xs)
  implicit def toRich[A, B](xs: Iterable[(A, B)]): IzMappings[A, B] = new IzMappings(xs)
  implicit def toRich[A, Repr[_] <: Iterable[_]](xs: Repr[A])(implicit dummy: DummyImplicit): IzIterable[A, Repr] = new IzIterable(xs)
}
