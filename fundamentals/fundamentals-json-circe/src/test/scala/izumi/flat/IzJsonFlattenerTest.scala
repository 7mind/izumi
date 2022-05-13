package izumi.flat

import io.circe.Json
import io.circe.literal._
import izumi.fundamentals.json.flat.JsonFlattener
import org.scalatest.Assertion
import org.scalatest.wordspec.AnyWordSpec

class IzJsonFlattenerTest extends AnyWordSpec {

  "JSON flattener" should {
    "keep identity" in {
      val original = json"""{"a": [1,2, {"x.x": "y", "y": 123}], "b": {"c": "d"}}"""
      check(original)
    }

    "support empty arrays" in {
      val original = json"""{"a": [], "b": {}, "c": 1}"""
      check(original)
    }
  }

  private def check(original: Json): Assertion = {
    val flattened = new JsonFlattener.flatten(original)
    val inflated = new JsonFlattener.inflate(flattened)
    assert(inflated.contains(original))
  }
}
