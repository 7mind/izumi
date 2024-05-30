package izumi.distage.config.codec

import magnolia1.Magnolia

import scala.language.experimental.macros
import scala.reflect.macros.{blackbox, whitebox}

final class MetaAutoDerive[A](val value: DIConfigMeta[A]) extends AnyVal

object MetaAutoDerive {
  @inline def apply[A](implicit ev: MetaAutoDerive[A]): DIConfigMeta[A] = ev.value

  @inline def derived[A](implicit ev: MetaAutoDerive[A]): DIConfigMeta[A] = ev.value

  implicit def materialize[A]: MetaAutoDerive[A] = macro MetaAutoDeriveMacro.materializeImpl[A]

  object MetaAutoDeriveMacro {
    def materializeImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[MetaAutoDerive[A]] = {
      import c.universe.*
      c.Expr[MetaAutoDerive[A]] {
        q"""{
           import _root_.izumi.distage.config.codec.MetaInstances.auto._
           import _root_.izumi.distage.config.codec.PureconfigHints._

           _root_.izumi.distage.config.codec.MetaInstances.auto.gen[${weakTypeOf[A]}]
         }"""
      }
    }

    /** @see [[pureconfig.module.magnolia.ExportedMagnolia]] */
    def exportedMagnolia[A: c.WeakTypeTag](c: whitebox.Context): c.Expr[MetaAutoDerive[A]] = {
      val magnoliaTree = c.Expr[DIConfigMeta[A]](Magnolia.gen[A](c))
      c.universe.reify(new MetaAutoDerive[A](magnoliaTree.splice))
    }
  }
}
