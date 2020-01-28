package izumi.fundamentals.platform.build

import java.nio.file.{Path, Paths}
import java.time.LocalDateTime

import izumi.fundamentals.reflection.ReflectionUtil

import scala.language.experimental.macros
import scala.annotation.tailrec
import scala.reflect.macros.blackbox

/*
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:sbt-version=${sbtVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}",
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
    ),
 */
object ProjectAttributeMacro {
  def buildTimestamp(): LocalDateTime = macro ProjectAttributeMacroImpl.buildTimestampMacro

  def projectRoot(): Option[String] = macro ProjectAttributeMacroImpl.findProjectRootMacro

  def extractSbtProjectGroupId(): Option[String] = macro ProjectAttributeMacroImpl.extractProjectGroupIdMacro

  def extractSbtProjectVersion(): Option[String] = macro ProjectAttributeMacroImpl.extractProjectVersionMacro

  def extractSbtVersion(): Option[String] = macro ProjectAttributeMacroImpl.extractSbtVersionMacro

  def extractScalatestVersion(): Option[String] = macro ProjectAttributeMacroImpl.extractScalatestVersionMacro

  def extractScalaVersion(): Option[String] = macro ProjectAttributeMacroImpl.extractScalaVersionMacro

  def extractScalaVersions(): Option[String] = macro ProjectAttributeMacroImpl.extractScalaVersionsMacro

  def extract(name: String): Option[String] = macro ProjectAttributeMacroImpl.extractAttrMacro
}

object ProjectAttributeMacroImpl {

  def buildTimestampMacro(c: blackbox.Context)(): c.Expr[LocalDateTime] = {
    import c.universe._

    val time = LocalDateTime.now()
    c.Expr[LocalDateTime] {
      q"{_root_.java.time.LocalDateTime.of(${time.getYear}, ${time.getMonthValue}, ${time.getDayOfMonth}, ${time.getHour}, ${time.getMinute}, ${time.getSecond}, ${time.getNano})}"
    }
  }

  def extractAttrMacro(c: blackbox.Context)(name: c.Expr[String]): c.Expr[Option[String]] = {
    val nameStr = ReflectionUtil.getStringLiteral(c)(name.tree)
    extractAttr(c, nameStr)
  }

  def extractProjectGroupIdMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "product-group")
  }

  def extractSbtVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "sbt-version")
  }

  def extractScalatestVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "scalatest-version")
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

  @tailrec
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
