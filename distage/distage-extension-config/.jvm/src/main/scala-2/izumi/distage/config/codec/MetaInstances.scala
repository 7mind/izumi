package izumi.distage.config.codec

import magnolia1.{CaseClass, Magnolia, SealedTrait}

import scala.language.experimental.macros

trait MetaInstances {}

object MetaInstances extends MetaInstances {

  object auto {
    implicit def gen[T]: DIConfigMeta[T] = macro Magnolia.gen[T]
//    implicit def exportReader[A]: Exported[DIConfigMeta[A]] = macro ExportedMagnolia.exportedMagnolia[DIConfigMeta, A]

    type Typeclass[A] = DIConfigMeta[A]

    def join[A](ctx: CaseClass[DIConfigMeta, A]): DIConfigMeta[A] = {
      println(ctx)
      new DIConfigMeta[A] {
        override def tpe: ConfigMetaType = ???
      }
    }

    def split[A](ctx: SealedTrait[DIConfigMeta, A]): DIConfigMeta[A] = {
      println(ctx)

      new DIConfigMeta[A] {
        override def tpe: ConfigMetaType = ???
      }
    }

//    def configMetaJoin[TC[_], A](ctx: CaseClass[TC, A], toConfigMeta: TC[Any] => ConfigMetaType)(implicit productHint: ProductHint[A]): ConfigMetaType = {
//      def fields0: ConfigMetaType.TCaseClass = ConfigMetaType.TCaseClass(
//        ctx.parameters.map(
//          p => {
//            val realLabel = {
//              productHint.to(Some(ConfigValueFactory.fromAnyRef("x")), p.label) match {
//                case Some((processedLabel, _)) => processedLabel
//                case None => p.label
//              }
//            }
//            (realLabel, toConfigMeta(p.typeclass.asInstanceOf[TC[Any]]))
//          }
//        )
//      )
//      if (ctx.typeName.full.startsWith("scala.Tuple")) ConfigMetaType.TUnknown("configMetaJoin")
//      else if (ctx.isValueClass) fields0.fields.head._2 /* NB: AnyVal codecs are not supported on Scala 3 */
//      else fields0
//    }
//
//    def configMetaSplit[TC[_], A](ctx: SealedTrait[TC, A], toConfigMeta: TC[Any] => ConfigMetaType)(implicit coproductHint: CoproductHint[A]): ConfigMetaType = {
//      // Only support Circe-like sealed trait encoding
//      if (coproductHint == PureconfigInstances.circeLikeCoproductHint) {
//        ConfigMetaType.TSealedTrait(
//          ctx.subtypes.map {
//            s =>
//              val realLabel = s.typeName.short // no processing is required for Circe-like hint
//              (realLabel, toConfigMeta(s.typeclass.asInstanceOf[TC[Any]]))
//          }.toSet
//        )
//      } else {
//        ConfigMetaType.TUnknown("configMetaSplit")
//      }
//    }
  }

}
