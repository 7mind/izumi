package izumi.fundamentals.collections

import izumi.fundamentals.collections.nonempty.{NonEmptyList, NonEmptyMap, NonEmptySet, NonEmptyString}
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.nowarn

@nowarn("msg=Unused import")
class NonEmptyCollectionsTest extends AnyWordSpec {

  import scala.collection.compat._

  "NonEmptyList" should {
    "maintain base contracts" in {
      val nl = NonEmptyList(1)
      assert(nl.to(List) == List(1))
      assert(nl.head == 1)
      assert(nl.last == 1)
    }
  }

  "NonEmptySet" should {
    "maintain base contracts" in {
      val nl = NonEmptySet(1, 1)
      assert(nl.to(Set) == Set(1))
      assert(nl.head == 1)
      assert(nl.last == 1)
    }
  }

  "NonEmptyString" should {
    "maintain base contracts" in {
      assert(NonEmptyString.from("").isEmpty)
      assert(NonEmptyString.from("abc").exists(_.theString == "abc"))
    }
  }

  "NonEmptyMap" should {
    "maintain base contracts" in {
      assert(NonEmptyMap((1, "x")).toMap == Map((1, "x")))
      assert(NonEmptyMap.from[Nothing, Nothing](Map.empty).isEmpty)
    }
  }

}
