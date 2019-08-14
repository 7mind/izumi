package izumi.logstage.macros

sealed trait ExtractedName {
  def str: String
}

object ExtractedName {
  case class NString(s: String) extends ExtractedName {
    override def str: String = s
  }
  case class NChar(c: Char) extends ExtractedName {
    override def str: String = c.toString
  }
}
