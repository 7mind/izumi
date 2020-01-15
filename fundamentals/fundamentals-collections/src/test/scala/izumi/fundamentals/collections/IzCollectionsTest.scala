
package izumi.fundamentals.collections

import izumi.fundamentals.collections.IzCollections._
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

class IzCollectionsTest extends AnyWordSpec {
  "Collection utils" should {
    "allow to convert mappings to multimaps" in {
      assert(List("a" -> 1, "a" -> 2).toMultimap == Map("a" -> Set(1, 2)))
      assert(List("a" -> 1, "a" -> 2).toMutableMultimap == mutable.Map("a" -> mutable.Set(1, 2)))
    }

    "support .distinctBy operation" in {
      assert(1.to(20).distinctBy(_ % 5) == 1.to(5))
    }
  }
}


