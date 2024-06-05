package izumi.fundamentals.json.circe

import io.circe
import io.circe.syntax.*
import io.circe.Codec
import izumi.fundamentals.json.circe.WithCirceTest.{Cba, Enum, Enum1, Enum2, Nested, Sealed}
import org.scalatest.wordspec.AnyWordSpec

final class WithCirceTest extends AnyWordSpec {

  "WithCirce" should {
    "WithCirce works with case classes" in {
      assert(Cba(1, 2).asJson.as[Cba].toOption.get == Cba(1, 2))
      assert(Cba(1, 2).asJson.noSpaces == """{"a":1,"b":2}""")
    }

    "WithCirce works with (non-nested) sealed traits" in {
      assert(Sealed(Cba(1, 2)).asJson.as[Sealed].toOption.get == Sealed(Cba(1, 2)))
      assert(Sealed(Cba(1, 2)).asJson.noSpaces == """{"Sealed1":{"cba":{"a":1,"b":2}}}""")
    }

    // workaround https://github.com/milessabin/shapeless/issues/837
    "WithCirce works with nested sealed traits via delegation" in {
      assert(Nested(Cba(1, 2)).asJson.as[Nested].toOption.get == Nested(Cba(1, 2)))
      assert(Nested(Cba(1, 2)).asJson.noSpaces == """{"Nested1":{"cba":{"a":1,"b":2}}}""")
    }

    "progression test: WithCirce DOES NOT encode case objects as strings (circe-generic-extras deriveEnumCodec is still required)" in {
      assert((Enum1: Enum).asJson.as[Enum].toOption.get == Enum1)
      assert((Enum2: Enum).asJson.noSpaces != """"Enum2"""")
      assert((Enum2: Enum).asJson.noSpaces == """{"Enum2":{}}""")
    }

    "derivations for encoder/decoder" in {
      final case class Abc(a: Int)
      implicitly[DerivationDerivedEncoder[Abc]]
      implicitly[DerivationDerivedDecoder[Abc]]
      implicitly[DerivationDerivedCodec[Abc]]
    }
  }

}

trait Desc {
  type I1
  type I1R
  type I2
  type I2R

  def extractI1(i1: I1): I1R
  def extractI2(i2: I2): I2R
}

object Dummy {
  implicit def dummy: Dummy.type = this
}

object WithCirceTest {

  final case class Cba(a: Int, b: Int)
  object Cba extends WithCirce[Cba]

  sealed trait Sealed
  object Sealed extends WithCirce[Sealed] {
    def apply(cba: Cba): Sealed = Sealed1(cba)
  }

  final case class Sealed1(cba: Cba) extends Sealed
  object Sealed1 extends WithCirce[Sealed1]

  sealed trait Nested
  object Nested extends WithCirce[Nested](codecs) {
    def apply(cba: Cba): Nested = Nested1(cba)

    final case class Nested1(cba: Cba) extends Nested
    object Nested1 extends WithCirce[Nested1]
  }

  // workaround https://github.com/milessabin/shapeless/issues/837
  private object codecs extends WithCirce[Nested]

  sealed trait Enum
  case object Enum1 extends Enum {
    implicit val codec: Codec.AsObject[Enum1.type] = circe.generic.semiauto.deriveCodec[Enum1.type]
  }
  case object Enum2 extends Enum {
    implicit val codec: Codec.AsObject[Enum2.type] = circe.generic.semiauto.deriveCodec[Enum2.type]
  }
  object Enum extends WithCirce[Enum]
}
