package izumi.logstage.api.rendering.json

import io.circe.Json
import izumi.logstage.api.rendering.json.LogstageCirceWriter.Token
import izumi.logstage.api.rendering.{LogstageCodec, LogstageTokenRendering, LogstageWriter}

class LogstageCirceWriter extends LogstageWriter {
  private val stack = scala.collection.mutable.Stack[Token]()

  def translate(): Json = LogstageTokenRendering.translate[Token, Json](stack, Token.Close)(
    { case Token.Open(map) => map },
    { case Token.Value(value) => value },
    Token.Value.apply,
    elements => Json.fromFields(elements.sliding(2, 2).map {
      pair =>
        (pair.head.fold("null", _.toString, _.toString, identity, _.toString(), _.toString()), pair.last)
    }.toSeq),
    Json.fromValues,
    Json.Null,
    Json.fromValues,
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

  override def writeNull(): Unit = stack.push(Token.Value(Json.Null))

  override def write(a: Boolean): Unit = stack.push(Token.Value(Json.fromBoolean(a)))

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
    final case class Value(value: Json) extends Token
    final case class Open(map: Boolean) extends Struct
    case object Close extends Struct
  }

  def write[T](codec: LogstageCodec[T], value: T): Json = {
    val writer = new LogstageCirceWriter()
    codec.write(writer, value)
    writer.translate()
  }
}
