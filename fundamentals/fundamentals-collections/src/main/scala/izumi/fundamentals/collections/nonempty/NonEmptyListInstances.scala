package izumi.fundamentals.collections.nonempty

import scala.collection.Factory
import scala.collection.mutable

trait NEListInstances {
  implicit def factoryNel[A]: Factory[A, NEList[A]] = new Factory[A, NEList[A]] {
    override def fromSpecific(it: IterableOnce[A]): NEList[A] = NEList.unsafeFrom(it.iterator.toList)

    override def newBuilder: mutable.Builder[A, NEList[A]] =
      implicitly[Factory[A, List[A]]].newBuilder.mapResult(NEList.unsafeFrom[A])
  }
}

