package izumi.thirdparty.internal.boopickle

import java.nio.charset.CharacterCodingException
import java.nio.{ByteBuffer, ByteOrder}

private[izumi] class DecoderSize(val buf: ByteBuffer) extends Decoder {
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
    * Decodes a UTF-8 encoded character (1-3 bytes) and produces a single UTF-16 character
    *
    * @return
    */
  def readChar: Char = {
    val b0 = buf.get & 0xFF
    if (b0 < 0x80)
      b0.toChar
    else if ((b0 & 0xE0) == 0xC0) {
      val b1 = buf.get & 0x3F
      ((b0 & 0x1F) << 6 | b1).toChar
    } else if ((b0 & 0xF0) == 0xE0) {
      val s0 = buf.get & 0x3F
      val s1 = buf.get & 0x3F
      ((b0 & 0x0F) << 12 | s0 << 6 | s1).toChar
    } else
      throw new CharacterCodingException
  }

  /**
    * Decodes a 16-bit integer
    */
  def readShort: Short = {
    buf.getShort
  }

  /**
    * Decodes a 32-bit integer (1-5 bytes)
    * <pre>
    * 0XXX XXXX                            = 0 to 127
    * 1000 XXXX  b0                        = 128 to 4095
    * 1001 XXXX  b0                        = -1 to -4095
    * 1010 XXXX  b0 b1                     = 4096 to 1048575
    * 1011 XXXX  b0 b1                     = -4096 to -1048575
    * 1100 XXXX  b0 b1 b2                  = 1048576 to 268435455
    * 1101 XXXX  b0 b1 b2                  = -1048576 to -268435455
    * 1110 0000  b0 b1 b2 b3               = MinInt to MaxInt
    * 1111 ????                            = reserved for special codings
    * </pre>
    *
    * @return
    */
  def readInt: Int = {
    val b = buf.get & 0xFF
    if ((b & 0x80) != 0) {
      // special coding, expand sign bit
      val sign = if ((b & 0x10) == 0) 1 else -1
      val b0   = b & 0xF
      b >> 4 match {
        case 0x8 | 0x9 =>
          val b1 = buf.get & 0xFF
          sign * (b0 << 8 | b1)
        case 0xA | 0xB =>
          val b1 = buf.get & 0xFF
          val b2 = buf.get & 0xFF
          sign * (b0 << 16 | b1 << 8 | b2)
        case 0xC | 0xD =>
          val b1 = buf.get & 0xFF
          val b2 = buf.get & 0xFF
          val b3 = buf.get & 0xFF
          sign * (b0 << 24 | b1 << 16 | b2 << 8 | b3)
        case 0xE if b == 0xE0 =>
          sign * readRawInt
        case _ =>
          throw new IllegalArgumentException("Unknown integer coding")
      }
    } else {
      b
    }
  }

  def readRawInt: Int = {
    buf.getInt
  }

  /**
    * Decodes a 64-bit integer (1-9 bytes)
    * <pre>
    * 0XXX XXXX                            = 0 to 127
    * 1000 XXXX  b0                        = 128 to 4095
    * 1001 XXXX  b0                        = -1 to -4095
    * 1010 XXXX  b0 b1                     = 4096 to 1048575
    * 1011 XXXX  b0 b1                     = -4096 to -1048575
    * 1100 XXXX  b0 b1 b2                  = 1048576 to 268435455
    * 1101 XXXX  b0 b1 b2                  = -1048576 to -268435455
    * 1110 0000  b0 b1 b2 b3               = MinInt to MaxInt
    * 1110 0001  b0 b1 b2 b3 b4 b5 b6 b7   = anything larger
    * 1111 ????                            = reserved for special codings
    * </pre>
    *
    * @return
    */
  def readLong: Long = {
    val b = buf.get & 0xFF
    if (b != 0xE1) {
      (buf: java.nio.Buffer).position(buf.position() - 1)
      readInt.toLong
    } else {
      readRawLong
    }
  }

  def readRawLong: Long = {
    buf.getLong
  }

  /**
    * Decodes a 32-bit integer, or returns the first byte if it doesn't contain a valid encoding marker
    *
    * @return
    */
  def readIntCode: Either[Byte, Int] = {
    val b = buf.get & 0xFF
    if ((b & 0x80) != 0) {
      // special coding, expand sign bit
      val sign = if ((b & 0x10) == 0) 1 else -1
      val b0   = b & 0xF
      b >> 4 match {
        case 0x8 | 0x9 =>
          val b1 = buf.get & 0xFF
          Right(sign * (b0 << 8 | b1))
        case 0xA | 0xB =>
          val b1 = buf.get & 0xFF
          val b2 = buf.get & 0xFF
          Right(sign * (b0 << 16 | b1 << 8 | b2))
        case 0xC | 0xD =>
          val b1 = buf.get & 0xFF
          val b2 = buf.get & 0xFF
          val b3 = buf.get & 0xFF
          Right(sign * (b0 << 24 | b1 << 16 | b2 << 8 | b3))
        case 0xE if b == 0xE0 =>
          Right(sign * readRawInt)
        case _ =>
          Left((b & 0xF).toByte)
      }
    } else {
      Right(b)
    }
  }

  /**
    * Decodes a 64-bit long, or returns the first byte if it doesn't contain a valid encoding marker
    *
    * @return
    */
  def readLongCode: Either[Byte, Long] = {
    val b = buf.get & 0xFF
    if (b != 0xE1) {
      (buf: java.nio.Buffer).position(buf.position() - 1)
      readIntCode match {
        case Left(x)  => Left((x & 0xF).toByte)
        case Right(x) => Right(x.toLong)
      }
    } else
      Right(readRawLong)
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
  def readString: String = {
    // read string length
    val len = readInt
    stringCodec.decodeFast(len, buf)
  }

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
    var i     = 0
    while (i < len) {
      array(i) = readInt
      i += 1
    }
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

private[izumi] class EncoderSize(bufferProvider: BufferProvider = DefaultByteBufferProvider.provider) extends Encoder {
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
    if (c < 0x80) {
      alloc(1).put(c.toByte)
    } else if (c < 0x800) {
      alloc(2).put((0xC0 | (c >>> 6 & 0x3F)).toByte).put((0x80 | (c & 0x3F)).toByte)
    } else {
      alloc(3).put((0xE0 | (c >>> 12)).toByte).put((0x80 | (c >>> 6 & 0x3F)).toByte).put((0x80 | (c & 0x3F)).toByte)
    }
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
    * Encodes an integer efficiently in 1 to 5 bytes
    * <pre>
    * 0XXX XXXX                            = 0 to 127
    * 1000 XXXX  b0                        = 128 to 4095
    * 1001 XXXX  b0                        = -1 to -4095
    * 1010 XXXX  b0 b1                     = 4096 to 1048575
    * 1011 XXXX  b0 b1                     = -4096 to -1048575
    * 1100 XXXX  b0 b1 b2                  = 1048575 to 268435455
    * 1101 XXXX  b0 b1 b2                  = -1048575 to -268435455
    * 1110 0000  b0 b1 b2 b3               = MinInt to MaxInt
    * 1111 ????                            = reserved for special codings
    * </pre>
    *
    * @param i Integer to encode
    */
  def writeInt(i: Int): Encoder = {
    // check for a short number
    if (i >= 0 && i < 128) {
      alloc(1).put(i.toByte)
    } else {
      if (i > -268435456 && i < 268435456) {
        val mask = i >>> 31 << 4
        val a    = Math.abs(i)
        if (a < 4096) {
          alloc(2).put((mask | 0x80 | (a >> 8)).toByte).put((a & 0xFF).toByte)
        } else if (a < 1048576) {
          alloc(3).put((mask | 0xA0 | (a >> 16)).toByte).put(((a >> 8) & 0xFF).toByte).put((a & 0xFF).toByte)
        } else {
          alloc(4)
            .put((mask | 0xC0 | (a >> 24)).toByte)
            .put(((a >> 16) & 0xFF).toByte)
            .put(((a >> 8) & 0xFF).toByte)
            .put((a & 0xFF).toByte)
        }
      } else {
        alloc(5).put(0xE0.toByte).putInt(i)
      }
    }
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
    * Encodes a long efficiently in 1 to 9 bytes
    * <pre>
    * 0XXX XXXX                            = 0 to 127
    * 1000 XXXX  b0                        = 128 to 4095
    * 1001 XXXX  b0                        = -1 to -4096
    * 1010 XXXX  b0 b1                     = 4096 to 1048575
    * 1011 XXXX  b0 b1                     = -4096 to -1048575
    * 1100 XXXX  b0 b1 b2                  = 1048576 to 268435455
    * 1101 XXXX  b0 b1 b2                  = -1048576 to -268435455
    * 1110 0000  b0 b1 b2 b3               = MinInt to MaxInt
    * 1110 0001  b0 b1 b2 b3 b4 b5 b6 b7   = anything larger
    * 1111 ????                            = reserved for special codings
    * </pre>
    *
    * @param l Long to encode
    */
  def writeLong(l: Long): Encoder = {
    if (l <= Int.MaxValue && l >= Int.MinValue)
      writeInt(l.toInt)
    else {
      alloc(9).put(0xE1.toByte).putLong(l)
    }
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
        alloc(1).put((code | 0xF0).toByte)
      case Right(i) =>
        writeInt(i)
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
        alloc(1).put((code | 0xF0).toByte)
      case Right(l) =>
        writeLong(l)
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
    ia.foreach(writeInt)
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
