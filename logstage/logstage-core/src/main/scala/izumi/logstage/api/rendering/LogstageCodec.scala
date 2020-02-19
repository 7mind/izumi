package izumi.logstage.api.rendering

import izumi.fundamentals.platform.language.unused

trait LogstageScalarWriter {
  def write(a: Byte): Unit
  def write(a: Short): Unit
  def write(a: Char): Unit
  def write(a: Int): Unit
  def write(a: Long): Unit
  def write(a: Float): Unit
  def write(a: Double): Unit
  def write(a: Boolean): Unit
  def write(a: String): Unit
  def write(a: BigDecimal): Unit
  def write(a: BigInt): Unit
}

trait LogstageWriter extends LogstageScalarWriter {
  def openList(): Unit
  def closeList(): Unit
  def openMap(): Unit
  def closeMap(): Unit
  def nextListElementOpen(): Unit
  def nextListElementClose(): Unit
  def nextMapElementOpen(): Unit
  def nextMapElementClose(): Unit
  def mapElementSplitter(): Unit

  def writeNull(): Unit
}

trait LogstageCodec[-T] {
  def write(value: T, writer: LogstageWriter)
}

trait LogstageCodecs {
  implicit object LogstageCodecString extends LogstageCodec[String] {
    override def write(value: String, writer: LogstageWriter): Unit = writer.write(value)
  }

  implicit object LogstageCodecBoolean extends LogstageCodec[Boolean] {
    override def write(value: Boolean, writer: LogstageWriter): Unit = writer.write(value)
  }

  implicit object LogstageCodecByte extends LogstageCodec[Byte] {
    override def write(value: Byte, writer: LogstageWriter): Unit = writer.write(value)
  }

  implicit object LogstageCodecShort extends LogstageCodec[Short] {
    override def write(value: Short, writer: LogstageWriter): Unit = writer.write(value)
  }

  implicit object LogstageCodecChar extends LogstageCodec[Char] {
    override def write(value: Char, writer: LogstageWriter): Unit = writer.write(value)
  }

  implicit object LogstageCodecInt extends LogstageCodec[Int] {
    override def write(value: Int, writer: LogstageWriter): Unit = writer.write(value)
  }

  implicit object LogstageCodecLong extends LogstageCodec[Long] {
    override def write(value: Long, writer: LogstageWriter): Unit = writer.write(value)
  }

  implicit object LogstageCodecFloat extends LogstageCodec[Float] {
    override def write(value: Float, writer: LogstageWriter): Unit = writer.write(value)
  }

  implicit object LogstageCodecDouble extends LogstageCodec[Double] {
    override def write(value: Double, writer: LogstageWriter): Unit = writer.write(value)
  }

  implicit object LogstageCodecBigDecimal extends LogstageCodec[BigDecimal] {
    override def write(value: BigDecimal, writer: LogstageWriter): Unit = writer.write(value)
  }

  implicit object LogstageCodecBigInt extends LogstageCodec[BigInt] {
    override def write(value: BigInt, writer: LogstageWriter): Unit = writer.write(value)
  }
}

object LogstageCodec extends LogstageCodecs {

  implicit def listCodec[T: LogstageCodec]: LogstageCodec[Iterable[T]] = new LogstageCodec[Iterable[T]] {
    override def write(value: Iterable[T], writer: LogstageWriter): Unit = {
      writer.openList()

      value.foreach {
        v =>
          writer.nextListElementOpen()
          implicitly[LogstageCodec[T]].write(v, writer)
          writer.nextListElementClose()
      }

      writer.closeList()
    }
  }

  implicit def mapCodec[K: LogstageCodec, V: LogstageCodec]: LogstageCodec[Map[K, V]] = new LogstageCodec[Map[K, V]] {

    override def write(value: Map[K, V], writer: LogstageWriter): Unit = {
      writer.openMap()
      value.foreach {
        case (k, v) =>
          writer.nextMapElementOpen()
          implicitly[LogstageCodec[K]].write(k, writer)
          writer.mapElementSplitter()
          implicitly[LogstageCodec[V]].write(v, writer)
          writer.nextMapElementClose()
      }
      writer.closeMap()

    }

  }

  implicit object LogstageCodecNull extends LogstageCodec[Null] {
    override def write(@unused value: Null, writer: LogstageWriter): Unit = writer.writeNull()
  }



}