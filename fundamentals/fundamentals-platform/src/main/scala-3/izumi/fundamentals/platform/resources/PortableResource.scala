package izumi.fundamentals.platform.resources

import scala.quoted.{Expr, Quotes, Type}
import izumi.fundamentals.platform.build.BuildAttributes

object PortableResource {
  inline def embedSources(inline pathExpr: String): Map[String, String] = ${ PortableResourceMacro.embedSources('{ pathExpr }, '{ true }) }

  inline def embedSourcesUnchecked(inline pathExpr: String): Map[String, String] = ${ PortableResourceMacro.embedSources('{ pathExpr }, '{ false }) }

  inline def embedResources(inline path: String): Map[String, String] = ${ PortableResourceMacro.embedResources('{ path }, '{ true }) }

  inline def embedResourcesUnchecked(inline path: String): Map[String, String] = ${ PortableResourceMacro.embedResources('{ path }, '{ false }) }

  object PortableResourceMacro extends PortableResourceBase {
    def embedSources(
      _pathExpr: Expr[String],
      _check: Expr[Boolean],
    )(using quotes: Quotes
    ): Expr[Map[String, String]] = {
      import quotes.reflect.*

      val pathExpr = _pathExpr.valueOrAbort
      val check = _check.valueOrAbort

      val maybeRoot = BuildAttributes.sbtProjectRoot()
      val sources = doExtractSources(maybeRoot, pathExpr)

      if (check && sources.isEmpty) {
        report.errorAndAbort(s"empty result while enumerating sources with pathExpr=$pathExpr at root $maybeRoot")
      }
      liftMap(sources)
    }

    def embedResources(_path: Expr[String], _check: Expr[Boolean])(using quotes: Quotes): Expr[Map[String, String]] = {
      import quotes.reflect.*

      val path = _path.valueOrAbort
      val check = _check.valueOrAbort

      val resources = extractResourceContents(path)

      if (check && resources.isEmpty) {
        report.errorAndAbort(s"empty result while enumerating $path")
      }
      liftMap(resources)
    }

    /**
      * Lift entries into a `Map[String, String]` expression, splitting each oversized value into
      * constant-pool-safe chunks re-joined at runtime. See [[PortableResourceBase.chunkString]].
      */
    private def liftMap(entries: Seq[(String, String)])(using Quotes): Expr[Map[String, String]] = {
      val entryExprs = entries.map {
        case (key, content) =>
          '{ (${ Expr(key) }, ${ liftContent(content) }) }
      }
      '{ ${ Expr.ofList(entryExprs.toList) }.toMap }
    }

    private def liftContent(content: String)(using Quotes): Expr[String] = {
      val chunks = chunkString(content)
      if (chunks.size <= 1) {
        Expr(content)
      } else {
        '{ ${ Expr.ofList(chunks.toList.map(Expr(_))) }.mkString }
      }
    }

  }
}
