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

  def applyTagsStyles(parts: Seq[String], stylesheet: Map[String, Seq[StyleTag]]): Seq[String] = {
    renderTokens(parseMessageParts(parts), stylesheet)
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

  private def renderTokens(tokens: Seq[Seq[MessageToken]], stylesheet: Map[String, Seq[StyleTag]]): Seq[String] = {
    val activeTags = mutable.Set[StyleTag]()
    def renderPart(tokens: Seq[MessageToken]): String = {
      StyleTag.renderTags(activeTags.toSeq) +
      tokens
        .map {
          case PlainText(text) => text
          case OpenTagToken(name) =>
            val tag = StyleTag(name, stylesheet)
            if (!tag.isSelfClosing) activeTags.add(tag)
            tag.render
          case CloseTagToken(name) =>
            val tag = StyleTag(name, stylesheet)
            activeTags.remove(tag)
            StyleTag.RESET + StyleTag.renderTags(activeTags.toSeq)
        }.mkString("")
    }

    tokens.map(renderPart)
  }

}
