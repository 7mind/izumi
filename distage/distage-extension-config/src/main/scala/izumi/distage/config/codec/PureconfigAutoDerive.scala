package izumi.distage.config.codec

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** Derive `pureconfig.ConfigReader` for A and for its fields recursively with `pureconfig-magnolia` */
final class PureconfigAutoDerive[A](val value: pureconfig.ConfigReader[A]) extends AnyVal

object PureconfigAutoDerive {
  @inline def apply[A](implicit ev: PureconfigAutoDerive[A]): pureconfig.ConfigReader[A] = ev.value

  implicit def materialize[A]: PureconfigAutoDerive[A] = macro CirceDerivationConfigStyleMacro.materializeImpl[A]

  object CirceDerivationConfigStyleMacro {
    def materializeImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[PureconfigAutoDerive[A]] = {
      import c.universe._
      c.Expr[PureconfigAutoDerive[A]] {
        // Yes, this is legal /_\ !! We add an import so that implicit scope is enhanced
        // by new config codecs that aren't in Decoder companion object
        q"""{
           import _root_.izumi.distage.config.codec.PureconfigInstances._
           new ${weakTypeOf[PureconfigAutoDerive[A]]}(_root_.pureconfig.module.magnolia.auto.reader.exportReader[${weakTypeOf[A]}])
         }"""
      }
    }
  }
}
