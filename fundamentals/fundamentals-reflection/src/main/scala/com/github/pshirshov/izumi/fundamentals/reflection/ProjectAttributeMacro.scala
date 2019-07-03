package com.github.pshirshov.izumi.fundamentals.reflection

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/*
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:sbt-version=${sbtVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}",
      s"-Xmacro-settings:scaltest-version=${V.scalatest}",
    ),
 */
object ProjectAttributeMacro {
  def extractSbtProjectGroupId(): Option[String] = macro extractProjectGroupIdMacro

  def extractSbtProjectVersion(): Option[String] = macro extractProjectVersionMacro

  def extractSbtVersion(): Option[String] = macro extractSbtVersionMacro
  def extractScalatestVersion(): Option[String] = macro extractScalatestVersionMacro

  def extractScalaVersions(): Option[String] = macro extractScalaVersionsMacro

  def extract(name: String): Option[String] = macro extractAttrMacro

  def extractAttrMacro(c: blackbox.Context)(name:  c.Expr[String]): c.Expr[Option[String]] = {
    val nameStr = TreeTools.stringLiteral(c)(c.universe)(name.tree)
    extractAttr(c, nameStr)
  }

  def extractProjectGroupIdMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "product-group")
  }

  def extractSbtVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "sbt-version")
  }

  def extractScalatestVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "scaltest-version")
  }

  def extractScalaVersionsMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "scala-versions")
  }

  def extractProjectVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "product-version")
  }

  private def extractAttr(c: blackbox.Context, name: String): c.Expr[Option[String]] = {
    val prefix = s"$name="
    val value = c.settings.find(_.startsWith(prefix)).map(_.stripPrefix(prefix))
    if (value.isEmpty) {
      c.warning(c.enclosingPosition, s"Undefined macro parameter $name, add `-Xmacro-settings:$prefix<value>` into `scalac` options")
    }

    import c.universe._
    c.Expr[Option[String]](q"$value")
  }

}
