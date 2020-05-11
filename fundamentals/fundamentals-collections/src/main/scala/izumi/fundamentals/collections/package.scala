package izumi.fundamentals

import com.github.ghik.silencer.silent
import scala.collection.mutable

package object collections {
  @silent("msg=deprecated")
  type MutableMultiMap[A, B] = mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B]
  type ImmutableMultiMap[A, B] = Map[A, Set[B]]
}
