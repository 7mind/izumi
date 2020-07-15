package izumi.fundamentals.platform

import izumi.fundamentals.platform.strings.IzEscape
import org.scalatest.wordspec.AnyWordSpec

class IzStringEscapeTest extends AnyWordSpec {

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
