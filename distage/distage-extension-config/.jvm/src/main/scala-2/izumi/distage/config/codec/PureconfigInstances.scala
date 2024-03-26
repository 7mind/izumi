package izumi.distage.config.codec

import com.typesafe.config.{ConfigValue, ConfigValueFactory}
import magnolia1.{CaseClass, SealedTrait}
import pureconfig.*
import pureconfig.ConfigReader.Result
import pureconfig.error.{ConfigReaderFailures, ThrowableFailure}
import pureconfig.generic.error.{InvalidCoproductOption, NoValidCoproductOptionFound}
import pureconfig.generic.{CoproductHint, ProductHint}
import pureconfig.module.magnolia.{ExportedMagnolia, MagnoliaConfigReader}

import scala.language.experimental.macros

trait PureconfigInstances extends PureconfigSharedInstances {

  /** Override pureconfig's default `kebab-case` fields – force CamelCase product-hint */
  @inline implicit final def forceCamelCaseProductHint[T]: ProductHint[T] = PureconfigInstances.camelCaseProductHint.asInstanceOf[ProductHint[T]]

  /** Override pureconfig's default `type` field type discriminator for sealed traits.
    * Instead, use `circe`-like format with a single-key object. Example:
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
    *     values = [{ A { a = 5 } }, { B { b = cba } }]
    *   }
    * }}}
    */
  @inline implicit final def forceCirceLikeCoproductHint[T]: CoproductHint[T] = PureconfigInstances.circeLikeCoproductHint.asInstanceOf[CoproductHint[T]]

}

object PureconfigInstances extends PureconfigInstances {

  object auto {
    implicit def exportReader[A]: Exported[ConfigReader[A]] = macro ExportedMagnolia.exportedMagnolia[ConfigReader, A]

    type Typeclass[A] = ConfigReader[A]

    def join[A](ctx: CaseClass[ConfigReader, A])(implicit productHint: ProductHint[A]): ConfigReader[A] = {
      val magnoliaConfigReader = MagnoliaConfigReader.join(ctx)
      val fields = configMetaJoin(ctx, ConfigReaderWithConfigMeta.maybeFieldsFromConfigReader)
      new ConfigReaderWithConfigMeta[A] {
        override def from(cur: ConfigCursor): Result[A] = magnoliaConfigReader.from(cur)
        override def tpe: ConfigMetaType = fields
      }
    }

    def split[A](ctx: SealedTrait[ConfigReader, A])(implicit coproductHint: CoproductHint[A]): ConfigReader[A] = {
      val magnoliaConfigReader = MagnoliaConfigReader.split(ctx)
      val fields = configMetaSplit(ctx, ConfigReaderWithConfigMeta.maybeFieldsFromConfigReader)
      new ConfigReaderWithConfigMeta[A] {
        override def from(cur: ConfigCursor): Result[A] = magnoliaConfigReader.from(cur)
        override def tpe: ConfigMetaType = fields
      }
    }

    def configMetaJoin[TC[_], A](ctx: CaseClass[TC, A], toConfigMeta: TC[Any] => ConfigMetaType)(implicit productHint: ProductHint[A]): ConfigMetaType = {
      def fields0: ConfigMetaType.TCaseClass = ConfigMetaType.TCaseClass(
        ctx.parameters.map(
          p => {
            val realLabel = {
              productHint.to(Some(ConfigValueFactory.fromAnyRef("x")), p.label) match {
                case Some((processedLabel, _)) => processedLabel
                case None => p.label
              }
            }
            (realLabel, toConfigMeta(p.typeclass.asInstanceOf[TC[Any]]))
          }
        )
      )
      if (ctx.typeName.full.startsWith("scala.Tuple")) ConfigMetaType.TUnknown()
      else if (ctx.isValueClass) fields0.fields.head._2 /* NB: AnyVal codecs are not supported on Scala 3 */
      else fields0
    }

    def configMetaSplit[TC[_], A](ctx: SealedTrait[TC, A], toConfigMeta: TC[Any] => ConfigMetaType)(implicit coproductHint: CoproductHint[A]): ConfigMetaType = {
      // Only support Circe-like sealed trait encoding
      if (coproductHint == PureconfigInstances.circeLikeCoproductHint) {
        ConfigMetaType.TSealedTrait(
          ctx.subtypes.map {
            s =>
              val realLabel = s.typeName.short // no processing is required for Circe-like hint
              (realLabel, toConfigMeta(s.typeclass.asInstanceOf[TC[Any]]))
          }.toSet
        )
      } else {
        ConfigMetaType.TUnknown()
      }
    }
  }

  private[config] final val camelCaseProductHint: ProductHint[Any] = ProductHint(ConfigFieldMapping(CamelCase, CamelCase))

  private[config] final val circeLikeCoproductHint: CoproductHint[Any] = new CoproductHint[Any] {
    override def from(cur: ConfigCursor, options: Seq[String]): Result[CoproductHint.Action] = {
      for {
        objCur <- cur.asObjectCursor
        _ <-
          if (objCur.keys.size == 1) {
            Right(())
          } else {
            val msg = s"""Invalid format for sealed trait, found multiple or no keys in object. Expected a single-key object like `{ "$$K": {} }`
                         |where `$$K` can be one of ${options.mkString}""".stripMargin
            Left(
              ConfigReaderFailures(
                objCur.failureFor(
                  NoValidCoproductOptionFound(objCur.objValue, Seq("_invalid_format_" -> ConfigReaderFailures(ThrowableFailure(new RuntimeException(msg), None))))
                )
              )
            )
          }
        res <- {
          val key = objCur.keys.head
          if (options.contains(key)) {
            objCur.atKey(key).map(CoproductHint.Use(_, key))
          } else {
            Left(ConfigReaderFailures(objCur.failureFor(InvalidCoproductOption(key))))
          }
        }
      } yield res
    }
    override def to(cv: ConfigValue, name: String): ConfigValue = {
      import pureconfig.syntax.*
      Map(name -> cv).toConfig
    }
  }

}
