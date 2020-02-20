package izumi.distage.config.codec

import magnolia.{CaseClass, Magnolia, SealedTrait}
import pureconfig.generic.{CoproductHint, ProductHint}
import pureconfig.module.magnolia.MagnoliaConfigReader
import pureconfig.{ConfigReader, Exported}

import scala.language.experimental.macros
import scala.reflect.macros.{blackbox, whitebox}

/** Derive `pureconfig.ConfigReader` for A and for its fields recursively with `pureconfig-magnolia` */
final class PureconfigAutoDerive[A](val value: pureconfig.ConfigReader[A]) extends AnyVal

object PureconfigAutoDerive {
  @inline def apply[A](implicit ev: PureconfigAutoDerive[A]): pureconfig.ConfigReader[A] = ev.value

  implicit def materialize[A]: PureconfigAutoDerive[A] = macro PureconfigAutoDeriveMacro.materializeImpl[A]

  object exportedReader {
    type Typeclass[A] = ConfigReader[A]
    def combine[A: ProductHint](ctx: CaseClass[ConfigReader, A]): ConfigReader[A] = MagnoliaConfigReader.combine(ctx)
    def dispatch[A: CoproductHint](ctx: SealedTrait[ConfigReader, A]): ConfigReader[A] = MagnoliaConfigReader.dispatch(ctx)

//    @debug("Class")
    implicit def exportReader[A]: Exported[ConfigReader[A]] = macro ExportedMagnolia.materializeImpl[A]
    // Wrap the output of Magnolia in an Exported to force it to a lower priority.
    // This seems to work, despite magnolia hardcode checks for `macroApplication` symbol
    // and relying on getting an diverging implicit expansion error for auto-mode.
    // Thankfully at least it doesn't check the output type of its `macroApplication`
    object ExportedMagnolia {
      def materializeImpl[A](c: blackbox.Context)(implicit t: c.WeakTypeTag[A]): c.Expr[Exported[ConfigReader[A]]] = {
        val magnoliaTree = {
          val c0 = c.asInstanceOf[whitebox.Context] // if i TELL you it's whitebox, you BETTER believe me
          c.Expr[ConfigReader[A]](Magnolia.gen[A](c.asInstanceOf[c0.type])(t.asInstanceOf[c0.WeakTypeTag[A]]).asInstanceOf[c.Tree])
        }
        c.universe.reify(Exported(magnoliaTree.splice))
      }
    }
  }

  object PureconfigAutoDeriveMacro {
    def materializeImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[PureconfigAutoDerive[A]] = {
      import c.universe._
      c.Expr[PureconfigAutoDerive[A]] {
        // Yes, this is legal /_\ !! We add an import so that implicit scope is enhanced
        // by new config codecs that aren't in ConfigReader companion object
        q"""{
           import _root_.izumi.distage.config.codec.PureconfigAutoDerive.exportedReader._
           import _root_.izumi.distage.config.codec.PureconfigInstances._

           new ${weakTypeOf[PureconfigAutoDerive[A]]}(_root_.izumi.distage.config.codec.PureconfigAutoDerive.exportedReader.exportReader[${weakTypeOf[A]}].instance)
         }"""
      }
    }
  }
}
