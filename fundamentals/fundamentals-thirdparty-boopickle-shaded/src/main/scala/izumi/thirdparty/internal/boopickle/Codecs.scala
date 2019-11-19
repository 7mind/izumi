package izumi.thirdparty.internal.boopickle

import java.nio.ByteBuffer

private[izumi] trait Decoder {

  /**
    * Decodes a single byte
    *
    */
  def readByte: Byte

  /**
    * Decodes a UTF-8 encoded character (1-3 bytes) and produces a single UTF-16 character
    *
    */
  def readChar: Char

  /**
    * Decodes a 16-bit integer
    */
  def readShort: Short

  /**
    * Decodes a 32-bit integer
    */
  def readInt: Int

  def readRawInt: Int

  /**
    * Decodes a 64-bit integer
    */
  def readLong: Long

  def readRawLong: Long

  /**
    * Decodes a 32-bit integer, or returns the first byte if it doesn't contain a valid encoding marker
    */
  def readIntCode: Either[Byte, Int]

  /**
    * Decodes a 64-bit long, or returns the first byte if it doesn't contain a valid encoding marker
    */
  def readLongCode: Either[Byte, Long]

  /**
    * Decodes a 32-bit float (4 bytes)
    */
  def readFloat: Float

  /**
    * Decodes a 64-bit double (8 bytes)
    *
    */
  def readDouble: Double

  /**
    * Decodes a string
    *
    */
  def readString: String

  /**
    * Decodes a string whose length is already known
    *
    * @param len Length of the string (in bytes)
    */
  def readString(len: Int): String

  /**
    * Decodes a ByteBuffer
    */
  def readByteBuffer: ByteBuffer

  /**
    * Decodes an array of Bytes
    */
  def readByteArray(): Array[Byte]
  def readByteArray(len: Int): Array[Byte]

  /**
    * Decodes an array of Integers
    */
  def readIntArray(): Array[Int]
  def readIntArray(len: Int): Array[Int]

  /**
    * Decodes an array of Floats
    */
  def readFloatArray(): Array[Float]
  def readFloatArray(len: Int): Array[Float]

  /**
    * Decodes an array of Doubles
    */
  def readDoubleArray(): Array[Double]
  def readDoubleArray(len: Int): Array[Double]
}

private[izumi] trait Encoder {

  /**
    * Encodes a single byte
    *
    * @param b Byte to encode
    */
  def writeByte(b: Byte): Encoder

  /**
    * Encodes a single character using UTF-8 encoding
    *
    * @param c Character to encode
    */
  def writeChar(c: Char): Encoder

  /**
    * Encodes a short integer
    */
  def writeShort(s: Short): Encoder

  /**
    * Encodes an integer
    */
  def writeInt(i: Int): Encoder

  /**
    * Encodes an integer in 32-bits
    *
    * @param i Integer to encode
    */
  def writeRawInt(i: Int): Encoder

  /**
    * Encodes a long
    *
    * @param l Long to encode
    */
  def writeLong(l: Long): Encoder

  /**
    * Encodes a long in 64-bits
    *
    * @param l Long to encode
    */
  def writeRawLong(l: Long): Encoder

  /**
    * Writes either a code byte (0-15) or an Int
    *
    * @param intCode Integer or a code byte
    */
  def writeIntCode(intCode: Either[Byte, Int]): Encoder

  /**
    * Writes either a code byte (0-15) or a Long
    *
    * @param longCode Long or a code byte
    */
  def writeLongCode(longCode: Either[Byte, Long]): Encoder

  /**
    * Encodes a string
    *
    * @param s String to encode
    */
  def writeString(s: String): Encoder

  /**
    * Encodes a float as 4 bytes
    *
    * @param f Float to encode
    */
  def writeFloat(f: Float): Encoder

  /**
    * Encodes a double as 8 bytes
    *
    * @param d Double to encode
    */
  def writeDouble(d: Double): Encoder

  /**
    * Encodes a ByteBuffer by writing its length and content
    *
    * @param bb ByteBuffer to encode
    */
  def writeByteBuffer(bb: ByteBuffer): Encoder

  /**
    * Encodes an array of Bytes
    */
  def writeByteArray(ba: Array[Byte]): Encoder

  /**
    * Encodes an array of Integers
    */
  def writeIntArray(ia: Array[Int]): Encoder

  /**
    * Encodes an array of Floats
    */
  def writeFloatArray(fa: Array[Float]): Encoder

  /**
    * Encodes an array of Doubles
    */
  def writeDoubleArray(da: Array[Double]): Encoder

  /**
    * Completes the encoding and returns the ByteBuffer
    */
  def asByteBuffer: ByteBuffer

  /**
    * Completes the encoding and returns a sequence of ByteBuffers
    */
  def asByteBuffers: Iterable[ByteBuffer]
}
