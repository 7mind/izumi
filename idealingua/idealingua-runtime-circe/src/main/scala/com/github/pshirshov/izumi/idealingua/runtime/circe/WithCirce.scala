package com.github.pshirshov.izumi.idealingua.runtime.circe

import io.circe.{Decoder, Encoder, ObjectEncoder, derivation}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Provides circe codecs for case classes (won't work with sealed traits):
 *
 *   case class Abc(a: String, b: String, c: String)
 *
 *   object Abc extends WithCirce[Abc]
 *
 */
abstract class WithCirce[A: DerivationDerivedEncoder: DerivationDerivedDecoder] {
  implicit val enc: Encoder[A] = implicitly[DerivationDerivedEncoder[A]].value
  implicit val dec: Decoder[A] = implicitly[DerivationDerivedDecoder[A]].value
}

// TODO: merge upstream, also with @JsonCodec
class MaterializeDerivationMacros(override val c: blackbox.Context) extends derivation.DerivationMacros(c) {
  import c.universe._

  def materializeEncoderImpl[A: c.WeakTypeTag]: c.Expr[DerivationDerivedEncoder[A]] =
    c.Expr[DerivationDerivedEncoder[A]] {
      q"{ ${symbolOf[DerivationDerivedEncoder.type].asClass.module}.apply(${materializeEncoder[A]}) }"
    }

  def materializeDecoderImpl[A: c.WeakTypeTag]: c.Expr[DerivationDerivedDecoder[A]] =
    c.Expr[DerivationDerivedDecoder[A]] {
      q"{ ${symbolOf[DerivationDerivedDecoder.type].asClass.module}.apply(${materializeDecoder[A]}) }"
    }
}

case class DerivationDerivedEncoder[A](value: ObjectEncoder[A])
object DerivationDerivedEncoder {
  implicit def materialize[A]: DerivationDerivedEncoder[A] = macro MaterializeDerivationMacros.materializeEncoderImpl[A]
}

case class DerivationDerivedDecoder[A](value: Decoder[A])
object DerivationDerivedDecoder {
  implicit def materialize[A]: DerivationDerivedDecoder[A] = macro MaterializeDerivationMacros.materializeDecoderImpl[A]
}
