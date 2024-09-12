package izumi.distage.config.codec

import com.typesafe.config.ConfigValueFactory
import izumi.distage.config.codec.ConfigMetaType.ConfigField
import izumi.distage.config.model.ConfigDoc
import magnolia1.{CaseClass, SealedTrait, TypeName}
import pureconfig.generic.{CoproductHint, ProductHint}

import scala.language.experimental.macros

object MetaInstances {
  object auto {
    implicit def gen[T]: MetaAutoDerive[T] = macro MetaAutoDerive.MetaAutoDeriveMacro.exportedMagnolia[T]

    type Typeclass[A] = DIConfigMeta[A]

    def join[A](ctx: CaseClass[DIConfigMeta, A])(implicit productHint: ProductHint[A]): DIConfigMeta[A] = {
      val meta = configMetaJoin(ctx)

      DIConfigMeta[A](meta)
    }

    def split[A](ctx: SealedTrait[DIConfigMeta, A])(implicit coproductHint: CoproductHint[A]): DIConfigMeta[A] = {
      val meta = configMetaSplit(ctx)

      DIConfigMeta[A](meta)
    }

    private def configMetaJoin[A](ctx: CaseClass[DIConfigMeta, A])(implicit productHint: ProductHint[A]): ConfigMetaType = {
      val maybeDocs = extractAnnos(ctx.annotations)

      def fields0: ConfigMetaType.TCaseClass = ConfigMetaType.TCaseClass(
        convertId(ctx.typeName),
        ctx.parameters.map(
          p => {
            val maybeFieldAnnos = extractAnnos(p.annotations)
            val realLabel = {
              productHint.to(Some(ConfigValueFactory.fromAnyRef("x")), p.label) match {
                case Some((processedLabel, _)) => processedLabel
                case None => p.label
              }
            }
            ConfigField(realLabel, p.typeclass.asInstanceOf[DIConfigMeta[Any]].tpe, maybeFieldAnnos)
          }
        ),
        maybeDocs,
      )

      if (ctx.typeName.full.startsWith("scala.Tuple")) ConfigMetaType.TUnknown()
      else if (ctx.isValueClass) fields0.fields.head.tpe /* NB: AnyVal codecs are not supported on Scala 3 */
      else fields0
    }

    private def configMetaSplit[A](ctx: SealedTrait[DIConfigMeta, A])(implicit coproductHint: CoproductHint[A]): ConfigMetaType = {
      val maybeDocs = extractAnnos(ctx.annotations)

      // Only support Circe-like sealed trait encoding
      if (coproductHint == PureconfigHints.circeLikeCoproductHint) {
        ConfigMetaType.TSealedTrait(
          convertId(ctx.typeName),
          ctx.subtypes.map {
            s =>
              val realLabel = s.typeName.short // no processing is required for Circe-like hint
              (realLabel, s.typeclass.asInstanceOf[DIConfigMeta[Any]].tpe)
          }.toSet,
          maybeDocs,
        )
      } else {
        ConfigMetaType.TUnknown()
      }
    }

    private def convertId(name: TypeName): ConfigMetaTypeId = {
      ConfigMetaTypeId(Some(name.owner), name.short, name.typeArguments.map(convertId))
    }

    private def extractAnnos(annos: Seq[Any]) = {
      Option(annos.collect { case d: ConfigDoc => d }.map(_.doc)).filter(_.nonEmpty).map(_.mkString("\n"))
    }
  }

}
