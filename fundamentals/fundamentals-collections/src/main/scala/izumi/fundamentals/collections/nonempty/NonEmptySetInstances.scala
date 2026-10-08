package izumi.fundamentals.collections.nonempty

import scala.collection.Factory
import scala.collection.mutable

trait NESetInstances {
  implicit def factoryNes[A]: Factory[A, NESet[A]] = new Factory[A, NESet[A]] {
    override def fromSpecific(it: IterableOnce[A]): NESet[A] = NESet.unsafeFrom(it.iterator.toSet)

    override def newBuilder: mutable.Builder[A, NESet[A]] =
      implicitly[Factory[A, Set[A]]].newBuilder.mapResult(NESet.unsafeFrom[A])
  }

}
