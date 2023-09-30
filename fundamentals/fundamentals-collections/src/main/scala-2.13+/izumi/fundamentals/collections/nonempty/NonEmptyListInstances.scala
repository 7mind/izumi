package izumi.fundamentals.collections.nonempty

import scala.collection.compat.Factory
import scala.collection.mutable

trait NEListInstances {
  implicit def factoryNel[A]: Factory[A, NEList[A]] = new Factory[A, NEList[A]] {
    override def fromSpecific(it: IterableOnce[A]): NEList[A] = NEList.unsafeFrom(it.iterator.toList)

    override def newBuilder: mutable.Builder[A, NEList[A]] = {
      new mutable.Builder[A, NEList[A]] {

        private val sub = implicitly[Factory[A, List[A]]].newBuilder

        override def clear(): Unit = sub.clear()

        override def result(): NEList[A] = {
          NEList.unsafeFrom(sub.result())
        }

        override def addOne(elem: A): this.type = {
          sub.addOne(elem)
          this
        }
      }
    }
  }
}


