package izumi.distage.config.codec

import io.circe.Decoder

import scala.deriving.Mirror
import scala.language.implicitConversions

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

  inline implicit def materialize[A](using inline m: Mirror.Of[A]): CirceAutoDerive[A] = {
    import io.circe.generic.auto.deriveDecoder

    new CirceAutoDerive[A](deriveDecoder[A](using m).instance)
  }
}
