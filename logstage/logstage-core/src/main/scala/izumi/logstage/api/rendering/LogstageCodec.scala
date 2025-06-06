package izumi.logstage.api.rendering

import scala.annotation.unused
import izumi.fundamentals.platform.exceptions.IzThrowable.*

trait LogstageCodec[-T] {
  def write(writer: LogstageWriter, value: T): Unit

  def makeReprWriter(colored: Boolean): ExtendedLogstageWriter[String] = new LogstageReprWriter(colored)

  final def contramap[U](f: U => T): LogstageCodec[U] = (w, v) => write(w, f(v))
}

object LogstageCodec extends LogstageCodecLowPriority {
  @inline def apply[T: LogstageCodec]: LogstageCodec[T] = implicitly

  implicit def listCodec[T: LogstageCodec]: LogstageCodec[Iterable[T]] = new ListCodec[T](LogstageCodec[T])

  implicit def mapCodec[K: LogstageCodec, V: LogstageCodec]: LogstageCodec[collection.Map[K, V]] = new MapCodec[K, V](LogstageCodec[K], LogstageCodec[V])

  // make null instance higher priority than all other LowPriority instances
  // (`implicit object` is more specific than `implicit val` wrt specificity rule of implicit search)
  implicit object LogstageCodecNull extends LogstageCodec[Null] {
    override def write(writer: LogstageWriter, @unused value: Null): Unit = writer.writeNull()
  }

  final case class ListCodec[T](tCodec: LogstageCodec[T]) extends LogstageCodec[Iterable[T]] {
    override def write(writer: LogstageWriter, value: Iterable[T]): Unit = {
      writer.openList()

      value.foreach {
        v =>
          writer.nextListElementOpen()
          tCodec.write(writer, v)
          writer.nextListElementClose()
      }

      writer.closeList()
    }
  }

  final case class MapCodec[K, V](kCodec: LogstageCodec[K], vCodec: LogstageCodec[V]) extends LogstageCodec[collection.Map[K, V]] {
    override def write(writer: LogstageWriter, value: collection.Map[K, V]): Unit = {
      writer.openMap()
      value.foreach {
        case (k, v) =>
          writer.nextMapElementOpen()
          kCodec.write(writer, k)
          writer.mapElementSplitter()
          vCodec.write(writer, v)
          writer.nextMapElementClose()
      }
      writer.closeMap()
    }
  }

}

sealed trait LogstageCodecLowPriority {
  implicit final lazy val LogstageCodecString: LogstageCodec[String] = _.write(_)
  implicit final lazy val LogstageCodecBoolean: LogstageCodec[Boolean] = _.write(_)
  implicit final lazy val LogstageCodecByte: LogstageCodec[Byte] = _.write(_)
  implicit final lazy val LogstageCodecShort: LogstageCodec[Short] = _.write(_)
  implicit final lazy val LogstageCodecChar: LogstageCodec[Char] = _.write(_)
  implicit final lazy val LogstageCodecInt: LogstageCodec[Int] = _.write(_)
  implicit final lazy val LogstageCodecLong: LogstageCodec[Long] = _.write(_)
  implicit final lazy val LogstageCodecFloat: LogstageCodec[Float] = _.write(_)
  implicit final lazy val LogstageCodecDouble: LogstageCodec[Double] = _.write(_)
  implicit final lazy val LogstageCodecBigDecimal: LogstageCodec[BigDecimal] = _.write(_)
  implicit final lazy val LogstageCodecBigInt: LogstageCodec[BigInt] = _.write(_)
  implicit final lazy val LogstageCodecUnit: LogstageCodec[Unit] = (s, unit) => s.write(unit.toString)

  implicit final lazy val LogstageCodecThrowable: LogstageCodec[Throwable] = {
    (w, t) =>
      Option(t) match {
        case Some(_) =>
          w.openMap()
          w.writeMapElement("type", Option(t.getClass).map(_.getName))
          w.writeMapElement("message", Option(t.getMessage))
          w.writeMapElement("stacktrace", Option(t.stacktraceString))
          w.closeMap()
        case None =>
          w.writeNull()
      }
  }

}
