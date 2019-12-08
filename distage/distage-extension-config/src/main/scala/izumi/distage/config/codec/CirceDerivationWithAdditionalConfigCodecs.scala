package izumi.distage.config.codec

import io.circe.Decoder

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final case class CirceDerivationWithAdditionalConfigCodecs[A](value: Decoder[A]) extends AnyVal

object CirceDerivationWithAdditionalConfigCodecs {
  implicit def materialize[A]: CirceDerivationWithAdditionalConfigCodecs[A] = macro materializeImpl[A]

  final def materializeImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[CirceDerivationWithAdditionalConfigCodecs[A]] = {
    import c.universe._
    c.Expr[CirceDerivationWithAdditionalConfigCodecs[A]] {
      // Yes, this is legal /_\ !! We add an import so that implicit scope is enhanced
      // by new config codecs that aren't in Decoder companion object
      q"""{
           import _root_.izumi.distage.config.codec.CirceConfigInstances._

           new ${weakTypeOf[CirceDerivationWithAdditionalConfigCodecs[A]]}(_root_.io.circe.derivation.deriveDecoder[${weakTypeOf[A]}])
         }"""
    }
  }
}
