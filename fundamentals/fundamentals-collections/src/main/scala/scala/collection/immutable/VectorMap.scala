/*
* https://github.com/mdedetrich/linked-map
* */
package scala.collection.immutable

import scala.collection.{CustomParallelizable, GenTraversableOnce}
import scala.collection.generic.{CanBuildFrom, ImmutableMapFactory}
import scala.collection.parallel.immutable.ParHashMap
import scala.collection.mutable

/** This class implements immutable maps using a vector/map-based data structure, which preserves insertion order.
  *  Instances of `VectorMap` represent empty maps; they can be either created by
  *  calling the constructor directly, or by applying the function `VectorMap.empty`.
  *
  *  Unlike `ListMap`, `VectorMap` has amortized effectively constant lookup at the expense
  *  of using extra memory
  * @author Matthew de Detrich
  * @tparam A
  * @tparam B
  */
@SerialVersionUID(1858299024439116764L)
@deprecatedInheritance(
  "The semantics of immutable collections makes inheriting from VectorMap error-prone.")
class VectorMap[A, +B](private val fields: Vector[A] = Vector.empty[A],
                       private val underlying: HashMap[A, (Int, B)] = HashMap.empty[A, (Int, B)])
    extends AbstractMap[A, B]
    with LinkedMap[A, B]
    with Map[A, B]
    with MapLike[A, B, VectorMap[A, B]]
    with Serializable
    with CustomParallelizable[(A, B), ParHashMap[A, B]] {

  @inline override def default(key: A): B =
    throw new NoSuchElementException("key not found: " + key)

  @inline override def empty: VectorMap[A, Nothing] = VectorMap.empty

  override def +[B1 >: B](kv: (A, B1)): VectorMap[A, B1] = {
    if (underlying.get(kv._1).isDefined) {
      new VectorMap(
        fields,
        underlying.updated(kv._1, (fields.length, kv._2.asInstanceOf[B])))
    } else {
      new VectorMap(
        fields :+ kv._1,
        underlying.updated(kv._1, (fields.length + 1, kv._2.asInstanceOf[B])))
    }
  }

  override def + [B1 >: B] (elem1: (A, B1), elem2: (A, B1), elems: (A, B1) *): VectorMap[A, B1] =
    this + elem1 + elem2 ++ elems

  override def ++[B1 >: B](
      xs: GenTraversableOnce[(A, B1)]): VectorMap[A, B1] = {
    val fieldsBuilder = Vector.newBuilder[A]
    val mapBuilder = HashMap.newBuilder[A, (Int, B)]
    val originalFieldsLength = fields.length
    var newFieldsCounter = 0
    xs.foreach { value =>
      if (underlying.get(value._1).isEmpty) {
        newFieldsCounter += 1
        fieldsBuilder += value._1
        mapBuilder += ((value._1,
                        (originalFieldsLength + newFieldsCounter,
                         value._2.asInstanceOf[B])))
      } else {
        val oldCounter = underlying(value._1)._1
        mapBuilder += ((value._1, (oldCounter, value._2.asInstanceOf[B])))
      }

    }
    new VectorMap(fields ++ fieldsBuilder.result(),
                  underlying ++ mapBuilder.result())
  }

  override def get(key: A): Option[B] = underlying.get(key).map(_._2)

  override def getOrElse[B1 >: B](key: A, default: => B1): B1 =
    underlying.get(key).map(_._2.asInstanceOf[B]).getOrElse(default)

  override def iterator: Iterator[(A, B)] = new Iterator[(A, B)] {
    private val fieldsIterator = fields.iterator

    override def hasNext: Boolean = fieldsIterator.hasNext

    override def next(): (A, B) = {
      val field = fieldsIterator.next()
      (field, underlying(field)._2)
    }
  }

  override def -(key: A): VectorMap[A, B] = {
    underlying.get(key) match {
      case Some((index, _)) =>
        new VectorMap(fields.patch(index, Nil, 1), underlying - key)
      case _ =>
        this
    }
  }

  @inline override def keys: scala.Iterable[A] = fields.iterator.toIterable

  override def values: scala.Iterable[B] = new Iterable[B] {
    override def iterator: Iterator[B] = {
      new Iterator[B] {
        private val fieldsIterator = fields.iterator

        override def hasNext: Boolean = fieldsIterator.hasNext

        override def next(): B = {
          val field = fieldsIterator.next()
          underlying(field)._2
        }
      }
    }
  }

  @inline override def isEmpty: Boolean = fields.isEmpty

  @inline override def size: Int = fields.size

  override def apply(key: A): B = get(key) match {
    case None        => default(key)
    case Some(value) => value
  }

  override def updated[B1 >: B](key: A, value: B1): VectorMap[A, B1] = {
    if (underlying.get(key).isDefined) {
      val oldKey = underlying(key)._1
      new VectorMap(fields,
                    underlying.updated(key, (oldKey, value.asInstanceOf[B])))
    } else {
      new VectorMap(
        fields :+ key,
        underlying.updated(key, (fields.length + 1, value.asInstanceOf[B])))
    }
  }

  override def equals(that: Any): Boolean = {
    that match {
      case vectorMap: VectorMap[A, B] =>
        vectorMap.underlying.equals(this.underlying) && vectorMap.fields
          .equals(this.fields)
      case _ => false
    }
  }

  override def hashCode(): Int = {
    (31 * underlying.hashCode()) + (31 * fields.hashCode())
  }
}

object VectorMap extends ImmutableMapFactory[VectorMap] {
  implicit def canBuildFrom[A, B]: CanBuildFrom[Coll, (A, B), LinkedMap[A, B]] =
    new MapCanBuildFrom[A, B]

  @inline override def empty[A, B]: VectorMap[A, B] =
    VectorMap.empty

  @inline override def newBuilder[A, B]: mutable.Builder[(A, B), VectorMap[A, B]] =
    new VectorMapBuilder
}

class VectorMapBuilder[A, B] extends mutable.Builder[(A, B), VectorMap[A, B]] {
  private val fieldBuilder = Vector.newBuilder[A]
  private val underlyingBuilder = HashMap.newBuilder[A, (Int, B)]
  private var counter = 0

  override def +=(elem: (A, B)): VectorMapBuilder.this.type = {
    underlyingBuilder += ((elem._1, (counter, elem._2)))
    counter += 1
    fieldBuilder += elem._1
    this
  }

  override def clear(): Unit = {
    underlyingBuilder.clear()
    fieldBuilder.clear()
    counter = 0
  }

  override def result(): VectorMap[A, B] = {
    new VectorMap(fieldBuilder.result(), underlyingBuilder.result())
  }
}
