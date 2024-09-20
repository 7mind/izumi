package izumi.distage.config.codec

import izumi.distage.config.codec.ConfigMetaType.ConfigField
import izumi.distage.config.model.ConfigDoc
import izumi.fundamentals.platform.reflection.ReflectionUtil
import pureconfig.generic.derivation.Utils

import scala.compiletime.ops.int.+
import scala.compiletime.*
import scala.deriving.Mirror
import scala.util.chaining.*

object MetaInstances {

  // magnolia on scala3 abuses inlines and requires high inlines limit (e.g. -Xmax-inlines:1024).
  // so we had to re-implement derivation manually and copy-paste the type id logic from Magnolia
  object configReaderDerivation {
    transparent inline def summonDIConfigMeta[A]: DIConfigMeta[A] =
      summonFrom {
        case reader: DIConfigMeta[A] => reader
        case m @ given Mirror.Of[A] => derived[A](using m)
      }

    transparent inline def derived[A](using m: Mirror.Of[A]): DIConfigMeta[A] = {
      inline m match {
        case m @ given Mirror.ProductOf[A] => derivedProduct(using m)
        case m @ given Mirror.SumOf[A] => derivedSum(using m)
      }
    }

    transparent inline def derivedProduct[A](using m: Mirror.ProductOf[A]): DIConfigMeta[A] = {
      inline erasedValue[A] match {
        case _: Tuple =>
          DIConfigMeta[A](ConfigMetaType.TUnknown())

        case _ =>
          val tpe: ConfigMetaType = {
            val labels: Array[String] = Utils.transformedLabels[A](PureconfigInstances.configReaderDerivation.fieldMapping).toArray

            val codecs = readTuple[m.MirroredElemTypes, 0]

            import izumi.fundamentals.collections.IzCollections.*
            val annos = fieldAnnos[A].map { case (k, v) => (PureconfigInstances.configReaderDerivation.fieldMapping(k), v) }.toMultimap

            ConfigMetaType.TCaseClass(
              typeId[A],
              labels.iterator
                .zip(codecs).map {
                  case (label, reader) => ConfigField(label, reader.tpe, annos.get(label).flatMap(_.headOption))
                }.toSeq,
              typeDocs[A],
            )
          }
          DIConfigMeta[A](tpe)
      }
    }

    transparent inline def readTuple[T <: Tuple, N <: Int]: List[DIConfigMeta[Any]] =
      inline erasedValue[T] match {
        case _: (h *: t) =>
          val reader = summonDIConfigMeta[h]
          val tReaders = readTuple[t, N + 1]
          reader.asInstanceOf[DIConfigMeta[Any]] :: tReaders

        case _: EmptyTuple =>
          Nil
      }

    transparent inline def derivedSum[A](using m: Mirror.SumOf[A]): DIConfigMeta[A] = {
      val options: Map[String, DIConfigMeta[A]] =
        Utils
          .transformedLabels[A](PureconfigInstances.configReaderDerivation.fieldMapping)
          .zip(deriveForSubtypes[m.MirroredElemTypes, A])
          .toMap

      val tpe: ConfigMetaType = ConfigMetaType.TSealedTrait(
        typeId[A],
        options.map {
          case (label, reader) => (label, reader.tpe)
        }.toSet,
        typeDocs[A],
      )

      DIConfigMeta[A](tpe)
    }

    transparent inline def deriveForSubtypes[T <: Tuple, A]: List[DIConfigMeta[A]] =
      inline erasedValue[T] match {
        case _: (h *: t) => deriveForSubtype[h, A] :: deriveForSubtypes[t, A]
        case _: EmptyTuple => Nil
      }

    transparent inline def deriveForSubtype[A0, A]: DIConfigMeta[A] =
      summonFrom {
        case reader: DIConfigMeta[A0] =>
          reader.asInstanceOf[DIConfigMeta[A]]

        case given Mirror.Of[A0] =>
          derived[A0].asInstanceOf[DIConfigMeta[A]]
      }

    import scala.quoted.{Expr, Quotes, Type}

    // https://github.com/softwaremill/magnolia/blob/scala3/core/src/main/scala/magnolia1/macro.scala#L135
    transparent inline def typeId[T]: ConfigMetaTypeId = ${ typeIdImpl[T] }

    transparent inline def typeDocs[T]: Option[String] = ${ typeDocsImpl[T] }

    transparent inline def fieldAnnos[T]: List[(String, String)] = ${ fieldAnnosImpl[T] }

    def fieldAnnosImpl[T: Type](using qctx: Quotes): Expr[List[(String, String)]] = {
      import qctx.reflect.*

      val tpe = TypeRepr.of[T]
      val caseClassFields = tpe.typeSymbol.caseFields
      val idAnnotationSym: Symbol = TypeRepr.of[ConfigDoc].typeSymbol

      val fieldNames = caseClassFields.flatMap {
        f =>
          ReflectionUtil
            .findSymbolAnnoString(f, idAnnotationSym)
            .map(doc => f.name -> doc)
      }

      Expr.ofList(fieldNames.map(Expr.apply))
    }

    def typeDocsImpl[T: Type](using qctx: Quotes): Expr[Option[String]] = {
      import qctx.reflect.*

      val idAnnotationSym: Symbol = TypeRepr.of[ConfigDoc].typeSymbol
      val tpe = TypeRepr.of[T]

      Expr(ReflectionUtil.findTypeAnnoString(tpe, idAnnotationSym))
    }

    def typeIdImpl[T: Type](using qctx: Quotes): Expr[ConfigMetaTypeId] = {
      import qctx.reflect.*

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
