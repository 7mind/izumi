package izumi.logstage.api.rendering

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

  final def writeMapElement(key: String, value: Option[String]): Unit = {
    nextMapElementOpen()
    write(key)
    mapElementSplitter()
    value match {
      case Some(value) =>
        write(value)
      case None =>
        writeNull()
    }
    nextMapElementClose()
  }
}

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
