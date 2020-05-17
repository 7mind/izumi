package izumi.fundamentals.json.circe

import io.circe.{Codec, Decoder, Encoder, derivation}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
  * Provides circe codecs for case classes and sealed traits
  *
  * {{{
  *   final case class Abc(a: String, b: String, c: String)
  *
  *   object Abc extends WithCirce[Abc]
  * }}}
  *
  * To derive codecs for a sealed trait with branches inside its
  * own companion object, use a proxy object - this works around
  * a scala limitation: https://github.com/milessabin/shapeless/issues/837
  *
  * {{{
  *   sealed trait Abc
  *
  *   private abcCodecs extends WithCirce[Abc]
  *
  *   object Abc extends WithCirce(abcCodecs) {
  *     final case class A()
  *     object A extends WithCirce[A]
  *
  *     final case class B()
  *     object B extends WithCirce[B]
  *     final case class C()
  *
  *     object C extends WithCirce[C]
  *   }
  * }}}
  *
  */
abstract class WithCirce[A]()(implicit derivedCodec: DerivationDerivedCodec[A]) {
  // workaround for https://github.com/milessabin/shapeless/issues/837
  def this(proxy: WithCirce[A]) = this()(DerivationDerivedCodec(proxy.codec))

  implicit val codec: Codec.AsObject[A] = derivedCodec.value
}

// TODO: merge upstream, also with @JsonCodec
final class MaterializeDerivationMacros(override val c: blackbox.Context) extends derivation.DerivationMacros(c) {
  import c.universe._

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
