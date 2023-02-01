package izumi.distage.config.codec

import pureconfig.ConfigReader

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
  * Derive `pureconfig.ConfigReader` for A and for its fields recursively with `pureconfig-magnolia`
  *
  * This differs from just using [[pureconfig.module.magnolia.auto.reader.exportReader]] by using
  * different configuration, defined in [[PureconfigInstances]], specifically:
  *
  * 1. Field name remapping is disabled, `camelCase` fields will remain camelCase, not `kebab-case`
  * 2. Sealed traits are rendered as in `circe`, using a wrapper object with a single field, instead of using a `type` field. Example:
  *
  * {{{
  *   sealed trait AorB
  *   final case class A(a: Int) extends AorB
  *   final case class B(b: String) extends AorB
  *
  *   final case class Config(values: List[AorB])
  * }}}
  *
  * in config:
  *
  * {{{
  *   config {
  *     values = [
  *       { A { a = 123 } },
  *       { B { b = cba } }
  *     ]
  *   }
  * }}}
  */
final class PureconfigAutoDerive[A](val value: ConfigReader[A]) extends AnyVal

object PureconfigAutoDerive {
  @inline def apply[A](implicit ev: PureconfigAutoDerive[A]): ConfigReader[A] = ev.value

  @inline def derived[A](implicit ev: PureconfigAutoDerive[A]): ConfigReader[A] = ev.value

  implicit def materialize[A]: PureconfigAutoDerive[A] = macro PureconfigAutoDeriveMacro.materializeImpl[A]

  object PureconfigAutoDeriveMacro {
    def materializeImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[PureconfigAutoDerive[A]] = {
      import c.universe.*
      c.Expr[PureconfigAutoDerive[A]] {
        // Yes, this is legal /_\ !! We add an import so that implicit scope is enhanced
        // by new config codecs that aren't in ConfigReader companion object
        q"""{
           import _root_.izumi.distage.config.interned.pureconfig.module.magnolia.auto.reader._
           import _root_.izumi.distage.config.codec.PureconfigInstances._

           new ${weakTypeOf[PureconfigAutoDerive[A]]}(_root_.izumi.distage.config.interned.pureconfig.module.magnolia.auto.reader.exportReader[${weakTypeOf[A]}].instance)
         }"""
      }
    }
  }
}
