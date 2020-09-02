package izumi.logstage.api.rendering

import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.exceptions.IzThrowable._

trait LogstageCodec[-T] {
  def write(writer: LogstageWriter, value: T): Unit

  final def contramap[U](f: U => T): LogstageCodec[U] = (w, v) => write(w, f(v))
}

object LogstageCodec extends LogstageCodecLowPriority {
  @inline def apply[T: LogstageCodec]: LogstageCodec[T] = implicitly

  implicit def listCodec[T: LogstageCodec]: LogstageCodec[Iterable[T]] = new LogstageCodec[Iterable[T]] {
    override def write(writer: LogstageWriter, value: Iterable[T]): Unit = {
      writer.openList()

      value.foreach {
        v =>
          writer.nextListElementOpen()
          LogstageCodec[T].write(writer, v)
          writer.nextListElementClose()
      }

      writer.closeList()
    }
  }

  implicit def mapCodec[K: LogstageCodec, V: LogstageCodec]: LogstageCodec[Map[K, V]] = new LogstageCodec[Map[K, V]] {
    override def write(writer: LogstageWriter, value: Map[K, V]): Unit = {
      writer.openMap()
      value.foreach {
        case (k, v) =>
          writer.nextMapElementOpen()
          LogstageCodec[K].write(writer, k)
          writer.mapElementSplitter()
          LogstageCodec[V].write(writer, v)
          writer.nextMapElementClose()
      }
      writer.closeMap()

    }
  }

  // make null instance higher priority than all other LowPriority instances
  // (`implicit object` is more specific than `implicit val` wrt specificity rule of implicit search)
  implicit object LogstageCodecNull extends LogstageCodec[Null] {
    override def write(writer: LogstageWriter, @unused value: Null): Unit = writer.writeNull()
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
  implicit final lazy val LogstageCodecThrowable: LogstageCodec[Throwable] = {
    (w, t) =>
      w.openMap()

      w.write("type")
      w.write(t.getClass.getName)

      w.write("message")
      val m = t.getMessage
      if (m eq null) w.writeNull() else w.write(m)

      w.write("stacktrace")
      w.write(t.stackTrace)

      w.closeMap()
  }

}
