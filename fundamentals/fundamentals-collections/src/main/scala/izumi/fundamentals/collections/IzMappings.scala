package izumi.fundamentals.collections

import scala.annotation.nowarn
import scala.collection.compat._
import scala.collection.mutable

@nowarn("msg=deprecated")
@nowarn("msg=Unused import")
final class IzMappings[A, B](private val list: IterableOnce[(A, B)]) extends AnyVal {
  import scala.collection.compat._

  def toMultimapMut: MutableMultiMap[A, B] = {
    list.iterator.foldLeft(new mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B]) {
      (acc, pair) =>
        acc.addBinding(pair._1, pair._2)
    }
  }

  def toMultimapView = {
    toMultimapMut.view.mapValues(_.view)
  }

  def toMultimap: ImmutableMultiMap[A, B] = {
    toMultimapMut.view.mapValues(_.toSet).toMap
  }
}
