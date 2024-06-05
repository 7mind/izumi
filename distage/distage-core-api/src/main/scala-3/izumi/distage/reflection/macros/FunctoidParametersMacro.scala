package izumi.distage.reflection.macros

import izumi.distage.model.definition.Id
import izumi.distage.model.reflection.*
import izumi.fundamentals.platform.reflection.ReflectionUtil
import izumi.reflect.Tag

import scala.quoted.{Expr, Quotes}

final class FunctoidParametersMacro[Q <: Quotes & Singleton](using val qctx: Q) extends FunctoidParametersMacroBase[Q] {

  import qctx.reflect.*

  private val idAnnotationSym: Symbol = TypeRepr.of[Id].typeSymbol
  private val maybeJavaxNamedAnnotationSym: Option[Symbol] = scala.util.Try(Symbol.requiredClass("javax.inject.Named")).toOption

  extension (t: Either[TypeTree, TypeRepr]) {
    private def _tpe: TypeRepr = t match {
      case Right(t) => t
      case Left(t) => t.tpe
    }
  }

  def makeParam(name: String, tpe: Either[TypeTree, TypeRepr], annotSym: Option[Symbol]): Expr[LinkedParameter] = {
    makeParam(name, tpe, annotSym, tpe)
  }

  def makeParam(name: String, tpe: Either[TypeTree, TypeRepr], annotSym: Option[Symbol], annotTpe: Either[TypeTree, TypeRepr]): Expr[LinkedParameter] = {
    val identifier = {
      val mbIdIdentifier = ReflectionUtil
        .readTypeOrSymbolDIAnnotation(idAnnotationSym)(name, annotSym, annotTpe) {
          case aterm @ Apply(Select(New(_), _), c :: _) =>
            c.asExprOf[String].value.orElse {
              report.errorAndAbort(s"distage.Id annotation expects one literal String argument but got ${c.show} in tree ${aterm.show} ($aterm)")
            }
          case aterm =>
            report.errorAndAbort(s"distage.Id annotation expects one literal String argument but got malformed tree ${aterm.show} ($aterm)")
        }
      mbIdIdentifier.orElse {
        maybeJavaxNamedAnnotationSym.flatMap {
          namedAnnoSym =>
            ReflectionUtil.readTypeOrSymbolDIAnnotation(namedAnnoSym)(name, annotSym, annotTpe) {
              case aterm @ Apply(Select(New(_), _), c :: _) =>
                c.asExprOf[String].value.orElse {
                  report.errorAndAbort(s"javax.inject.Named annotation expects one literal String argument but got ${c.show} in tree ${aterm.show} ($aterm)")
                }
              case aterm =>
                report.errorAndAbort(s"javax.inject.Named annotation expects one literal String argument but got malformed tree ${aterm.show} ($aterm)")
            }
        }
      }
    }

    val tpeRepr = tpe._tpe

    val isByName = tpeRepr match {
      case ByNameType(_) => true
      case _ => false
    }

    val wasGeneric = tpeRepr.typeSymbol.isTypeParam // deem abstract type members as generic? No. Because we don't do that in Scala 2 version.

    '{
      LinkedParameter(
        SymbolInfo(
          name = ${ Expr(name) },
          finalResultType = ${ safeTypeFromRepr(tpeRepr) },
          isByName = ${ Expr(isByName) },
          wasGeneric = ${ Expr(wasGeneric) },
        ),
        ${ makeKeyFromRepr(tpeRepr, identifier) },
      )
    }
  }

  private def makeKeyFromRepr(tpe: TypeRepr, id: Option[String]): Expr[DIKey] = {
    val safeTpe = safeTypeFromRepr(tpe)
    id match {
      case Some(str) =>
        val strExpr = Expr(str)
        '{ new DIKey.IdKey($safeTpe, $strExpr, None)(scala.compiletime.summonInline[IdContract[String]]) }
      case None =>
        '{ new DIKey.TypeKey($safeTpe, None) }
    }
  }

  private def safeTypeFromRepr(tpe: TypeRepr): Expr[SafeType] = {
    dropByName(tpe).asType match {
      case '[a] =>
        '{ SafeType.get[a](using scala.compiletime.summonInline[Tag[a]]) }
      case _ =>
        report.errorAndAbort(s"Cannot generate SafeType from ${tpe.show}, probably that's a bug in Functoid macro")
    }
  }

  private def dropByName(tpe: TypeRepr): TypeRepr = {
    tpe match {
      case ByNameType(u) => u
      case _ => tpe
    }
  }

}
