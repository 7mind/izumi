package izumi.distage.config.codec

import pureconfig.*
import pureconfig.generic.derivation.Utils

import scala.compiletime.ops.int.+
import scala.compiletime.{constValue, constValueTuple, erasedValue, summonFrom}
import scala.deriving.Mirror
import scala.util.chaining.*

object MetaInstances {
  // TODO: deduplicate w/PureconfigInstances
  object auto {
    inline implicit def exportDerivedDIConfigMeta[A]: Exported[DIConfigMeta[A]] = {
      summonFrom {
        case c: DIConfigMeta[A] => Exported(c) // avoids `increase -Xmax-inlines` error, but makes no sense to me. Shouldn't be required at all.
        case m: Mirror.Of[A] =>
          Exported(configReaderDerivation.derived[A](using m))
      }
    }
  }

  object configReaderDerivation {

    inline def derived[A](using m: Mirror.Of[A]): DIConfigMeta[A] = {
      inline m match {
        case given Mirror.ProductOf[A] => derivedProduct
        case given Mirror.SumOf[A] => derivedSum
      }
    }

    /** Override pureconfig's default `kebab-case` fields – force CamelCase product-hint */
    inline def derivedProduct[A](using m: Mirror.ProductOf[A]): DIConfigMeta[A] = {
      val tname = constValue[m.MirroredLabel]

      inline erasedValue[A] match {
        case _: Tuple =>
          new DIConfigMeta[A] {
            override def tpe: ConfigMetaType = ConfigMetaType.TUnknown("derivedProduct")
          }

        case _ =>
          new DIConfigMeta[A] {
            override def tpe: ConfigMetaType = {
              val labels: Array[String] = Utils.transformedLabels[A](fieldMapping).toArray
              val codecs = readTuple[m.MirroredElemTypes, 0]
              val fieldMeta = ConfigMetaType.TCaseClass(
                convertId(tname),
                labels.iterator
                  .zip(codecs).map {
                    case (label, reader) => (label, reader.tpe)
                  }.toSeq,
              )
              fieldMeta
            }
          }
      }
    }

    inline def readTuple[T <: Tuple, N <: Int]: List[DIConfigMeta[Any]] =
      inline erasedValue[T] match {
        case _: (h *: t) =>
          val reader = summonDIConfigMeta[h]
          val tReaders = readTuple[t, N + 1]
          reader.asInstanceOf[DIConfigMeta[Any]] :: tReaders

        case _: EmptyTuple =>
          Nil
      }

    inline def summonDIConfigMeta[A]: DIConfigMeta[A] =
      summonFrom {
        case reader: DIConfigMeta[A] => reader
        case given Mirror.Of[A] => derived[A]
      }

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
    inline def derivedSum[A](using m: Mirror.SumOf[A]): DIConfigMeta[A] = {
      val tname = constValue[m.MirroredLabel]

      new DIConfigMeta[A] {
        val options: Map[String, DIConfigMeta[A]] =
          Utils
            .transformedLabels[A](fieldMapping)
            .zip(deriveForSubtypes[m.MirroredElemTypes, A])
            .toMap

        override val tpe: ConfigMetaType = ConfigMetaType.TSealedTrait(
          convertId(tname),
          options.map {
            case (label, reader) => (label, reader.tpe)
          }.toSet,
        )
      }
    }

    inline def deriveForSubtypes[T <: Tuple, A]: List[DIConfigMeta[A]] =
      inline erasedValue[T] match {
        case _: (h *: t) => deriveForSubtype[h, A] :: deriveForSubtypes[t, A]
        case _: EmptyTuple => Nil
      }

    inline def deriveForSubtype[A0, A]: DIConfigMeta[A] =
      summonFrom {
        case reader: DIConfigMeta[A0] =>
          reader.asInstanceOf[DIConfigMeta[A]]

        case given Mirror.Of[A0] =>
          derived[A0].asInstanceOf[DIConfigMeta[A]]
      }

    private[this] val fieldMapping: ConfigFieldMapping = ConfigFieldMapping(CamelCase, CamelCase)

    private def convertId(name: String): ConfigMetaTypeId = {
      val parts = name.split('.').toSeq
      ConfigMetaTypeId(Some(parts.init.mkString(".")), parts.last, Seq.empty)
    }
  }

}
