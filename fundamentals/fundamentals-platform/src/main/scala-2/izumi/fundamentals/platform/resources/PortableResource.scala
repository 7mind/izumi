package izumi.fundamentals.platform.resources

import izumi.fundamentals.platform.build

import java.nio.file.Paths
import scala.annotation.unused
import scala.language.experimental.macros
import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

object PortableResourceMacro extends PortableResourceBase {
  def makeEmbedSources(
    c: blackbox.Context
  )(pathExpr: c.Expr[String]
  ): c.Expr[Map[String, String]] = {
    processSources[Map[String, String]](c)(pathExpr, check = true)
  }

  def makeEmbedSourcesUnchecked(
    c: blackbox.Context
  )(pathExpr: c.Expr[String]
  ): c.Expr[Map[String, String]] = {
    processSources[Map[String, String]](c)(pathExpr, check = false)
  }

  def makeEmbedResources(
    c: blackbox.Context
  )(path: c.Expr[String]
  ): c.Expr[Map[String, String]] = {
    processResources(c)(path, check = true)
  }

  def makeEmbedResourcesUnchecked(
    c: blackbox.Context
  )(path: c.Expr[String]
  ): c.Expr[Map[String, String]] = {
    processResources(c)(path, check = false)
  }

  private def processSources[T](
    c: blackbox.Context
  )(_pathExpr: c.Expr[String],
    check: Boolean,
  )(implicit @unused l: c.universe.Liftable[T]
  ): c.Expr[T] = {
    val pathExpr = getStringLiteral(c)(_pathExpr.tree)

    val maybeRoot = build.findProjectRoot(Paths.get(c.enclosingPosition.source.path)).map(_.toFile.getCanonicalPath)
    val sources = doExtractSources(maybeRoot, pathExpr)

    import c.universe.*
    if (check && sources.isEmpty) {
      c.error(
        c.enclosingPosition,
        s"empty result while enumerating sources with pathExpr=$pathExpr at root $maybeRoot",
      )
    }

    c.Expr(q"${sources.toMap[String, String]}")
  }

  private def processResources[T](
    c: blackbox.Context
  )(path: c.Expr[String],
    check: Boolean,
  ): c.Expr[Map[String, String]] = {
    import c.universe.*
    val sourcePath = getStringLiteral(c)(path.tree)

    val resources = extractResourceContents(sourcePath)
    if (check && resources.isEmpty) {
      c.error(c.enclosingPosition, s"empty result while enumerating $sourcePath")
    }

    val resourceTrees = resources.map {
      case (resourcePath, content) =>
        q"$resourcePath -> $content"
    }

    c.Expr[Map[String, String]](q"Map(..$resourceTrees)")
  }

  private def getStringLiteral(c: blackbox.Context)(tree: c.universe.Tree): String = {
    findStringLiteral(tree).getOrElse(
      c.abort(c.enclosingPosition, "must use string literal")
    )
  }

  private def findStringLiteral(tree: Universe#Tree): Option[String] = {
    tree.collect {
      case l: Universe#LiteralApi if l.value.value.isInstanceOf[String] =>
        l.value.value.asInstanceOf[String]
    }.headOption
  }
}

object PortableResource {
  def embedSources(
    pathExpr: String
  ): Map[String, String] = macro PortableResourceMacro.makeEmbedSources

  def embedSourcesUnchecked(
    pathExpr: String
  ): Map[String, String] = macro PortableResourceMacro.makeEmbedSourcesUnchecked

  def embedResources(path: String): Map[String, String] = macro PortableResourceMacro.makeEmbedResources

  def embedResourcesUnchecked(path: String): Map[String, String] = macro PortableResourceMacro.makeEmbedResourcesUnchecked
}
