package izumi.logstage.api.rendering.logunits

import izumi.logstage.api.rendering.logunits.TagStyler.MessageToken.{CloseTagToken, OpenTagToken, PlainText}
import izumi.logstage.api.rendering.logunits.TagStyler.ParserState.{InTag, InText}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object TagStyler {
  sealed trait MessageToken
  object MessageToken {
    case class PlainText(text: String) extends MessageToken
    case class OpenTagToken(name: String) extends MessageToken
    case class CloseTagToken(name: String) extends MessageToken
  }

  sealed trait ParserState
  object ParserState {
    case object InText extends ParserState
    case class InTag(isClosing: Boolean) extends ParserState
  }

  sealed trait StyleTag {
    def render: String
    val isSelfClosing: Boolean = false
  }
  private object StyleTag {
    val RESET = "\u001b[0m"
    def apply(name: String): StyleTag = {
      name match {
        case "b" | "bold" => Bold
        case "i" | "italic" => Italic
        case "u" | "underlined" => Underlined
        case "r" | "reversed" => Reversed
        case Color(color) => ColorTag(color)
        case n => Ignore(n)
      }
    }

    private case object Bold extends StyleTag {
      override def render: String = "\u001b[1m"
    }
    private case object Italic extends StyleTag {
      override def render: String = "\u001b[3m"
    }
    private case object Underlined extends StyleTag {
      override def render: String = "\u001b[4m"
    }
    private case object Reversed extends StyleTag {
      override def render: String = "\u001b[7m"
    }
    private case class ColorTag(color: String) extends StyleTag {
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

    private object Color {
      def unapply(tag: String): Option[String] = {
        val name = if (tag.startsWith("/")) tag.drop(1) else tag
        if (name.startsWith("c:")) Some(name.drop(2))
        else if (name.startsWith("color:")) Some(name.drop(6))
        else None
      }
    }

    private case class Ignore(name: String) extends StyleTag {
      override def render: String = ""
      override val isSelfClosing = true
    }
  }

  def applyTagsStyles(parts: Seq[String]): Seq[String] = {
    renderTokens(parseMessageParts(parts))
  }

  private def parseMessageParts(parts: Seq[String]): Seq[Seq[MessageToken]] = {
    parts.map(part => parseSinglePart(part))
  }

  private def parseSinglePart(message: String): Seq[MessageToken] = {
    val tokens = ListBuffer[MessageToken]()
    val textBuffer: StringBuilder = new StringBuilder
    var parserState: ParserState = InText

    for (char <- message) {
      parserState match {
        case InText =>
          if (char == '<') {
            tokens += PlainText(textBuffer.toString)
            textBuffer.clear()
            parserState = InTag(false)
          } else {
            textBuffer.append(char)
          }
        case InTag(isClosing) =>
          if (char == '/' && textBuffer.isEmpty) {
            parserState = InTag(true)
          } else if (char == '>') {
            val tagName = textBuffer.toString
            tokens += (if (isClosing) CloseTagToken(tagName) else OpenTagToken(tagName))
            textBuffer.clear()
            parserState = InText
          } else {
            textBuffer.append(char)
          }
      }
    }
    if (textBuffer.nonEmpty) tokens += PlainText(textBuffer.toString)
    tokens.toSeq
  }

  private def renderTokens(tokens: Seq[Seq[MessageToken]]): Seq[String] = {
    val activeTags = mutable.Set[StyleTag]()
    def renderPart(tokens: Seq[MessageToken]): String = {
      renderTags(activeTags.toSet) +
      tokens
        .map {
          case PlainText(text) => text
          case OpenTagToken(name) =>
            val tag = StyleTag(name)
            if (!tag.isSelfClosing) activeTags.add(tag)
            tag.render
          case CloseTagToken(name) =>
            val tag = StyleTag(name)
            activeTags.remove(tag)
            StyleTag.RESET + renderTags(activeTags.toSet)
        }.mkString("")
    }

    def renderTags(tags: Set[StyleTag]): String = {
      tags.map(_.render).mkString("")
    }

    tokens.map(renderPart)
  }

}
