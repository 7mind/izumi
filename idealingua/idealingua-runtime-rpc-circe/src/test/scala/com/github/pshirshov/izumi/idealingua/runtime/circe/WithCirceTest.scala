package com.github.pshirshov.izumi.idealingua.runtime.circe

import io.circe.JsonObject
import io.circe.syntax._
import org.scalatest.WordSpec
import shapeless.{Cached, Lazy}

class WithCirceTest extends WordSpec {
  import WithCirceTest._

  "WithCirce" should {
    "WithCirceGeneric works and is cached" in {
      import TestCaseGeneric._

      assert(Abc(1, 2).asJson.as[Abc].right.get == Abc(1, 2))
      assert(Abc(1, 2).asJson.noSpaces == """{"a":1,"b":2}""")

      {
        import Alt._
        implicit val _ = enc
        assert(Abc(1, 2).asJson.as[Abc].right.get == Abc(1, 2)) // old instance is picked up anyway due to aching, ignoring the one we just defined
      }

      assertThrows[NotImplementedError] {
        import Alt._
        Abc(1, 2).asJson(Abc.enc(Cached(enc))).as[Abc].right.get == Abc(1, 2)
      }

    }

    "WithCirce works" in {
      import TestCaseDerivation._

      assert(Cba(1, 2).asJson.as[Cba].right.get == Cba(1, 2))
      assert(Cba(1, 2).asJson.noSpaces == """{"a":1,"b":2}""")
    }
  }

}

object WithCirceTest {

  object TestCaseGeneric {
    final case class Abc(a: Int, b: Int)
    object Abc extends IRTWithCirceGeneric[Abc]

    object Alt {
      import io.circe.generic.encoding.DerivedObjectEncoder
      // Not DerivedObjectEncoder directly because DerivedObjectEncoder <: Encoder, so when it's in scope it gets picked up
      // instead of implicit def instance in WithCirceGeneric
      implicit val enc: Lazy[DerivedObjectEncoder[Abc]] = Lazy(new DerivedObjectEncoder[Abc] {
        override def encodeObject(a: Abc): JsonObject = throw new RuntimeException("panic")
      })
    }
  }

  object TestCaseDerivation {
    final case class Cba(a: Int, b: Int)
    object Cba extends IRTWithCirce[Cba]
  }

}

