package izumi.fundamentals.json.circe

import io.circe.{Codec, Decoder, Encoder}

import scala.quoted.{Expr, Quotes, Type}

final case class DerivationDerivedEncoder[A](value: Encoder.AsObject[A]) extends AnyVal
object DerivationDerivedEncoder {
  inline implicit def materialize[A]: DerivationDerivedEncoder[A] = ${ doMaterialize }
  private def doMaterialize[A](using Quotes): Expr[DerivationDerivedEncoder[A]] = ???

}

final case class DerivationDerivedDecoder[A](value: Decoder[A]) extends AnyVal
object DerivationDerivedDecoder {
  inline implicit def materialize[A]: DerivationDerivedDecoder[A] = ${ doMaterialize }
  private def doMaterialize[A](using Quotes): Expr[DerivationDerivedDecoder[A]] = ???

}

final case class DerivationDerivedCodec[A](value: Codec.AsObject[A]) extends AnyVal
object DerivationDerivedCodec {
  inline implicit def materialize[A]: DerivationDerivedCodec[A] = ${ doMaterialize }
  private def doMaterialize[A](using Quotes): Expr[DerivationDerivedCodec[A]] = ???
}
