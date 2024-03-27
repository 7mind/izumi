package izumi.distage.config.codec

import magnolia1.{CaseClass, SealedTrait}
import pureconfig.*
import pureconfig.ConfigReader.Result
import pureconfig.generic.{CoproductHint, ProductHint}
import pureconfig.module.magnolia.{ExportedMagnolia, MagnoliaConfigReader}

import scala.language.experimental.macros

object PureconfigInstances extends PureconfigHints {

  object auto {
    implicit def exportReader[A]: Exported[ConfigReader[A]] = macro ExportedMagnolia.exportedMagnolia[ConfigReader, A]

    type Typeclass[A] = ConfigReader[A]

    def join[A](ctx: CaseClass[ConfigReader, A])(implicit productHint: ProductHint[A]): ConfigReader[A] = {
      val magnoliaConfigReader = MagnoliaConfigReader.join(ctx)
      new ConfigReader[A] {
        override def from(cur: ConfigCursor): Result[A] = magnoliaConfigReader.from(cur)
      }
    }

    def split[A](ctx: SealedTrait[ConfigReader, A])(implicit coproductHint: CoproductHint[A]): ConfigReader[A] = {
      val magnoliaConfigReader = MagnoliaConfigReader.split(ctx)
      new ConfigReader[A] {
        override def from(cur: ConfigCursor): Result[A] = magnoliaConfigReader.from(cur)
      }
    }
  }

}
