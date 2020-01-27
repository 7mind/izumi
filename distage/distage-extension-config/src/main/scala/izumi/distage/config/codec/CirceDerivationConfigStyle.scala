package izumi.distage.config.codec

import io.circe.Decoder

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final case class CirceDerivationConfigStyle[A](value: Decoder[A]) extends AnyVal

object CirceDerivationConfigStyle {
  @inline def deriveDecoder[A](implicit ev: CirceDerivationConfigStyle[A]): Decoder[A] = ev.value

  implicit def materialize[A]: CirceDerivationConfigStyle[A] = macro CirceDerivationConfigStyleMacro.materializeImpl[A]

  object CirceDerivationConfigStyleMacro {
    def materializeImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[CirceDerivationConfigStyle[A]] = {
      import c.universe._
      c.Expr[CirceDerivationConfigStyle[A]] {
        // Yes, this is legal /_\ !! We add an import so that implicit scope is enhanced
        // by new config codecs that aren't in Decoder companion object
        q"""{
           import _root_.izumi.distage.config.codec.CirceConfigInstances._

           new ${weakTypeOf[CirceDerivationConfigStyle[A]]}(_root_.io.circe.derivation.deriveDecoder[${weakTypeOf[A]}])
         }"""
      }
    }
  }
}
