package izumi.fundamentals.platform.resources

import izumi.fundamentals.platform.build

import java.nio.file.Paths
import scala.language.experimental.macros
import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

object PortableResourceMacro extends PortableResourceBase {
  def makeEmbedSources(
    c: blackbox.Context
  )(pathExpr: c.Expr[String]
  ): c.Expr[Map[String, String]] = {
    processSources(c)(pathExpr, check = true)
  }

  def makeEmbedSourcesUnchecked(
    c: blackbox.Context
  )(pathExpr: c.Expr[String]
  ): c.Expr[Map[String, String]] = {
    processSources(c)(pathExpr, check = false)
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

  private def processSources(
    c: blackbox.Context
  )(_pathExpr: c.Expr[String],
    check: Boolean,
  ): c.Expr[Map[String, String]] = {
    val pathExpr = getStringLiteral(c)(_pathExpr.tree)

    val maybeRoot = build.findProjectRoot(Paths.get(c.enclosingPosition.source.path)).map(_.toFile.getCanonicalPath)
    val sources = doExtractSources(maybeRoot, pathExpr)

    if (check && sources.isEmpty) {
      c.error(
        c.enclosingPosition,
        s"empty result while enumerating sources with pathExpr=$pathExpr at root $maybeRoot",
      )
    }

    liftMap(c)(sources)
  }

  private def processResources(
    c: blackbox.Context
  )(path: c.Expr[String],
    check: Boolean,
  ): c.Expr[Map[String, String]] = {
    val sourcePath = getStringLiteral(c)(path.tree)

    val resources = extractResourceContents(sourcePath)
    if (check && resources.isEmpty) {
      c.error(c.enclosingPosition, s"empty result while enumerating $sourcePath")
    }

    liftMap(c)(resources)
  }

  /**
    * Build a `Map[String, String]` tree, splitting each oversized value into constant-pool-safe
    * chunks re-joined at runtime. See [[PortableResourceBase.chunkString]].
    */
  private def liftMap(c: blackbox.Context)(entries: Seq[(String, String)]): c.Expr[Map[String, String]] = {
    import c.universe.*
    val entryTrees = entries.map {
      case (key, content) =>
        q"new _root_.scala.Tuple2($key, ${liftContent(c)(content)})"
    }
    c.Expr[Map[String, String]](q"_root_.scala.collection.immutable.Map(..$entryTrees)")
  }

  private def liftContent(c: blackbox.Context)(content: String): c.universe.Tree = {
    import c.universe.*
    val chunks = chunkString(content)
    if (chunks.size <= 1) {
      q"$content"
    } else {
      q"_root_.scala.collection.immutable.List(..${chunks.map(ch => q"$ch")}).mkString"
    }
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
