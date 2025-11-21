package izumi.distage.reflection.macros

import izumi.distage.model.reflection.*
import izumi.fundamentals.reflection.ReflectiveCall

import scala.quoted.{Expr, Quotes, Type}

final class FunctoidParametersMacro[Q <: Quotes](using val qctx: Q)(idExtractor: IdExtractor[qctx.type]) extends FunctoidParametersMacroBase[Q] {

  import qctx.reflect.*

  extension (t: Either[TypeTree, TypeRepr]) {
    private def _tpe: TypeRepr = t match {
      case Right(t) => t
      case Left(t) => t.tpe
    }
  }

  override def makeParam(
    name: String,
    tpe: Either[TypeTree, TypeRepr],
    mbSym: Option[Symbol],
    annotSym: Option[Symbol],
    annotTpe: Either[TypeTree, TypeRepr],
    ignoreDuringImplicitsSearch: List[Symbol],
  ): Expr[LinkedParameter] = {
    val identifier = idExtractor.extractId(name, annotSym, annotTpe)

    val tpeRepr = tpe._tpe

    val isByName = tpeRepr match {
      case ByNameType(_) => true
      case _ => false
    }

    val wasGeneric = tpeRepr.typeSymbol.isTypeParam || mbSym.exists {
      s =>
        ReflectiveCall.call[TypeRepr](qctx.reflect.SymbolMethods, "info", s).typeSymbol.isTypeParam
    } // deem abstract type members as generic? No. Because we don't do that in Scala 2 version.

    '{
      val safeType = ${ safeTypeFromRepr(tpeRepr, ignoreDuringImplicitsSearch) }
      LinkedParameter(
        SymbolInfo(
          name = ${ Expr(name) },
          finalResultType = safeType,
          isByName = ${ Expr(isByName) },
          wasGeneric = ${ Expr(wasGeneric) },
        ),
        ${ makeKeyFromRepr('{ safeType }, identifier, ignoreDuringImplicitsSearch) },
      )
    }
  }

  private def makeKeyFromRepr(safeType: Expr[SafeType], id: Option[String], ignoreDuringImplicitsSearch: List[Symbol]): Expr[DIKey] = {
    id match {
      case Some(str) =>
        val idContractExpr = Expr
          .summonIgnoring[IdContract[String]](ignoreDuringImplicitsSearch*)
          .getOrElse(qctx.reflect.report.errorAndAbort(s"No implicit value found for ${Type.show[IdContract[String]]}"))

        '{ DIKey.IdKey[String](${ safeType }, ${ Expr(str) }, None)(using ${ idContractExpr }) }
      case None =>
        '{ DIKey.TypeKey(${ safeType }, None) }
    }
  }

  private def safeTypeFromRepr(tpe: TypeRepr, ignoreDuringImplicitsSearch: List[Symbol]): Expr[SafeType] = {
    dropByName(tpe).asType match {
      case '[a] => FunctoidMacroHelpers.generateSafeType[a](ignoreDuringImplicitsSearch)
      case _ => report.errorAndAbort(s"Cannot generate SafeType from ${tpe.show}, probably that's a bug in Functoid macro")
    }
  }

  private def dropByName(tpe: TypeRepr): TypeRepr = {
    tpe match {
      case ByNameType(u) => u
      case _ => tpe
    }
  }

}
