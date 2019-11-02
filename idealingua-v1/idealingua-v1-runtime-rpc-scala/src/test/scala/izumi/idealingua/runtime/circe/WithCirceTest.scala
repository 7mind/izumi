package izumi.idealingua.runtime.circe

import io.circe
import io.circe.Codec
import io.circe.syntax._
import izumi.idealingua.runtime.circe.WithCirceTest.{Cba, Enum, Enum1, Enum2, Nested, Sealed}
import org.scalatest.WordSpec

final class WithCirceTest extends WordSpec {

  "WithCirce" should {
    "WithCirce works with case classes" in {
      assert(Cba(1, 2).asJson.as[Cba].right.get == Cba(1, 2))
      assert(Cba(1, 2).asJson.noSpaces == """{"a":1,"b":2}""")
    }

    "WithCirce works with (non-nested) sealed traits" in {
      assert(Sealed(Cba(1, 2)).asJson.as[Sealed].right.get == Sealed(Cba(1, 2)))
      assert(Sealed(Cba(1, 2)).asJson.noSpaces == """{"Sealed1":{"cba":{"a":1,"b":2}}}""")
    }

    // workaround https://github.com/milessabin/shapeless/issues/837
    "WithCirce works with nested sealed traits via delegation" in {
      assert(Nested(Cba(1, 2)).asJson.as[Nested].right.get == Nested(Cba(1, 2)))
      assert(Nested(Cba(1, 2)).asJson.noSpaces == """{"Nested1":{"cba":{"a":1,"b":2}}}""")
    }

    "WithCirce does not encode case objects as strings (circe-generic-extras deriveEnumCodec is still required)" in {
      assert((Enum1: Enum).asJson.as[Enum].right.get == Enum1)
      assert((Enum2: Enum).asJson.noSpaces != """"Enum2"""")
      assert((Enum2: Enum).asJson.noSpaces == """{"Enum2":{}}""")
    }
  }

}

object WithCirceTest {

  final case class Cba(a: Int, b: Int)
  object Cba extends IRTWithCirce[Cba]

  sealed trait Sealed
  object Sealed extends IRTWithCirce[Sealed] {
    def apply(cba: Cba): Sealed = Sealed1(cba)
  }

  final case class Sealed1(cba: Cba) extends Sealed
  object Sealed1 extends IRTWithCirce[Sealed1]

  sealed trait Nested
  object Nested extends IRTWithCirce[Nested](codecs) {
    def apply(cba: Cba): Nested = Nested1(cba)

    final case class Nested1(cba: Cba) extends Nested
    object Nested1 extends IRTWithCirce[Nested1]
  }

  // workaround https://github.com/milessabin/shapeless/issues/837
  private[this] object codecs extends IRTWithCirce[Nested]

  sealed trait Enum
  object Enum extends IRTWithCirce[Enum]
  case object Enum1 extends Enum {
    implicit val codec: Codec.AsObject[Enum1.type] = circe.derivation.deriveCodec[Enum1.type]
  }
  case object Enum2 extends Enum {
    implicit val codec: Codec.AsObject[Enum2.type] = circe.derivation.deriveCodec[Enum2.type]
  }
}

