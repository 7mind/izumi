package izumi.fundamentals.platform.build

import java.nio.file.{Path, Paths}
import java.time.LocalDateTime

import izumi.fundamentals.reflection.ReflectionUtil

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object BuildAttributeMacroImpl {
  private lazy val time = LocalDateTime.now()

  def javaVendorUrl(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "java.vendor.url")
  def javaVmVendor(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "java.vm.vendor")
  def javaVersion(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "java.version")
  def javaHome(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "java.home")
  def javaSpecificationVersion(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "java.specification.version")
  def javaVmSpecificationVersion(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "java.vm.specification.version")

  def osName(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "os.name")
  def osVersion(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "os.version")

  def scalaHome(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "scala.home")

  def userHome(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "user.home")
  def userName(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "user.name")
  def userDir(c: blackbox.Context)(): c.Expr[Option[String]] = prop(c, "user.dir")

  def buildProperty(c: blackbox.Context)(name: c.Expr[String]): c.Expr[Option[String]] = {
    val nameStr = ReflectionUtil.getStringLiteral(c)(name.tree)
    prop(c, nameStr)
  }

  def buildTimestampMacro(c: blackbox.Context)(): c.Expr[LocalDateTime] = {
    import c.universe._

    c.Expr[LocalDateTime] {
      q"{_root_.java.time.LocalDateTime.of(${time.getYear}, ${time.getMonthValue}, ${time.getDayOfMonth}, ${time.getHour}, ${time.getMinute}, ${time.getSecond}, ${time.getNano})}"
    }
  }

  def sbtProjectRoot(c: blackbox.Context)(): c.Expr[Option[String]] = {
    val srcPath = Paths.get(c.enclosingPosition.source.path)
    import c.universe._

    val result = findProjectRoot(srcPath).map(_.toFile.getCanonicalPath)

    c.Expr[Option[String]](q"$result")
  }

  @tailrec
  private def findProjectRoot(cp: Path): Option[Path] = {
    if (cp.resolve("build.sbt").toFile.exists()) {
      Some(cp)
    } else {
      val parent = cp.getParent

      if (parent == null || parent == cp.getRoot) {
        None
      } else {
        findProjectRoot(parent)
      }
    }

  }

  private def prop(c: blackbox.Context, name: String): c.Expr[Option[String]] = {
    import c.universe._
    c.Expr[Option[String]](q"${Option(System.getProperty(name))}")
  }

}
