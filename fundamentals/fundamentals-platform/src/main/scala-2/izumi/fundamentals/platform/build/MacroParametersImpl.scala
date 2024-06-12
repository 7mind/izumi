package izumi.fundamentals.platform.build

import izumi.fundamentals.reflection.ReflectionUtil

import scala.reflect.macros.blackbox

object MacroParametersImpl {
  def extractAttrMacro(c: blackbox.Context)(name: c.Expr[String]): c.Expr[Option[String]] = {
    val nameStr = ReflectionUtil.getStringLiteral(c)(name.tree)
    attr(c, nameStr)
  }

  def extractAttrBoolMacro(c: blackbox.Context)(name: c.Expr[String]): c.Expr[Option[Boolean]] = {
    val nameStr = ReflectionUtil.getStringLiteral(c)(name.tree)
    attrBool(c, nameStr)
  }

  def gitRepoClean(c: blackbox.Context)(): c.Expr[Option[Boolean]] = attrBool(c, "git-repo-clean")

  def sbtIsInsideCI(c: blackbox.Context)(): c.Expr[Option[Boolean]] = attrBool(c, "is-ci")

  def gitBranch(c: blackbox.Context)(): c.Expr[Option[String]] = attr(c, "git-branch")

  def gitHeadCommit(c: blackbox.Context)(): c.Expr[Option[String]] = attr(c, "git-head-commit")

  def gitDescribedVersion(c: blackbox.Context)(): c.Expr[Option[String]] = attr(c, "git-described-version")

  def scalaVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = attr(c, "scala-version")

  def scalaVersionsMacro(c: blackbox.Context)(): c.Expr[Option[String]] = attr(c, "scala-versions")

  def projectGroupIdMacro(c: blackbox.Context)(): c.Expr[Option[String]] = attr(c, "product-group")

  def sbtVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = attr(c, "sbt-version")

  def scalatestVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = attr(c, "scalatest-version")

  def projectVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = attr(c, "product-version")

  def projectNameMacro(c: blackbox.Context)(): c.Expr[Option[String]] = attr(c, "product-name")

  private def attrBool(c: blackbox.Context, name: String): c.Expr[Option[Boolean]] = {
    val value = getAttr(c, name)

    import c.universe.*

    val isTrue = value.map(_.toLowerCase).map(v => v == "true" || v == "1")
    c.Expr[Option[Boolean]](q"$isTrue")
  }

  private def attr(c: blackbox.Context, name: String): c.Expr[Option[String]] = {
    val value = getAttr(c, name)

    import c.universe.*
    c.Expr[Option[String]](q"$value")
  }

  private def getAttr(c: blackbox.Context, name: String): Option[String] = {
    val prefix = s"$name="
    val value = c.settings.filter(_.startsWith(prefix)).map(_.stripPrefix(prefix)).lastOption
    if (value.isEmpty) {
      c.info(c.enclosingPosition, s"Undefined macro parameter $name, add `-Xmacro-settings:$prefix<value>` into `scalac` options", force = true)
    }
    value
  }

}
