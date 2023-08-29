package izumi.fundamentals.collections.nonempty

import scala.collection.compat.Factory
import scala.collection.mutable

trait NonEmptySetInstances {
  implicit def factoryNes[A]: Factory[A, NonEmptySet[A]] = new Factory[A, NonEmptySet[A]] {
    override def fromSpecific(it: IterableOnce[A]): NonEmptySet[A] = NonEmptySet.unsafeFrom(it.iterator.toSet)

    override def newBuilder: mutable.Builder[A, NonEmptySet[A]] = {
      new mutable.Builder[A, NonEmptySet[A]] {

        private val sub = implicitly[Factory[A, Set[A]]].newBuilder

        override def clear(): Unit = sub.clear()

        override def result(): NonEmptySet[A] = {
          NonEmptySet.unsafeFrom(sub.result())
        }

        override def addOne(elem: A): this.type = {
          sub.addOne(elem)
          this
        }
      }
    }
  }

}
