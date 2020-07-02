package izumi.fundamentals

import scala.annotation.nowarn
import scala.collection.mutable

package object collections {
  @nowarn("msg=deprecated")
  type MutableMultiMap[A, B] = mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B]
  type ImmutableMultiMap[A, B] = Map[A, Set[B]]
}
