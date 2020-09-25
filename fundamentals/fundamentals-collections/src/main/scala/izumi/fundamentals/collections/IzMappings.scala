package izumi.fundamentals.collections

import scala.annotation.nowarn
import scala.collection.mutable

@nowarn("msg=deprecated")
final class IzMappings[A, B](private val list: IterableOnce[(A, B)]) extends AnyVal {
  def toMultimapMut: MutableMultiMap[A, B] = {
    list.iterator.foldLeft(new mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B]) {
      (acc, pair) =>
        acc.addBinding(pair._1, pair._2)
    }
  }

  @nowarn("msg=Unused import")
  def toMultimapView = {
    import scala.collection.compat._
    toMultimapMut.view.mapValues(_.view)
  }

  @nowarn("msg=Unused import")
  def toMultimap: ImmutableMultiMap[A, B] = {
    import scala.collection.compat._
    toMultimapMut.view.mapValues(_.toSet).toMap
  }
}
