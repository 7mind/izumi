package izumi.fundamentals.collections

import scala.annotation.nowarn

@nowarn("msg=[Uu]nused import")
final class IzMultiMaps[A, B](private val mmap: scala.collection.Map[A, Set[B]]) extends AnyVal {
  import scala.collection.compat._

  def unwrap: List[(A, B)] = {
    mmap.view.flatMap { case (k, vs) => vs.map(v => (k, v)) }.toList
  }
}

@nowarn("msg=[Uu]nused import")
final class IzIterOnceMappings[A, B](private val list: scala.collection.compat.IterableOnce[(A, B)]) extends AnyVal {
  import scala.collection.compat._

  @nowarn("msg=deprecated")
  def toMultimapMut: MutableMultiMap[A, B] = {
    list.iterator.foldLeft(new scala.collection.mutable.HashMap[A, scala.collection.mutable.Set[B]] with scala.collection.mutable.MultiMap[A, B]) {
      (acc, pair) =>
        acc.addBinding(pair._1, pair._2)
    }
  }

  // don't add return type here, the types are different between scala versions
  def toMultimapView = {
    toMultimapMut.view.mapValues(_.view)
  }

  def toMultimap: ImmutableMultiMap[A, B] = {
    toMultimapMut.view.mapValues(_.toSet).toMap
  }
}

@nowarn("msg=[Uu]nused import")
final class IzIterMappings[A, B](private val list: Iterable[(A, B)]) extends AnyVal {

  import scala.collection.compat._

  def toUniqueMap[E](onConflict: Map[A, List[B]] => E): Either[E, Map[A, B]] = {
    val grouped = list.groupBy(_._1)
    val bad = grouped.filter(_._2.size > 1)

    if (bad.isEmpty) {
      Right(list.toMap)
    } else {
      Left(onConflict(bad.view.mapValues(_.map(_._2).toList).toMap))
    }

  }
}
