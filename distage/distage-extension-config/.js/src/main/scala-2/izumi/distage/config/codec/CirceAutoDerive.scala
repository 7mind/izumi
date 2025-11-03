package izumi.distage.config.codec

import io.circe.Decoder

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
  * Derive `io.circe.Decoder` for A and for its fields recursively with `circe-generic`
  *
  * This is the JS/Circe equivalent of [[PureconfigAutoDerive]] on JVM.
  *
  * Uses circe's auto-derivation which:
  * 1. Uses `camelCase` field names (same as DIConfigReader's pureconfig configuration)
  * 2. Supports sealed traits with a wrapper object with a single field (circe's default behavior)
  *
  * Example:
  *
  * {{{
  *   sealed trait AorB
  *   final case class A(a: Int) extends AorB
  *   final case class B(b: String) extends AorB
  *
  *   final case class Config(values: List[AorB])
  * }}}
  *
  * in config (JSON):
  *
  * {{{
  *   {
  *     "values": [
  *       { "A": { "a": 123 } },
  *       { "B": { "b": "cba" } }
  *     ]
  *   }
  * }}}
  */
final class CirceAutoDerive[A](val value: Decoder[A]) extends AnyVal

object CirceAutoDerive {
  @inline def apply[A](implicit ev: CirceAutoDerive[A]): Decoder[A] = ev.value

  @inline def derived[A](implicit ev: CirceAutoDerive[A]): Decoder[A] = ev.value

  implicit def materialize[A]: CirceAutoDerive[A] = macro CirceAutoDeriveMacro.materializeImpl[A]

  object CirceAutoDeriveMacro {
    def materializeImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[CirceAutoDerive[A]] = {
      import c.universe.*
      c.Expr[CirceAutoDerive[A]] {
        q"""{
           import _root_.io.circe.generic.auto._

           new ${weakTypeOf[CirceAutoDerive[A]]}(_root_.io.circe.generic.auto.exportDecoder[${weakTypeOf[A]}].instance)
         }"""
      }
    }
  }
}
