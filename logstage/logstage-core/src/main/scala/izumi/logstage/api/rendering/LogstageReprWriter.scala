package izumi.logstage.api.rendering

import scala.annotation.unused
import izumi.logstage.api.rendering.LogstageReprWriter.Token

class LogstageReprWriter(@unused colored: Boolean) extends ExtendedLogstageWriter[String] {
  private val stack = scala.collection.mutable.Stack[Token]()

  def translate(): String = LogstageTokenRendering.translate[Token, String](stack, Token.Close)(
    { case Token.Open(map) => map },
    { case Token.Value(value) => value },
    Token.Value.apply,
    elements => elements.sliding(2, 2).map {
      pair =>
        s"${pair.head}: ${pair.last}"
    }.mkString("{", "; ", "}"),
    _.mkString("; "),
    "<???>",
    _.mkString(", "),
  )

  override def openList(): Unit = stack.push(Token.Open(false))

  override def closeList(): Unit = stack.push(Token.Close)

  override def openMap(): Unit = stack.push(Token.Open(true))

  override def closeMap(): Unit = stack.push(Token.Close)

  override def nextListElementClose(): Unit = {}

  override def nextMapElementClose(): Unit = {}

  override def mapElementSplitter(): Unit = {}

  override def nextListElementOpen(): Unit = {}

  override def nextMapElementOpen(): Unit = {}

  override def writeNull(): Unit = str("<null>")

  override def write(a: Boolean): Unit = str(a)

  override def write(a: Byte): Unit = str(a)

  override def write(a: Short): Unit = str(a)

  override def write(a: Char): Unit = str(a)

  override def write(a: Int): Unit = str(a)

  override def write(a: Long): Unit = str(a)

  override def write(a: Float): Unit = str(a)

  override def write(a: Double): Unit = str(a)

  override def write(a: String): Unit = str(a)

  override def write(a: BigDecimal): Unit = str(a)

  override def write(a: BigInt): Unit = str(a)

  @inline private def str(a: Any): Unit = {
    stack.push(Token.Value(a.toString))
  }
}

object LogstageReprWriter {
  sealed trait Token
  object Token {
    sealed trait Struct extends Token
    final case class Value(value: String) extends Token
    final case class Open(map: Boolean) extends Struct
    case object Close extends Struct
  }

}
