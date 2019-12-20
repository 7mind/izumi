package izumi.idealingua.runtime.circe

import io.circe.{Codec, Decoder, Encoder, derivation}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
  * Provides circe codecs for case classes and sealed traits
  *
  * {{{
  *   final case class Abc(a: String, b: String, c: String)
  *
  *   object Abc extends IRTWithCirce[Abc]
  * }}}
  *
  * To derive codecs for a sealed trait with branches inside its
  * own companion object, use a proxy object - this works around
  * a scala limitation: https://github.com/milessabin/shapeless/issues/837
  *
  * {{{
  *   sealed trait Abc
  *
  *   private abcCodecs extends IRTWithCirce[Abc]
  *
  *   object Abc extends IRTWithCirce(abcCodecs) {
  *     final case class A()
  *     object A extends IRTWithCirce[A]
  *
  *     final case class B()
  *     object B extends IRTWithCirce[B]
  *     final case class C()
  *
  *     object C extends IRTWithCirce[C]
  *   }
  * }}}
  *
  */
abstract class IRTWithCirce[A]()(implicit derivedCodec: DerivationDerivedCodec[A]) {
  // workaround for https://github.com/milessabin/shapeless/issues/837
  def this(proxy: IRTWithCirce[A]) = this()(DerivationDerivedCodec(proxy.codec))

  implicit val codec: Codec.AsObject[A] = derivedCodec.value
}

// TODO: merge upstream, also with @JsonCodec
final class MaterializeDerivationMacros(override val c: blackbox.Context) extends derivation.DerivationMacros(c) {
  import c.universe._

  def materializeEncoderImpl[A: c.WeakTypeTag]: c.Expr[DerivationDerivedEncoder[A]] =
    c.Expr[DerivationDerivedEncoder[A]] {
      // Yes, this is legal /_\ !! We add an import so that
      // instances in IRTTimeInstances always beat out circe's default instances
      q"""{
           import _root_.izumi.idealingua.runtime.circe.IRTTimeInstances._

           new ${weakTypeOf[DerivationDerivedEncoder[A]]}(_root_.io.circe.derivation.deriveEncoder[${weakTypeOf[A]}])
         }"""
    }

  def materializeDecoderImpl[A: c.WeakTypeTag]: c.Expr[DerivationDerivedDecoder[A]] =
    c.Expr[DerivationDerivedDecoder[A]] {
      // Yes, this is legal /_\ !! We add an import so that
      // instances in IRTTimeInstances always beat out circe's default instances
      q"""{
           import _root_.izumi.idealingua.runtime.circe.IRTTimeInstances._

           new ${weakTypeOf[DerivationDerivedDecoder[A]]}(_root_.io.circe.derivation.deriveDecoder[${weakTypeOf[A]}])
         }"""
    }

  def materializeCodecImpl[A: c.WeakTypeTag]: c.Expr[DerivationDerivedCodec[A]] =
    c.Expr[DerivationDerivedCodec[A]] {
      // Yes, this is legal /_\ !! We add an import so that
      // instances in IRTTimeInstances always beat out circe's default instances
      q"""{
           import _root_.izumi.idealingua.runtime.circe.IRTTimeInstances._

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
