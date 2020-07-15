package izumi.logstage.api.rendering.json

import io.circe.Json
import izumi.logstage.api.rendering.{LogstageCodec, LogstageWriter}
import izumi.fundamentals.platform.language.Quirks._

class LogstageCirceWriter extends LogstageWriter {
  import LogstageCirceWriter._
  private val stack = scala.collection.mutable.Stack[Token]()

  def translate(): Json = {
    val boundaries = scala.collection.mutable.Stack[Token]()

    while (stack.nonEmpty) {
      stack.pop() match {
        case Token.Open(m) =>
          val elements = scala.collection.mutable.ArrayBuffer[Json]()
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
                (e.head.fold("null", _.toString, _.toString, identity, _.toString(), _.toString()), e.last)
            }
            boundaries.push(Token.Value(Json.fromFields(pairs.toSeq)))
          } else {
            boundaries.push(Token.Value(Json.fromValues(elements)))
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
        Json.Null
      case shouldNotHappen =>
        Json.fromValues(shouldNotHappen)
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

  override def write(a: Boolean): Unit = {}

  override def writeNull(): Unit = stack.push(Token.Value(Json.Null))

  override def write(a: Byte): Unit = stack.push(Token.Value(Json.fromInt(a.toInt)))

  override def write(a: Short): Unit = stack.push(Token.Value(Json.fromInt(a.toInt)))

  override def write(a: Char): Unit = stack.push(Token.Value(Json.fromString(a.toString)))

  override def write(a: Int): Unit = stack.push(Token.Value(Json.fromInt(a)))

  override def write(a: Long): Unit = stack.push(Token.Value(Json.fromLong(a)))

  override def write(a: Float): Unit = stack.push(Token.Value(Json.fromFloatOrString(a)))

  override def write(a: Double): Unit = stack.push(Token.Value(Json.fromDoubleOrString(a)))

  override def write(a: String): Unit = stack.push(Token.Value(Json.fromString(a)))

  override def write(a: BigDecimal): Unit = stack.push(Token.Value(Json.fromBigDecimal(a)))

  override def write(a: BigInt): Unit = stack.push(Token.Value(Json.fromBigInt(a)))
}

object LogstageCirceWriter {
  sealed trait Token
  object Token {
    sealed trait Struct extends Token
    case class Value(value: Json) extends Token
    case class Open(map: Boolean) extends Struct
    case object Close extends Struct

  }
  def write(codec: LogstageCodec[Any], value: Any): Json = {
    val writer = new LogstageCirceWriter()
    codec.write(writer, value)
    writer.translate()
  }
}
