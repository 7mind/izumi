package izumi.fundamentals.collections

import izumi.fundamentals.collections.nonempty.{NEList, NEMap, NESet, NEString}
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.nowarn

@nowarn("msg=Unused import")
class NonEmptyCollectionsTest extends AnyWordSpec {

  import scala.collection.compat._

  "NEList" should {
    "maintain base contracts" in {
      val nl = NEList(1)
      assert(nl.to(List) == List(1))
      assert(nl.head == 1)
      assert(nl.last == 1)
    }
  }

  "NESet" should {
    "maintain base contracts" in {
      val nl = NESet(1, 1)
      assert(nl.to(Set) == Set(1))
      assert(nl.head == 1)
      assert(nl.last == 1)
    }
  }

  "NEString" should {
    "maintain base contracts" in {
      assert(NEString.from("").isEmpty)
      assert(NEString.from("abc").exists(_.theString == "abc"))
    }
  }

  "NEMap" should {
    "maintain base contracts" in {
      assert(NEMap((1, "x")).toMap == Map((1, "x")))
      assert(NEMap.from[Nothing, Nothing](Map.empty).isEmpty)
    }
  }

}
