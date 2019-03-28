/*
* https://github.com/mdedetrich/linked-map
* */
package scala.collection.immutable

import scala.collection.{Iterator, immutable}
import scala.collection.generic.{CanBuildFrom, ImmutableMapFactory}

/**
  * A generic trait for ordered immutable maps. Concrete classes have to provide
  * functionality for the abstract methods in `LinkedMap`:
  *
  * @tparam A
  * @tparam B
  */
trait LinkedMap[A, +B]
    extends Map[A, B]
    with Iterable[(A, B)]
    with scala.collection.Map[A, B]
    with MapLike[A, B, LinkedMap[A, B]] {
  override def empty: LinkedMap[A, B] = LinkedMap.empty

  override def seq: LinkedMap[A, B] = this

  override def withDefault[B1 >: B](d: A => B1): immutable.LinkedMap[A, B1] =
    new LinkedMap.WithDefault[A, B1](this, d)

  override def withDefaultValue[B1 >: B](d: B1): immutable.LinkedMap[A, B1] =
    new LinkedMap.WithDefault[A, B1](this, x => d)

  @inline override def updated[B1 >: B](key: A, value: B1): LinkedMap[A, B1] =
    this + ((key, value))

  override def +[B1 >: B](kv: (A, B1)): LinkedMap[A, B1]

  @inline override def apply(key: A): B = get(key) match {
    case None => default(key)
    case Some(value) => value
  }
}

object LinkedMap extends ImmutableMapFactory[LinkedMap] {
  implicit def canBuildFrom[A, B]: CanBuildFrom[Coll, (A, B), LinkedMap[A, B]] =
    new MapCanBuildFrom[A, B]

  @inline override def empty[A, B]: LinkedMap[A, B] =
    EmptyMap.asInstanceOf[LinkedMap[A, B]]

  class WithDefault[A, +B](underlying: LinkedMap[A, B], d: A => B)
      extends scala.collection.Map.WithDefault[A, B](underlying, d)
      with LinkedMap[A, B] {
    override def empty = new WithDefault(underlying.empty, d)
    override def updated[B1 >: B](key: A, value: B1): WithDefault[A, B1] =
      new WithDefault[A, B1](underlying.updated[B1](key, value), d)
    override def +[B1 >: B](kv: (A, B1)): WithDefault[A, B1] =
      updated(kv._1, kv._2)
    override def -(key: A): WithDefault[A, B] =
      new WithDefault(underlying - key, d)
    override def withDefault[B1 >: B](d: A => B1): immutable.LinkedMap[A, B1] =
      new WithDefault[A, B1](underlying, d)
    override def withDefaultValue[B1 >: B](d: B1): immutable.LinkedMap[A, B1] =
      new WithDefault[A, B1](underlying, x => d)
  }

  private object EmptyMap
      extends AbstractMap[Any, Nothing]
      with LinkedMap[Any, Nothing] {
    override def size: Int = 0
    override def get(key: Any): Option[Nothing] = None
    override def iterator: Iterator[(Any, Nothing)] = Iterator.empty
    override def updated[B1](key: Any, value: B1): LinkedMap[Any, B1] =
      new LinkedMap1(key, value)
    override def +[B1](kv: (Any, B1)): LinkedMap[Any, B1] = updated(kv._1, kv._2)
    override def -(key: Any): LinkedMap[Any, Nothing] = this
  }

  final class LinkedMap1[A, +B](key1: A, value1: B)
      extends AbstractMap[A, B]
      with LinkedMap[A, B]
      with Serializable {
    override def size = 1
    override def get(key: A): Option[B] =
      if (key == key1) Some(value1) else None
    override def iterator = Iterator((key1, value1))
    override def updated[B1 >: B](key: A, value: B1): LinkedMap[A, B1] =
      if (key == key1) new LinkedMap1(key1, value)
      else new LinkedMap2(key1, value1, key, value)
    override def +[B1 >: B](kv: (A, B1)): LinkedMap[A, B1] =
      updated(kv._1, kv._2)
    override def -(key: A): LinkedMap[A, B] =
      if (key == key1) VectorMap.empty else this
    override def foreach[U](f: ((A, B)) => U): Unit = {
      f((key1, value1))
    }
  }

  final class LinkedMap2[A, +B](key1: A, value1: B, key2: A, value2: B)
    extends AbstractMap[A, B]
      with LinkedMap[A, B]
      with Serializable {
    override def size = 2
    override def get(key: A): Option[B] =
      if (key == key1) Some(value1)
      else if (key == key2) Some(value2)
      else None
    override def iterator = Iterator((key1, value1), (key2, value2))
    override def updated[B1 >: B](key: A, value: B1): LinkedMap[A, B1] =
      if (key == key1) new LinkedMap2(key1, value, key2, value2)
      else if (key == key2) new LinkedMap2(key1, value1, key2, value)
      else new LinkedMap3(key1, value1, key2, value2, key, value)
    override def +[B1 >: B](kv: (A, B1)): LinkedMap[A, B1] =
      updated(kv._1, kv._2)
    override def -(key: A): LinkedMap[A, B] =
      if (key == key1) new LinkedMap1(key2, value2)
      else if (key == key2) new LinkedMap1(key1, value1)
      else this
    override def foreach[U](f: ((A, B)) => U): Unit = {
      f((key1, value1)); f((key2, value2))
    }
  }

  final class LinkedMap3[A, +B](key1: A,
                                value1: B,
                                key2: A,
                                value2: B,
                                key3: A,
                                value3: B)
    extends AbstractMap[A, B]
      with LinkedMap[A, B]
      with Serializable {
    override def size = 3
    override def get(key: A): Option[B] =
      if (key == key1) Some(value1)
      else if (key == key2) Some(value2)
      else if (key == key3) Some(value3)
      else None
    override def iterator = Iterator((key1, value1), (key2, value2), (key3, value3))
    override def updated[B1 >: B](key: A, value: B1): LinkedMap[A, B1] =
      if (key == key1) new LinkedMap3(key1, value, key2, value2, key3, value3)
      else if (key == key2)
        new LinkedMap3(key1, value1, key2, value, key3, value3)
      else if (key == key3)
        new LinkedMap3(key1, value1, key2, value2, key3, value)
      else new LinkedMap4(key1, value1, key2, value2, key3, value3, key, value)
    override def +[B1 >: B](kv: (A, B1)): LinkedMap[A, B1] =
      updated(kv._1, kv._2)
    override def -(key: A): LinkedMap[A, B] =
      if (key == key1) new LinkedMap2(key2, value2, key3, value3)
      else if (key == key2) new LinkedMap2(key1, value1, key3, value3)
      else if (key == key3) new LinkedMap2(key1, value1, key2, value2)
      else this
    override def foreach[U](f: ((A, B)) => U): Unit = {
      f((key1, value1)); f((key2, value2)); f((key3, value3))
    }
  }

  final class LinkedMap4[A, +B](key1: A,
                                value1: B,
                                key2: A,
                                value2: B,
                                key3: A,
                                value3: B,
                                key4: A,
                                value4: B)
    extends AbstractMap[A, B]
      with LinkedMap[A, B]
      with Serializable {
    override def size = 4
    override def get(key: A): Option[B] =
      if (key == key1) Some(value1)
      else if (key == key2) Some(value2)
      else if (key == key3) Some(value3)
      else if (key == key4) Some(value4)
      else None
    override def iterator = Iterator((key1, value1), (key2, value2), (key3, value3), (key4, value4))
    override def updated[B1 >: B](key: A, value: B1): LinkedMap[A, B1] =
      if (key == key1)
        new LinkedMap4(key1, value, key2, value2, key3, value3, key4, value4)
      else if (key == key2)
        new LinkedMap4(key1, value1, key2, value, key3, value3, key4, value4)
      else if (key == key3)
        new LinkedMap4(key1, value1, key2, value2, key3, value, key4, value4)
      else if (key == key4)
        new LinkedMap4(key1, value1, key2, value2, key3, value3, key4, value)
      else {
        val builder = VectorMap.newBuilder[A, B1]
        builder.sizeHint(5)

        builder += ((key1, value1))
        builder += ((key2, value2))
        builder += ((key3, value3))
        builder += ((key4, value4))
        builder += ((key, value))

        builder.result()
      }
    @inline override def +[B1 >: B](kv: (A, B1)): LinkedMap[A, B1] =
      updated(kv._1, kv._2)
    override def -(key: A): LinkedMap[A, B] =
      if (key == key1) new LinkedMap3(key2, value2, key3, value3, key4, value4)
      else if (key == key2)
        new LinkedMap3(key1, value1, key3, value3, key4, value4)
      else if (key == key3)
        new LinkedMap3(key1, value1, key2, value2, key4, value4)
      else if (key == key4)
        new LinkedMap3(key1, value1, key2, value2, key3, value3)
      else this
    override def foreach[U](f: ((A, B)) => U): Unit = {
      f((key1, value1)); f((key2, value2)); f((key3, value3)); f((key4, value4))
    }
  }
}
