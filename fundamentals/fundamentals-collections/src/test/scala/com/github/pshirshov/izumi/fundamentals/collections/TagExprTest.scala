package com.github.pshirshov.izumi.fundamentals.collections

import com.github.pshirshov.izumi.fundamentals.tags.TagExpr
import org.scalatest.WordSpec

class TagExprTest extends WordSpec {

  "Tag expression evaluator" should {
    "support boolean operations" in {
      assert(TagExpr.Strings.any("a", "b").evaluate(Set("a")))
      assert(TagExpr.Strings.any("a", "b").evaluate(Set("a", "b")))
      assert(TagExpr.Strings.any("a", "b").evaluate(Set("a", "c")))
      assert(TagExpr.Strings.any("a", "b").evaluate(Set("a", "b", "c")))
      assert(!TagExpr.Strings.any("a", "b").evaluate(Set("d")))

      assert(!TagExpr.Strings.all("a", "b").evaluate(Set("a")))
      assert(TagExpr.Strings.all("a", "b").evaluate(Set("a", "b")))
      assert(!TagExpr.Strings.all("a", "b").evaluate(Set("a", "c")))
      assert(TagExpr.Strings.all("a", "b").evaluate(Set("a", "b", "c")))
      assert(!TagExpr.Strings.all("a", "b").evaluate(Set("d")))


      assert(TagExpr.Strings.one("a", "b").evaluate(Set("a")))
      assert(!TagExpr.Strings.one("a", "b").evaluate(Set("a", "b")))
      assert(TagExpr.Strings.one("a", "b").evaluate(Set("a", "c")))
      assert(!TagExpr.Strings.one("a", "b").evaluate(Set("a", "b", "c")))
      assert(!TagExpr.Strings.one("a", "b").evaluate(Set("d")))
      assert(TagExpr.Strings.Not(TagExpr.Strings.one("a", "b")).evaluate(Set("d")))
    }
  }


}
