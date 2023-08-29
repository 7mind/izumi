package izumi.fundamentals.collections.nonempty

import scala.collection.compat.Factory
import scala.collection.mutable

trait NonEmptyListInstances {
  implicit def factoryNel[A]: Factory[A, NonEmptyList[A]] = new Factory[A, NonEmptyList[A]] {
    override def fromSpecific(it: IterableOnce[A]): NonEmptyList[A] = NonEmptyList.unsafeFrom(it.iterator.toList)

    override def newBuilder: mutable.Builder[A, NonEmptyList[A]] = {
      new mutable.Builder[A, NonEmptyList[A]] {

        private val sub = implicitly[Factory[A, List[A]]].newBuilder

        override def clear(): Unit = sub.clear()

        override def result(): NonEmptyList[A] = {
          NonEmptyList.unsafeFrom(sub.result())
        }

        override def addOne(elem: A): this.type = {
          sub.addOne(elem)
          this
        }
      }
    }
  }
}


