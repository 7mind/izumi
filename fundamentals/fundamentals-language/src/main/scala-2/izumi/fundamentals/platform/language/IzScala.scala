package izumi.fundamentals.platform.language

import scala.language.experimental.macros
import scala.math.Ordering.Implicits._
import scala.reflect.macros.blackbox

object IzScala {

  sealed trait ScalaRelease {
    def parts: Seq[Int]
  }

  object ScalaRelease {
    def parse(versionString: String): ScalaRelease = {
      val parts = versionString.split('.').toList
      parts match {
        case "2" :: "12" :: ParseInt(bugfix) :: Nil =>
          ScalaRelease.`2_12`(bugfix)
        case "2" :: "13" :: ParseInt(bugfix) :: Nil =>
          ScalaRelease.`2_13`(bugfix)
        case ParseInt(major) :: ParseInt(minor) :: ParseInt(bugfix) :: Nil =>
          ScalaRelease.Unsupported(Seq(major, minor, bugfix))
        case _ =>
          ScalaRelease.Unknown(versionString)
      }
    }
    private[this] object ParseInt {
      def unapply(str: String): Option[Int] = {
        try { Some(Integer.parseInt(str)) }
        catch { case _: NumberFormatException => None }
      }
    }

    implicit lazy val ordering: Ordering[ScalaRelease] = Ordering.fromLessThan(_.parts < _.parts)

    case class `2_12`(bugfix: Int) extends ScalaRelease {
      override def parts: Seq[Int] = Seq(2, 12, bugfix)
    }

    case class `2_13`(bugfix: Int) extends ScalaRelease {
      override def parts: Seq[Int] = Seq(2, 13, bugfix)
    }

    case class Unsupported(parts: Seq[Int]) extends ScalaRelease

    case class Unknown(verString: String) extends ScalaRelease {
      override def parts: Seq[Int] = Seq.empty
    }
  }

  def scalaRelease: IzScala.ScalaRelease = macro ScalaReleaseMacro.scalaRelease

  object ScalaReleaseMacro {

    def scalaRelease(c: blackbox.Context): c.Expr[IzScala.ScalaRelease] = {
      import c.universe._

      ScalaRelease.parse(scala.util.Properties.versionNumberString) match {
        case ScalaRelease.`2_12`(bugfix) =>
          c.Expr[ScalaRelease](q"${symbolOf[ScalaRelease.`2_12`].asClass.companion}($bugfix)")
        case ScalaRelease.`2_13`(bugfix) =>
          c.Expr[ScalaRelease](q"${symbolOf[ScalaRelease.`2_13`].asClass.companion}($bugfix)")
        case ScalaRelease.Unsupported(parts) =>
          c.Expr[ScalaRelease](q"${symbolOf[ScalaRelease.Unsupported].asClass.companion}(..${parts.toList})")
        case ScalaRelease.Unknown(verString) =>
          c.Expr[ScalaRelease](q"${symbolOf[ScalaRelease.Unknown].asClass.companion}($verString)")
      }
    }

  }

}
