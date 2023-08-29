package izumi.fundamentals.collections.nonempty

import scala.collection.compat.Factory
import scala.collection.mutable
import scala.annotation.{nowarn, unused}

trait NESetInstances {
  @nowarn("msg=dead code following this construct")
  implicit def factoryNes[A]: Factory[A, NESet[A]] = new Factory[A, NESet[A]] {
    override def apply(@unused from: Nothing): mutable.Builder[A, NESet[A]] = apply()

    override def apply(): mutable.Builder[A, NESet[A]] = new mutable.Builder[A, NESet[A]] {
      private val sub = implicitly[Factory[A, Set[A]]].apply()

      override def +=(elem: A): this.type = {
        sub += elem
        this
      }

      override def clear(): Unit = sub.clear()

      override def result(): NESet[A] = {
        NESet.unsafeFrom(sub.result())
      }
    }
  }

}
