package izumi.fundamentals.collections

import izumi.fundamentals.collections.impl.{IzIterable, IzTraversables}
import izumi.fundamentals.platform.IzPlatformSyntax

import scala.collection.compat.*
import scala.language.implicitConversions

trait IzCollections extends IzPlatformSyntax {
  implicit def toRichTraversables[A](xs: IterableOnce[A]): IzTraversables[A] = new IzTraversables(xs)
  implicit def toRichMappingsIterOnce[A, B](xs: IterableOnce[(A, B)]): IzIterOnceMappings[A, B] = new IzIterOnceMappings(xs)
  implicit def toRichMappingsIter[A, B](xs: Iterable[(A, B)]): IzIterMappings[A, B] = new IzIterMappings(xs)
  implicit def toRichIterables[A, Repr[X] <: Iterable[X]](xs: Repr[A]): IzIterable[A, Repr] = new IzIterable(xs)

}

object IzCollections extends IzCollections {}
