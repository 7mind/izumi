package izumi.logstage.api.rendering

import izumi.fundamentals.platform.language.Quirks.Discarder
import scala.annotation.unused
import izumi.logstage.api.rendering.LogstageReprWriter.Token

class LogstageReprWriter(@unused colored: Boolean) extends ExtendedLogstageWriter[String] {
  private val stack = scala.collection.mutable.Stack[Token]()

  def translate(): String = {
    val boundaries = scala.collection.mutable.Stack[Token]()

    while (stack.nonEmpty) {
      stack.pop() match {
        case Token.Open(m) =>
          val elements = scala.collection.mutable.ArrayBuffer[String]()

          while (boundaries.head != Token.Close) {
            boundaries.pop() match {
              case Token.Value(v) =>
                elements += v
              case t =>
                throw new RuntimeException(s"Unexpected token: $t; stack=$stack, bstack=$boundaries")
            }
          }

          boundaries.pop().discard()

          if (m) {
            val pairs = elements.sliding(2, 2).map {
              e =>
                s"${e.head}: ${e.last}"
            }
            boundaries.push(Token.Value(pairs.mkString("{", "; ", "}")))
          } else {
            boundaries.push(Token.Value(elements.mkString("; ")))
          }

        case Token.Close =>
          boundaries.push(Token.Close)
        case v: Token.Value =>
          boundaries.push(v)
      }
    }

    boundaries.flatMap {
      case _: Token.Struct =>
        Seq.empty
      case Token.Value(value) =>
        Seq(value)
    }.toList match {
      case one :: Nil =>
        one
      case Nil =>
        "<???>"
      case shouldNotHappen =>
        shouldNotHappen.mkString(", ")
    }
  }

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
