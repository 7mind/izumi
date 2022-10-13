package izumi.fundamentals.json.circe

import io.circe.{Codec, Decoder, Encoder}

import scala.deriving.Mirror

final case class DerivationDerivedEncoder[A](value: Encoder.AsObject[A]) extends AnyVal
object DerivationDerivedEncoder {
  inline implicit def materialize[A: Mirror.Of]: DerivationDerivedEncoder[A] = DerivationDerivedEncoder(
    io.circe.generic.auto.deriveEncoder[A].instance
  )
}

final case class DerivationDerivedDecoder[A](value: Decoder[A]) extends AnyVal
object DerivationDerivedDecoder {
  inline implicit def materialize[A: Mirror.Of]: DerivationDerivedDecoder[A] = DerivationDerivedDecoder(
    io.circe.generic.auto.deriveDecoder[A].instance
  )
}

final case class DerivationDerivedCodec[A](value: Codec.AsObject[A]) extends AnyVal
object DerivationDerivedCodec {
  inline implicit def materialize[A: Mirror.Of]: DerivationDerivedCodec[A] = DerivationDerivedCodec(
    Codec.AsObject.from(
      io.circe.generic.auto.deriveDecoder[A].instance,
      io.circe.generic.auto.deriveEncoder[A].instance,
    )
  )
}
