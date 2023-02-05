package izumi.fundamentals.json.circe

import io.circe.{Codec, Decoder, Encoder}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

// TODO: merge upstream, also with @JsonCodec
final class MaterializeDerivationMacros(val c: blackbox.Context) {
  import c.universe.*

  def materializeEncoderImpl[A: c.WeakTypeTag]: c.Expr[DerivationDerivedEncoder[A]] =
    c.Expr[DerivationDerivedEncoder[A]] {
      q"""{
           new ${weakTypeOf[DerivationDerivedEncoder[A]]}(_root_.io.circe.derivation.deriveEncoder[${weakTypeOf[A]}])
         }"""
    }

  def materializeDecoderImpl[A: c.WeakTypeTag]: c.Expr[DerivationDerivedDecoder[A]] =
    c.Expr[DerivationDerivedDecoder[A]] {
      q"""{
           new ${weakTypeOf[DerivationDerivedDecoder[A]]}(_root_.io.circe.derivation.deriveDecoder[${weakTypeOf[A]}])
         }"""
    }

  def materializeCodecImpl[A: c.WeakTypeTag]: c.Expr[DerivationDerivedCodec[A]] =
    c.Expr[DerivationDerivedCodec[A]] {
      q"""{
           new ${weakTypeOf[DerivationDerivedCodec[A]]}(_root_.io.circe.derivation.deriveCodec[${weakTypeOf[A]}])
         }"""
    }
}

final case class DerivationDerivedEncoder[A](value: Encoder.AsObject[A]) extends AnyVal
object DerivationDerivedEncoder {
  implicit def materialize[A]: DerivationDerivedEncoder[A] = macro MaterializeDerivationMacros.materializeEncoderImpl[A]
}

final case class DerivationDerivedDecoder[A](value: Decoder[A]) extends AnyVal
object DerivationDerivedDecoder {
  implicit def materialize[A]: DerivationDerivedDecoder[A] = macro MaterializeDerivationMacros.materializeDecoderImpl[A]
}

final case class DerivationDerivedCodec[A](value: Codec.AsObject[A]) extends AnyVal
object DerivationDerivedCodec {
  implicit def materialize[A]: DerivationDerivedCodec[A] = macro MaterializeDerivationMacros.materializeCodecImpl[A]
}
