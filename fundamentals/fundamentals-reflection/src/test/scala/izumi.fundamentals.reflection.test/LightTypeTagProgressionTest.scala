package izumi.fundamentals.reflection.test

import izumi.fundamentals.reflection.macrortti.LTT
import org.scalatest.exceptions.TestFailedException

class LightTypeTagProgressionTest extends TagAssertions {

  import TestModel._

  "lightweight type tags" should {
    "progression test: can't support subtyping of type prefixes" in {
      val a = new C {}

      intercept[TestFailedException] {
        assertChild(LTT[a.A], LTT[C#A])
      }
    }

    "progression test: can't support subtyping of concrete type projections" in {
      trait A {

        trait T

      }
      trait B extends A

      val tagA = LTT[A#T]
      val tagB = LTT[B#T]
      assertSame(LTT[A#T], LTT[A#T])
      assertDifferent(LTT[B#T], LTT[A#T])
      intercept[TestFailedException] {
        assertChild(tagB, tagA)
      }
    }

    "progression test: wildcards are not supported" in {
      intercept[TestFailedException] {
        assertChild(LTT[Set[Int]], LTT[Set[_]])
      }
    }

    "progression test: subtype check fails when child type has absorbed a covariant type parameter of the supertype" in {
      assertChild(LTT[Set[Int]], LTT[Iterable[AnyVal]])

      val tagF3 = LTT[F3]
      assertChild(tagF3, LTT[F2[Int]])

      intercept[TestFailedException] {
        assertChild(tagF3, LTT[F2[AnyVal]])
      }
    }

  }
}
