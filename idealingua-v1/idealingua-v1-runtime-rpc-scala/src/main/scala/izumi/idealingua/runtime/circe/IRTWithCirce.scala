package izumi.idealingua.runtime.circe

import io.circe.{Decoder, Encoder, derivation}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Provides circe codecs for case classes and sealed traits
 * {{{
 *   final case class Abc(a: String, b: String, c: String)
 *
 *   object Abc extends IRTWithCirce[Abc]
 * }}}
 */
abstract class IRTWithCirce[A](implicit encoder: DerivationDerivedEncoder[A], decoder: DerivationDerivedDecoder[A]) {
  // workaround https://github.com/milessabin/shapeless/issues/837
  def this(proxy: IRTWithCirce[A]) = this()(DerivationDerivedEncoder(proxy.enc), DerivationDerivedDecoder(proxy.dec))

  implicit val enc: Encoder.AsObject[A] = encoder.value
  implicit val dec: Decoder[A] = decoder.value
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

final case class DerivationDerivedEncoder[A](value: Encoder.AsObject[A])
object DerivationDerivedEncoder {
  implicit def materialize[A]: DerivationDerivedEncoder[A] = macro MaterializeDerivationMacros.materializeEncoderImpl[A]
}

final case class DerivationDerivedDecoder[A](value: Decoder[A])
object DerivationDerivedDecoder {
  implicit def materialize[A]: DerivationDerivedDecoder[A] = macro MaterializeDerivationMacros.materializeDecoderImpl[A]
}
