package izumi.fundamentals.platform.language

import scala.math.Ordering.Implicits._

object IzScala {

  sealed trait ScalaRelease {
    def parts: Seq[Int]
  }

  object ScalaRelease {
    def parse(versionString: String): ScalaRelease = {
      val parts = versionString.split('.').toList
      parts match {
        case "2" :: "12" :: bugfix :: Nil =>
          ScalaRelease.`2_12`(Integer.parseInt(bugfix))
        case "2" :: "13" :: bugfix :: Nil =>
          ScalaRelease.`2_13`(Integer.parseInt(bugfix))
        case major :: minor :: bugfix :: Nil =>
          ScalaRelease.Unsupported(Seq(Integer.parseInt(major), Integer.parseInt(minor), Integer.parseInt(bugfix)))
        case _ =>
          ScalaRelease.Unknown(versionString)
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

  def scalaRelease: ScalaRelease = ScalaRelease.parse(scala.util.Properties.versionNumberString)
}
