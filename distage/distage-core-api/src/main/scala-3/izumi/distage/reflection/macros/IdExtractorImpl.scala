package izumi.distage.reflection.macros

import izumi.distage.model.definition.Id
import izumi.fundamentals.platform.reflection.ReflectionUtil

import scala.quoted.Quotes

final class IdExtractorImpl[Q <: Quotes](using val qctx: Q) extends IdExtractor[Q] {
  import qctx.reflect.*

  private val idAnnotationSym: Symbol = TypeRepr.of[Id].typeSymbol
  private val maybeJavaxNamedAnnotationSym: Option[Symbol] = scala.util.Try(Symbol.requiredClass("javax.inject.Named")).toOption

  def extractId(name: String, annotSym: Option[Symbol], annotTpe: Either[TypeTree, TypeRepr]): Option[String] = {
    ReflectionUtil
      .readTypeOrSymbolDIAnnotation(idAnnotationSym)(name, annotSym, annotTpe) {
        case aterm @ Apply(Select(New(_), _), c :: _) =>
          c.asExprOf[String].value.orElse {
            report.errorAndAbort(s"distage.Id annotation expects one literal String argument but got ${c.show} in tree ${aterm.show} ($aterm)")
          }
        case aterm =>
          report.errorAndAbort(s"distage.Id annotation expects one literal String argument but got malformed tree ${aterm.show} ($aterm)")
      }.orElse {
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
}
