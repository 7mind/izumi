package izumi.distage.config.codec

import pureconfig.generic.derivation.Utils

import scala.compiletime.ops.int.+
import scala.compiletime.*
import scala.deriving.Mirror
import scala.util.chaining.*

object MetaInstances {

  // magnolia on scala3 abuses inlines and requires high inlines limit (e.g. -Xmax-inlines:1024).
  // so we had to re-implement derivation manually and copy-paste the type id logic from Magnolia
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
            override def tpe: ConfigMetaType = ConfigMetaType.TUnknown()
          }

        case _ =>
          new DIConfigMeta[A] {
            override def tpe: ConfigMetaType = {
              val labels: Array[String] = Utils.transformedLabels[A](PureconfigInstances.configReaderDerivation.fieldMapping).toArray
              val codecs = readTuple[m.MirroredElemTypes, 0]
              val fieldMeta = ConfigMetaType.TCaseClass(
                typeId[A],
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
      new DIConfigMeta[A] {
        val options: Map[String, DIConfigMeta[A]] =
          Utils
            .transformedLabels[A](PureconfigInstances.configReaderDerivation.fieldMapping)
            .zip(deriveForSubtypes[m.MirroredElemTypes, A])
            .toMap

        override val tpe: ConfigMetaType = ConfigMetaType.TSealedTrait(
          typeId[A],
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

    import scala.quoted.*

    // https://github.com/softwaremill/magnolia/blob/scala3/core/src/main/scala/magnolia1/macro.scala#L135
    private inline def typeId[T]: ConfigMetaTypeId = ${ typeIdImpl[T] }

    private def typeIdImpl[T: Type](using Quotes): Expr[ConfigMetaTypeId] = {

      import quotes.reflect.*

      def normalizedName(s: Symbol): String =
        if s.flags.is(Flags.Module) then s.name.stripSuffix("$") else s.name

      def name(tpe: TypeRepr): Expr[String] = tpe.dealias match {
        case matchedTpe @ TermRef(typeRepr, name) if matchedTpe.typeSymbol.flags.is(Flags.Module) =>
          Expr(name.stripSuffix("$"))
        case TermRef(typeRepr, name) =>
          Expr(name)
        case matchedTpe =>
          Expr(normalizedName(matchedTpe.typeSymbol))
      }

      def ownerNameChain(sym: Symbol): List[String] =
        if sym.isNoSymbol then List.empty
        else if sym == defn.EmptyPackageClass then List.empty
        else if sym == defn.RootPackage then List.empty
        else if sym == defn.RootClass then List.empty
        else ownerNameChain(sym.owner) :+ normalizedName(sym)

      def owner(tpe: TypeRepr): Expr[String] = Expr(
        ownerNameChain(tpe.dealias.typeSymbol.maybeOwner).mkString(".")
      )

      def typeInfo(tpe: TypeRepr): Expr[ConfigMetaTypeId] = tpe match {

        case AppliedType(tpe, args) =>
          '{
            ConfigMetaTypeId(
              Some(${ owner(tpe) }),
              ${ name(tpe) },
              ${ Expr.ofList(args.map(typeInfo)) },
            )
          }
        case _ =>
          '{ ConfigMetaTypeId(Some(${ owner(tpe) }), ${ name(tpe) }, Nil) }
      }
      typeInfo(TypeRepr.of[T])
    }

  }

}
