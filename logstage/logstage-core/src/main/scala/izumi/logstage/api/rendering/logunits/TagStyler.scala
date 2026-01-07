package izumi.logstage.api.rendering.logunits

import izumi.logstage.api.rendering.logunits.TagStyler.MessageToken.{CloseTag, OpenTag, PlainText}
import izumi.logstage.api.rendering.logunits.TagStyler.ParserState.{InTag, InText}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object TagStyler {
  sealed trait MessageToken
  object MessageToken {
    case class PlainText(text: String) extends MessageToken
    case class OpenTag(name: String) extends MessageToken
    case class CloseTag(name: String) extends MessageToken
  }

  sealed trait ParserState
  object ParserState {
    case object InText extends ParserState
    case class InTag(isClosing: Boolean) extends ParserState
  }

  sealed trait StyleTag {
    def render: String
  }
  object StyleTag {
    val RESET = "\u001b[0m"
    def apply(name: String): StyleTag = {
      name match {
        case "b" | "bold" => Bold
        case "i" | "italic" => Italic
        case "u" | "underline" => Underlined
        case _ => throw new RuntimeException("Can not apply style: unknown tag")
      }
    }

    case object Bold extends StyleTag {
      override def render: String = "\u001b[1m"
    }
    case object Italic extends StyleTag {
      override def render: String = "\u001b[3m"
    }
    case object Underlined extends StyleTag {
      override def render: String = "\u001b[4m"
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
            tokens += (if (isClosing) CloseTag(tagName) else OpenTag(tagName))
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
          case OpenTag(name) =>
            val tag = StyleTag(name)
            activeTags.add(tag)
            tag.render
          case CloseTag(name) =>
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
