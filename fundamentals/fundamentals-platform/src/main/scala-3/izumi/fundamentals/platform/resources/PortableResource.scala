package izumi.fundamentals.platform.resources

import scala.quoted.{Expr, Quotes, Type}
import izumi.fundamentals.platform.build.BuildAttributes

object PortableResource {
  inline def embedSources(inline pathExpr: String
  ): Map[String, String] = ${PortableResourceMacro.embedSources('{pathExpr}, '{true})}

  inline def embedSourcesUnchecked(inline pathExpr: String
  ): Map[String, String] = ${PortableResourceMacro.embedSources('{pathExpr}, '{false})}

  inline def embedResources(inline path: String): Map[String, String] = ${PortableResourceMacro.embedResources('{path}, '{true})}

  inline def embedResourcesUnchecked(inline path: String): Map[String, String] = ${PortableResourceMacro.embedResources('{path}, '{false})}

  object PortableResourceMacro extends PortableResourceBase {
    def embedSources(
                      _pathExpr: Expr[String],
                      _check: Expr[Boolean])(using quotes: Quotes): Expr[Map[String, String]] = {
      import quotes.reflect.*

      val pathExpr = _pathExpr.valueOrAbort
      val check = _check.valueOrAbort

      val maybeRoot = BuildAttributes.sbtProjectRoot()
      val sources = doExtractSources(maybeRoot, pathExpr)

      if (check && sources.isEmpty) {
        report.errorAndAbort(s"empty result while enumerating sources with pathExpr=$pathExpr at root $maybeRoot")
      }
      Expr(sources.toMap)
    }

    def embedResources(_path: Expr[String], _check: Expr[Boolean])(using quotes: Quotes): Expr[Map[String, String]] = {
      import quotes.reflect.*

      val path = _path.valueOrAbort
      val check = _check.valueOrAbort

      val resources = extractResourceContents(path)

      if (check && resources.isEmpty) {
        report.errorAndAbort(s"empty result while enumerating $path")
      }
      Expr(resources.toMap)
    }

  }
}
