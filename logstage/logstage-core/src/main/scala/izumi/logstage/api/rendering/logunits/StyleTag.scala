package izumi.logstage.api.rendering.logunits

sealed trait StyleTag {
  def render: String
  val isSelfClosing: Boolean = false
}
sealed trait BasicStyleTag extends StyleTag
object StyleTag {
  val RESET = "\u001b[0m"
  def apply(name: String, stylesheet: Map[String, Seq[BasicStyleTag]]): StyleTag = {
    name match {
      case "b" | "bold" => Bold
      case "i" | "italic" => Italic
      case "u" | "underlined" => Underlined
      case "r" | "reversed" => Reversed
      case Color(color) => ColorTag(color)
      case Custom(name) =>
        stylesheet
          .get(name)
          .fold(Ignore(name): StyleTag)(styles => CustomTag(name, styles))
      case n => Ignore(n)
    }
  }

  def renderTags(tags: Seq[StyleTag]): String = {
    tags.map(_.render).mkString("")
  }

  case object Bold extends BasicStyleTag {
    override def render: String = "\u001b[1m"
  }
  case object Italic extends BasicStyleTag {
    override def render: String = "\u001b[3m"
  }
  case object Underlined extends BasicStyleTag {
    override def render: String = "\u001b[4m"
  }
  case object Reversed extends BasicStyleTag {
    override def render: String = "\u001b[7m"
  }
  case class ColorTag(color: String) extends BasicStyleTag {
    override def render: String = color.toLowerCase match {
      case "black" => "\u001b[30m"
      case "red" => "\u001b[31m"
      case "green" => "\u001b[32m"
      case "yellow" => "\u001b[33m"
      case "blue" => "\u001b[34m"
      case "magenta" => "\u001b[35m"
      case "cyan" => "\u001b[36m"
      case "white" => "\u001b[37m"
      case _ => "" // fallback if color is unknown
    }
  }

  case class CustomTag(name: String, styles: Seq[BasicStyleTag]) extends StyleTag {
    override def render: String = {
      renderTags(styles)
    }
  }

  private object Color {
    def unapply(tag: String): Option[String] = {
      val name = if (tag.startsWith("/")) tag.drop(1) else tag
      if (name.startsWith("color:")) Some(name.drop(6))
      else None
    }
  }

  private object Custom {
    def unapply(tag: String): Option[String] = {
      val name = if (tag.startsWith("/")) tag.drop(1) else tag
      if (name.startsWith("c:")) Some(name.drop(2))
      else None
    }
  }

  private case class Ignore(name: String) extends StyleTag {
    override def render: String = ""
    override val isSelfClosing = true
  }
}
