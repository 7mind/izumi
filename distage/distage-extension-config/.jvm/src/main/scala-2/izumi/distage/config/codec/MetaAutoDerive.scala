package izumi.distage.config.codec

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final class MetaAutoDerive[A](val value: DIConfigMeta[A]) extends AnyVal

object MetaAutoDerive {
  @inline def apply[A](implicit ev: MetaAutoDerive[A]): DIConfigMeta[A] = ev.value

  @inline def derived[A](implicit ev: MetaAutoDerive[A]): DIConfigMeta[A] = ev.value

  implicit def materialize[A]: MetaAutoDerive[A] = macro MetaAutoDeriveMacro.materializeImpl[A]

  object MetaAutoDeriveMacro {
    def materializeImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[MetaAutoDerive[A]] = {
      import c.universe.*
      c.Expr[MetaAutoDerive[A]] {
        // Yes, this is legal /_\ !! We add an import so that implicit scope is enhanced
        // by new config codecs that aren't in ConfigReader companion object
        q"""{
           import _root_.izumi.distage.config.codec.MetaInstances.auto._
           import _root_.izumi.distage.config.codec.MetaInstances._

           new ${weakTypeOf[MetaAutoDerive[A]]}(_root_.izumi.distage.config.codec.MetaInstances.auto.gen[${weakTypeOf[A]}])
         }"""
      }
    }
  }
}
