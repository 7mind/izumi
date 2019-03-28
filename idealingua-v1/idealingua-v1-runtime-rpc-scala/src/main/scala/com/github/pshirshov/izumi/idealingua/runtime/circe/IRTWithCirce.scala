package com.github.pshirshov.izumi.idealingua.runtime.circe

import io.circe.{Decoder, Encoder, ObjectEncoder, derivation}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Provides circe codecs for case classes (won't work with sealed traits):
 * {{{
 *   final case class Abc(a: String, b: String, c: String)
 *
 *   object Abc extends WithCirce[Abc]
 * }}}
 *
 * For sealed traits use [[IRTWithCirceGeneric]]. It's not as efficient wrt compile time, but will cache during a single compilation run.
 */
abstract class IRTWithCirce[A: DerivationDerivedEncoder: DerivationDerivedDecoder] {
  implicit val enc: Encoder[A] = implicitly[DerivationDerivedEncoder[A]].value
  implicit val dec: Decoder[A] = implicitly[DerivationDerivedDecoder[A]].value
}

// TODO: merge upstream, also with @JsonCodec
final class MaterializeDerivationMacros(override val c: blackbox.Context) extends derivation.DerivationMacros(c) {
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

final case class DerivationDerivedEncoder[A](value: ObjectEncoder[A])
object DerivationDerivedEncoder {
  implicit def materialize[A]: DerivationDerivedEncoder[A] = macro MaterializeDerivationMacros.materializeEncoderImpl[A]
}

final case class DerivationDerivedDecoder[A](value: Decoder[A])
object DerivationDerivedDecoder {
  implicit def materialize[A]: DerivationDerivedDecoder[A] = macro MaterializeDerivationMacros.materializeDecoderImpl[A]
}
