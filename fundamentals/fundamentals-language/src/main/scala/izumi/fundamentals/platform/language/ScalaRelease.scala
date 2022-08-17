package izumi.fundamentals.platform.language

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

  implicit lazy val ordering: Ordering[ScalaRelease] = {
    import scala.math.Ordering.Implicits.*
    Ordering.fromLessThan(_.parts < _.parts)
  }

  case class `2_12`(bugfix: Int) extends ScalaRelease {
    override def parts: Seq[Int] = Seq(2, 12, bugfix)
  }

  case class `2_13`(bugfix: Int) extends ScalaRelease {
    override def parts: Seq[Int] = Seq(2, 13, bugfix)
  }

  case class `3_0`(bugfix: Int) extends ScalaRelease {
    override def parts: Seq[Int] = Seq(3, 0, bugfix)
  }

  case class Unsupported(parts: Seq[Int]) extends ScalaRelease

  case class Unknown(verString: String) extends ScalaRelease {
    override def parts: Seq[Int] = Seq.empty
  }
}