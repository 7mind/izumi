package izumi.thirdparty.internal.boopickle

import izumi.thirdparty.internal.boopickle.Constants.NullRef

import scala.collection.generic.CanBuildFrom

private[izumi] trait XCompatImplicitPicklers {
  this: PicklerHelper =>

  implicit def mapPickler[T: P, S: P, V[_, _] <: scala.collection.Map[_, _]](
      implicit cbf: CanBuildFrom[Nothing, (T, S), V[T, S]]): P[V[T, S]] =
    BasicPicklers.MapPickler[T, S, V]
  implicit def iterablePickler[T: P, V[_] <: Iterable[_]](implicit cbf: CanBuildFrom[Nothing, T, V[T]]): P[V[T]] =
    BasicPicklers.IterablePickler[T, V]
}

private[izumi] trait XCompatPicklers {
  this: PicklerHelper =>

  /**
    * This pickler works on all collections that derive from Iterable[T] (Vector, Set, List, etc)
    *
    * @tparam T type of the values
    * @tparam V type of the collection
    * @return
    */
  def IterablePickler[T: P, V[_] <: Iterable[_]](implicit cbf: CanBuildFrom[Nothing, T, V[T]]): P[V[T]] = new P[V[T]] {
    override def pickle(iterable: V[T])(implicit state: PickleState): Unit = {
      if (iterable == null) {
        state.enc.writeInt(NullRef)
      } else {
        // encode length
        state.enc.writeInt(iterable.size)
        // encode contents
        iterable.iterator.asInstanceOf[Iterator[T]].foreach(a => write[T](a))
      }
    }

    override def unpickle(implicit state: UnpickleState): V[T] = {
      state.dec.readInt match {
        case NullRef =>
          null.asInstanceOf[V[T]]
        case 0 =>
          // empty sequence
          val res = cbf().result()
          res
        case len =>
          val b = cbf()
          b.sizeHint(len)
          var i = 0
          while (i < len) {
            b += read[T]
            i += 1
          }
          val res = b.result()
          res
      }
    }
  }

  /**
    * Maps require a specific pickler as they have two type parameters.
    *
    * @tparam T Type of keys
    * @tparam S Type of values
    * @return
    */
  def MapPickler[T: P, S: P, V[_, _] <: scala.collection.Map[_, _]](
      implicit cbf: CanBuildFrom[Nothing, (T, S), V[T, S]]): P[V[T, S]] =
    new P[V[T, S]] {
      override def pickle(map: V[T, S])(implicit state: PickleState): Unit = {
        if (map == null) {
          state.enc.writeInt(NullRef)
        } else {
          // encode length
          state.enc.writeInt(map.size)
          // encode contents as a sequence
          val kPickler = implicitly[P[T]]
          val vPickler = implicitly[P[S]]
          map.asInstanceOf[scala.collection.Map[T, S]].foreach { kv =>
            kPickler.pickle(kv._1)(state)
            vPickler.pickle(kv._2)(state)
          }
        }
      }

      override def unpickle(implicit state: UnpickleState): V[T, S] = {
        state.dec.readInt match {
          case NullRef =>
            null.asInstanceOf[V[T, S]]
          case 0 =>
            // empty map
            val res = cbf().result()
            res
          case idx if idx < 0 =>
            state.identityFor[V[T, S]](-idx)
          case len =>
            val b = cbf()
            b.sizeHint(len)
            val kPickler = implicitly[P[T]]
            val vPickler = implicitly[P[S]]
            var i        = 0
            while (i < len) {
              b += kPickler.unpickle(state) -> vPickler.unpickle(state)
              i += 1
            }
            val res = b.result()
            res
        }
      }
    }
}
