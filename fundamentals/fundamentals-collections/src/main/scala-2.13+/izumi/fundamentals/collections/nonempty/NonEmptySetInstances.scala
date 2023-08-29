package izumi.fundamentals.collections.nonempty

import scala.collection.compat.Factory
import scala.collection.mutable

trait NESetInstances {
  implicit def factoryNes[A]: Factory[A, NESet[A]] = new Factory[A, NESet[A]] {
    override def fromSpecific(it: IterableOnce[A]): NESet[A] = NESet.unsafeFrom(it.iterator.toSet)

    override def newBuilder: mutable.Builder[A, NESet[A]] = {
      new mutable.Builder[A, NESet[A]] {

        private val sub = implicitly[Factory[A, Set[A]]].newBuilder

        override def clear(): Unit = sub.clear()

        override def result(): NESet[A] = {
          NESet.unsafeFrom(sub.result())
        }

        override def addOne(elem: A): this.type = {
          sub.addOne(elem)
          this
        }
      }
    }
  }

}
