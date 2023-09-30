package izumi.fundamentals.collections.nonempty

import scala.annotation.{nowarn, unused}
import scala.collection.compat.Factory
import scala.collection.mutable

trait NEListInstances {
  @nowarn("msg=dead code following this construct")
  implicit def factoryNel[A]: Factory[A, NEList[A]] = new Factory[A, NEList[A]] {
    override def apply(@unused from: Nothing): mutable.Builder[A, NEList[A]] = apply()

    override def apply(): mutable.Builder[A, NEList[A]] = new mutable.Builder[A, NEList[A]] {
      private val sub = implicitly[Factory[A, List[A]]].apply()

      override def +=(elem: A): this.type = {
        sub += elem
        this
      }

      override def clear(): Unit = sub.clear()

      override def result(): NEList[A] = {
        NEList.unsafeFrom(sub.result())
      }
    }
  }
}
