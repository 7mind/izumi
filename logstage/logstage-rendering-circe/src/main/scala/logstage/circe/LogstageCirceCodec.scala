package logstage.circe

import io.circe.{Encoder, Json, JsonNumber, JsonObject}
import izumi.logstage.api.rendering.{LogstageCodec, LogstageWriter}

class LogstageCirceCodec[T : Encoder] extends LogstageCodec[T] {
  override def write(value: T, writer: LogstageWriter): Unit = {
    LogstageCirceCodec.LogstageCirceJsonCodec.write(implicitly[Encoder[T]].apply(value), writer)
  }
}

object LogstageCirceCodec {
  implicit object LogstageCirceJsonCodec extends LogstageCodec[Json] {

    def folder(writer: LogstageWriter): Json.Folder[Unit] = new Json.Folder[Unit] {
      override def onNull: Unit = writer.writeNull()

      override def onBoolean(value: Boolean): Unit = writer.write(value)

      override def onNumber(value: JsonNumber): Unit = {
        value.toBigInt match {
          case Some(value) =>
            writer.write(value)
          case None =>
            value.toBigDecimal match {
              case Some(value) =>
                writer.write(value)
              case None =>
                writer.write(value.toDouble)
            }
        }
      }

      override def onString(value: String): Unit = writer.write(value)

      override def onArray(value: Vector[Json]): Unit = {
        writer.openList()
        value.foreach {
          v =>
            writer.nextListElementOpen()
            v.foldWith(this)
            writer.nextListElementClose()
        }
        writer.closeList()
      }

      override def onObject(value: JsonObject): Unit = {
        writer.openMap()
        value.toIterable.foreach {
          case (k, v) =>
            writer.nextMapElementOpen()
            writer.write(k)
            writer.mapElementSplitter()
            v.foldWith(this)
            writer.nextMapElementClose()
        }
        writer.closeMap()
      }
    }

    override def write(value: Json, writer: LogstageWriter): Unit = value.foldWith(folder(writer))
  }
}