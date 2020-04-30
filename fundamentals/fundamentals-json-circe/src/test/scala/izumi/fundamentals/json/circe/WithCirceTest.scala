package izumi.fundamentals.json.circe

import io.circe
import io.circe.{Codec, Decoder, Encoder}
import io.circe.syntax._
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

abstract class deriving[C <: Desc](implicit c: C { type I1 = C#I1; type I2 = C#I2 }, i10: C#I1, i20: C#I2) {
  implicit val i1: C#I1R = c.extractI1(i10)
  implicit val i2: C#I2R = c.extractI2(i20)
}
final class JustDecoder[A] extends Desc {
  override type I1 = DerivationDerivedDecoder[A]
  override type I1R = Decoder[A]
  override type I2 = Dummy.type
  override type I2R = Dummy.type

  override def extractI1(i1: DerivationDerivedDecoder[A]): Decoder[A] = i1.value
  override def extractI2(i2: Dummy.type): Dummy.type = i2
}
object JustDecoder {
  implicit def x[A]: JustDecoder[A] = new JustDecoder[A]
}
final class DecoderEncoder[A] extends Desc {
  override type I1 = DerivationDerivedDecoder[A]
  override type I1R = Decoder[A]
  override type I2 = DerivationDerivedEncoder[A]
  override type I2R = Encoder.AsObject[A]

  override def extractI1(i1: DerivationDerivedDecoder[A]): Decoder[A] = i1.value
  override def extractI2(i2: DerivationDerivedEncoder[A]): Encoder.AsObject[A] = i2.value
}
object DecoderEncoder {
  implicit def x[A]: DecoderEncoder[A] = new DecoderEncoder[A]
}

object WithCirceTest {

  final case class Cba(a: Int, b: Int)
  object Cba extends WithCirce[Cba]

  final case class Cbaa(a: Int, b: Int)
  object Cbaa extends deriving[JustDecoder[Cbaa]]

  final case class Cbaaa(a: Int, b: Int)
  object Cbaaa extends deriving[DecoderEncoder[Cbaa]]

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
  private[this] object codecs extends WithCirce[Nested]

  sealed trait Enum
  object Enum extends WithCirce[Enum]
  case object Enum1 extends Enum {
    implicit val codec: Codec.AsObject[Enum1.type] = circe.derivation.deriveCodec[Enum1.type]
  }
  case object Enum2 extends Enum {
    implicit val codec: Codec.AsObject[Enum2.type] = circe.derivation.deriveCodec[Enum2.type]
  }
}

