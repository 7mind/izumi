package com.github.pshirshov.izumi.fundamentals.reflection

import java.nio.file.{Path, Paths}
import java.time.LocalDateTime

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
  def buildTimestamp(): LocalDateTime = macro buildTimestampMacro

  def projectRoot(): Option[String] = macro findProjectRootMacro

  def extractSbtProjectGroupId(): Option[String] = macro extractProjectGroupIdMacro

  def extractSbtProjectVersion(): Option[String] = macro extractProjectVersionMacro

  def extractSbtVersion(): Option[String] = macro extractSbtVersionMacro

  def extractScalatestVersion(): Option[String] = macro extractScalatestVersionMacro

  def extractScalaVersion(): Option[String] = macro extractScalaVersionMacro

  def extractScalaVersions(): Option[String] = macro extractScalaVersionsMacro

  def extract(name: String): Option[String] = macro extractAttrMacro

  def buildTimestampMacro(c: blackbox.Context)(): c.Expr[LocalDateTime] = {
    import c.universe._

    reify {
      LocalDateTime.now()
    }
  }


  def extractAttrMacro(c: blackbox.Context)(name: c.Expr[String]): c.Expr[Option[String]] = {
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

  def extractScalaVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "scala-version")
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

  def findProjectRootMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    val srcPath = Paths.get(c.enclosingPosition.source.path)
    import c.universe._

    val result = projectRoot(srcPath).map(_.toFile.getCanonicalPath)

    c.Expr[Option[String]](q"$result")
  }

  private def projectRoot(cp: Path): Option[Path] = {
    if (cp.resolve("build.sbt").toFile.exists()) {
      Some(cp)
    } else {
      val parent = cp.getParent

      if (parent == null || parent == cp.getRoot) {
        None
      } else {
        projectRoot(parent)
      }
    }

  }

}
