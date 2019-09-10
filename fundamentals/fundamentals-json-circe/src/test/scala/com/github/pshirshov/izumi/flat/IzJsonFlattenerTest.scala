
package com.github.pshirshov.izumi.flat

import com.github.pshirshov.izumi.json.flat.JsonFlattener
import io.circe.Json
import io.circe.literal._
import org.scalatest.{Assertion, WordSpec}

class IzJsonFlattenerTest extends WordSpec {

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
    val flattened = new JsonFlattener().flatten(original)
    val inflated = new JsonFlattener().inflate(flattened)
    assert(inflated.contains(original))
  }
}

