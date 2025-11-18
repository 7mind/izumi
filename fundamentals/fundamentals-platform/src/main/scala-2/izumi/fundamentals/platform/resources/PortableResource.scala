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
    val cl = Thread.currentThread().getContextClassLoader
    processResources[Map[String, String]](c)(path, check = true)(r => convertResources(r, cl))
  }

  def makeEmbedResourcesUnchecked(
    c: blackbox.Context
  )(path: c.Expr[String]
  ): c.Expr[Map[String, String]] = {
    val cl = Thread.currentThread().getContextClassLoader
    processResources[Map[String, String]](c)(path, check = false)(r => convertResources(r, cl))
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
  )(h: List[String] => T
  )(implicit @unused l: c.universe.Liftable[T]
  ): c.Expr[T] = {
    import c.universe.*
    val sourcePath = getStringLiteral(c)(path.tree)
    val names = doExtractResources(sourcePath)

    if (check && names.isEmpty) {
      c.error(c.enclosingPosition, s"empty result while enumerating $sourcePath")
    }
    val out = h(names)
    c.Expr(q"$out")
  }

  private def getStringLiteral(c: blackbox.Context)(tree: c.universe.Tree): String = {
    findStringLiteral(tree).getOrElse(
      c.abort(c.enclosingPosition, "must use string literal")
    )
  }

  private def getBoolLiteral(c: blackbox.Context)(tree: c.universe.Tree): Boolean = {
    findBoolLiteral(tree).getOrElse(
      c.abort(c.enclosingPosition, "must use bool literal")
    )
  }

  private def getIntLiteral(c: blackbox.Context)(tree: c.universe.Tree): Int = {
    findIntLiteral(tree).getOrElse(
      c.abort(c.enclosingPosition, "must use int literal")
    )
  }

  private def findStringLiteral(tree: Universe#Tree): Option[String] = {
    tree.collect {
      case l: Universe#LiteralApi if l.value.value.isInstanceOf[String] =>
        l.value.value.asInstanceOf[String]
    }.headOption
  }

  private def findBoolLiteral(tree: Universe#Tree): Option[Boolean] = {
    tree.collect {
      case l: Universe#LiteralApi if l.value.value.isInstanceOf[Boolean] =>
        l.value.value.asInstanceOf[Boolean]
    }.headOption
  }

  private def findIntLiteral(tree: Universe#Tree): Option[Int] = {
    tree.collect {
      case l: Universe#LiteralApi if l.value.value.isInstanceOf[Int] =>
        l.value.value.asInstanceOf[Int]
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
