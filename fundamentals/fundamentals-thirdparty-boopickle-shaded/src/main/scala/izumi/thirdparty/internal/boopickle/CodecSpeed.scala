package izumi.thirdparty.internal.boopickle

import java.nio.{ByteBuffer, ByteOrder}

private[izumi] class DecoderSpeed(val buf: ByteBuffer) extends Decoder {
  val stringCodec: StringCodecBase = StringCodec

  /**
    * Decodes a single byte
    *
    * @return
    */
  def readByte: Byte = {
    buf.get
  }

  /**
    * Decodes a character
    *
    * @return
    */
  def readChar: Char = {
    buf.getChar()
  }

  /**
    * Decodes a 16-bit integer
    */
  def readShort: Short = {
    buf.getShort
  }

  /**
    * Decodes a 32-bit integer
    */
  def readInt: Int = {
    buf.getInt
  }

  def readRawInt: Int = {
    buf.getInt
  }

  /**
    * Decodes a 64-bit integer
    */
  def readLong: Long = {
    buf.getLong
  }

  def readRawLong: Long = {
    buf.getLong
  }

  /**
    * Decodes a 32-bit integer, or a special code
    *
    * @return
    */
  def readIntCode: Either[Byte, Int] = {
    val b = buf.get & 0xFF
    if ((b & 0x80) != 0) {
      Left((b & 0xF).toByte)
    } else {
      Right(buf.getInt)
    }
  }

  /**
    * Decodes a 64-bit long, or a special code
    *
    * @return
    */
  def readLongCode: Either[Byte, Long] = {
    val b = buf.get & 0xFF
    if ((b & 0x80) != 0) {
      Left((b & 0xF).toByte)
    } else {
      Right(buf.getLong)
    }
  }

  /**
    * Decodes a 32-bit float (4 bytes)
    *
    * @return
    */
  def readFloat: Float = {
    buf.getFloat
  }

  /**
    * Decodes a 64-bit double (8 bytes)
    *
    * @return
    */
  def readDouble: Double = {
    buf.getDouble
  }

  /**
    * Decodes a UTF-8 encoded string
    *
    * @return
    */
  def readString: String = readString(readInt)

  /**
    * Decodes a UTF-8 encoded string whose length is already known
    *
    * @param len Length of the string (in bytes)
    * @return
    */
  def readString(len: Int): String = {
    stringCodec.decodeFast(len, buf)
  }

  def readByteBuffer: ByteBuffer = {
    // length and byte order are encoded into same integer
    val sizeBO = readInt
    if (sizeBO < 0)
      throw new IllegalArgumentException(s"Invalid size $sizeBO for ByteBuffer")
    val size      = sizeBO >> 1
    val byteOrder = if ((sizeBO & 1) == 1) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    // create a copy (sharing content), set correct byte order
    val b = buf.slice().order(byteOrder)
    (buf: java.nio.Buffer).position(buf.position() + size)
    b.limit(b.position() + size)
    b
  }

  /**
    * Decodes an array of Bytes
    */
  def readByteArray(): Array[Byte] = readByteArray(readRawInt)
  def readByteArray(len: Int): Array[Byte] = {
    val array = new Array[Byte](len)
    buf.get(array)
    array
  }

  /**
    * Decodes an array of Integers
    */
  def readIntArray(): Array[Int] = readIntArray(readRawInt)
  def readIntArray(len: Int): Array[Int] = {
    val array = new Array[Int](len)
    buf.asIntBuffer().get(array)
    (buf: java.nio.Buffer).position(buf.position() + len * 4)
    array
  }

  /**
    * Decodes an array of Floats
    */
  def readFloatArray(): Array[Float] = readFloatArray(readRawInt)
  def readFloatArray(len: Int): Array[Float] = {
    val array = new Array[Float](len)
    buf.asFloatBuffer().get(array)
    (buf: java.nio.Buffer).position(buf.position() + len * 4)
    array
  }

  /**
    * Decodes an array of Doubles
    */
  def readDoubleArray(): Array[Double] = {
    val len = readRawInt
    readRawInt // remove padding
    readDoubleArray(len)
  }
  def readDoubleArray(len: Int): Array[Double] = {
    val array = new Array[Double](len)
    buf.asDoubleBuffer().get(array)
    (buf: java.nio.Buffer).position(buf.position() + len * 8)
    array
  }
}

private[izumi] class EncoderSpeed(bufferProvider: BufferProvider = DefaultByteBufferProvider.provider) extends Encoder {
  val stringCodec: StringCodecBase = StringCodec

  @inline private def alloc(size: Int): ByteBuffer = bufferProvider.alloc(size)

  /**
    * Encodes a single byte
    *
    * @param b Byte to encode
    * @return
    */
  def writeByte(b: Byte): Encoder = {
    alloc(1).put(b)
    this
  }

  /**
    * Encodes a single character using UTF-8 encoding
    *
    * @param c Character to encode
    * @return
    */
  def writeChar(c: Char): Encoder = {
    alloc(2).putChar(c)
    this
  }

  /**
    * Encodes a short integer
    */
  def writeShort(s: Short): Encoder = {
    alloc(2).putShort(s)
    this
  }

  /**
    * Encodes an integer
    *
    * @param i Integer to encode
    */
  def writeInt(i: Int): Encoder = {
    alloc(4).putInt(i)
    this
  }

  /**
    * Encodes an integer in 32-bits
    *
    * @param i Integer to encode
    * @return
    */
  def writeRawInt(i: Int): Encoder = {
    alloc(4).putInt(i)
    this
  }

  /**
    * Encodes a long
    *
    * @param l Long to encode
    */
  def writeLong(l: Long): Encoder = {
    alloc(8).putLong(l)
    this
  }

  /**
    * Encodes a long in 64-bits
    *
    * @param l Long to encode
    * @return
    */
  def writeRawLong(l: Long): Encoder = {
    alloc(8).putLong(l)
    this
  }

  /**
    * Writes either a code byte (0-15) or an Int
    *
    * @param intCode Integer or a code byte
    */
  def writeIntCode(intCode: Either[Byte, Int]): Encoder = {
    intCode match {
      case Left(code) =>
        alloc(1).put((code | 0x80).toByte)
      case Right(i) =>
        alloc(5).put(0.toByte).putInt(i)
    }
    this
  }

  /**
    * Writes either a code byte (0-15) or a Long
    *
    * @param longCode Long or a code byte
    */
  def writeLongCode(longCode: Either[Byte, Long]): Encoder = {
    longCode match {
      case Left(code) =>
        alloc(1).put((code | 0x80).toByte)
      case Right(l) =>
        alloc(9).put(0.toByte).putLong(l)
    }
    this
  }

  /**
    * Encodes a string using UTF8
    *
    * @param s String to encode
    * @return
    */
  def writeString(s: String): Encoder = {
    writeInt(s.length)
    val bb = alloc(s.length * 3)
    stringCodec.encodeFast(s, bb)
    this
  }

  /**
    * Encodes a float as 4 bytes
    *
    * @param f Float to encode
    * @return
    */
  def writeFloat(f: Float): Encoder = {
    alloc(4).putFloat(f)
    this
  }

  /**
    * Encodes a double as 8 bytes
    *
    * @param d Double to encode
    * @return
    */
  def writeDouble(d: Double): Encoder = {
    alloc(8).putDouble(d)
    this
  }

  /**
    * Encodes a ByteBuffer by writing its length and content
    *
    * @param bb ByteBuffer to encode
    * @return
    */
  def writeByteBuffer(bb: ByteBuffer): Encoder = {
    bb.mark()
    val byteOrder = if (bb.order() == ByteOrder.BIG_ENDIAN) 1 else 0
    // encode byte order as bit 0 in the length
    writeInt(bb.remaining * 2 | byteOrder)
    alloc(bb.remaining).put(bb)
    bb.reset()
    this
  }

  /**
    * Encodes an array of Bytes
    */
  def writeByteArray(ba: Array[Byte]): Encoder = {
    writeRawInt(ba.length)
    alloc(ba.length).put(ba)
    this
  }

  /**
    * Encodes an array of Integers
    */
  def writeIntArray(ia: Array[Int]): Encoder = {
    writeRawInt(ia.length)
    val bb = alloc(ia.length * 4)
    bb.asIntBuffer().put(ia)
    (bb: java.nio.Buffer).position(bb.position() + ia.length * 4)
    this
  }

  /**
    * Encodes an array of Floats
    */
  def writeFloatArray(fa: Array[Float]): Encoder = {
    writeRawInt(fa.length)
    val bb = alloc(fa.length * 4)
    bb.asFloatBuffer().put(fa)
    (bb: java.nio.Buffer).position(bb.position() + fa.length * 4)
    this
  }

  /**
    * Encodes an array of Doubles
    */
  def writeDoubleArray(da: Array[Double]): Encoder = {
    writeRawInt(da.length)
    // padding
    writeRawInt(0)
    val bb = alloc(da.length * 8)
    bb.asDoubleBuffer().put(da)
    (bb: java.nio.Buffer).position(bb.position() + da.length * 8)
    this
  }

  /**
    * Completes the encoding and returns the ByteBuffer
    *
    * @return
    */
  def asByteBuffer = bufferProvider.asByteBuffer

  /**
    * Completes the encoding and returns a sequence of ByteBuffers
    *
    * @return
    */
  def asByteBuffers = bufferProvider.asByteBuffers
}
