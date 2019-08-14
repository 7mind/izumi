package com.github.pshirshov.izumi.fundamentals.platform

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzEscape
import org.scalatest.WordSpec

class IzStringEscapeTest extends WordSpec {

  "string escape tool" should {
    "keep identity" in {
      val e = new IzEscape(Set('.', '[', ']'), '\\')

      def test(s: String) = {
        assert(s == e.unescape(e.escape(s)))

      }

      test("a.[\\1].b")
      test("...")
      test("abc")
    }
  }


}
