package izumi.thirdparty.internal.boopickle

import java.nio.ByteBuffer

abstract class StringCodecFast {

  /**
    * String decoding function for a special 1-3 byte encoding of 16-bit char values
    *
    * @param len How many bytes to decode
    * @param buf Buffer containing the data
    * @return String with decoded data
    */
  def decodeFast(len: Int, buf: ByteBuffer): String = {
    if (buf.hasArray)
      decodeFastArray(len, buf)
    else
      decodeFastBuf(len, buf)
  }

  /**
    * String encoding function for a special 1-3 byte encoding of 16-bit char values
    *
    * @param s String to encode
    * @return `ByteBuffer` with the encoded data
    */
  def encodeFast(s: String, bb: ByteBuffer): Unit = {
    if (bb.hasArray)
      encodeFastArray(s, bb)
    else
      encodeFastBuf(s, bb)
  }

  def encodeFastArray(s: String, bb: ByteBuffer): Unit = {
    val len     = s.length()
    val buf     = bb.array()
    var dst     = bb.arrayOffset() + bb.position()
    var src     = 0
    var c: Char = ' '
    // start by encoding ASCII only
    while ((src < len) && { c = s.charAt(src); c < 0x80 }) {
      buf(dst) = c.toByte
      src += 1
      dst += 1
    }

    // next stage, encode also non-ASCII
    while (src < len) {
      c = s.charAt(src)
      if (c < 0x80) {
        buf(dst) = c.toByte
        dst += 1
      } else if (c < 0x4000) {
        buf(dst) = (0x80 | (c & 0x3F)).toByte
        buf(dst + 1) = (c >> 6 & 0xFF).toByte
        dst += 2
      } else {
        buf(dst) = (0xC0 | (c & 0x3F)).toByte
        buf(dst + 1) = (c >> 6 & 0xFF).toByte
        buf(dst + 2) = (c >> 14).toByte
        dst += 3
      }
      src += 1
    }
    bb.position(dst - bb.arrayOffset())
  }

  def encodeFastBuf(s: String, bb: ByteBuffer): Unit = {
    val len = s.length()
    // worst case scenario produces 3 bytes per character
    var src     = 0
    var c: Char = ' '
    // start by encoding ASCII only
    while ((src < len) && { c = s.charAt(src); c < 0x80 }) {
      bb.put(c.toByte)
      src += 1
    }

    // next stage, encode also non-ASCII
    while (src < len) {
      c = s.charAt(src)
      if (c < 0x80) {
        bb.put(c.toByte)
      } else if (c < 0x4000) {
        bb.put((0x80 | (c & 0x3F)).toByte)
        bb.put((c >> 6 & 0xFF).toByte)
      } else {
        bb.put((0xC0 | (c & 0x3F)).toByte)
        bb.put((c >> 6 & 0xFF).toByte)
        bb.put((c >> 14).toByte)
      }
      src += 1
    }
  }

  /**
    * Faster decoding for array backed buffers
    */
  protected def decodeFastArray(len: Int, buf: ByteBuffer): String = {
    val cp     = new Array[Char](len)
    val src    = buf.array()
    var offset = buf.arrayOffset() + buf.position()
    var dst    = 0
    while (dst < len) {
      val b = src(offset)
      offset += 1
      if ((b & 0x80) == 0) {
        cp(dst) = (b & 0x7F).toChar
      } else if ((b & 0xC0) == 0x80) {
        val b1 = src(offset)
        offset += 1
        cp(dst) = (b & 0x3F | (b1.toShort & 0xFF) << 6).toChar
      } else {
        val b1 = src(offset)
        val b2 = src(offset + 1)
        offset += 2
        cp(dst) = (b & 0x3F | (b1.toShort & 0xFF) << 6 | (b2.toShort << 14)).toChar
      }
      dst += 1
    }
    buf.position(offset - buf.arrayOffset())
    new String(cp)
  }

  /**
    * Decoding for normal non-array `ByteBuffer`
    */
  protected def decodeFastBuf(len: Int, buf: ByteBuffer): String = {
    val cp  = new Array[Char](len)
    var i   = 0
    var dst = 0
    while (dst < len) {
      val b = buf.get()
      if ((b & 0x80) == 0) {
        cp(dst) = (b & 0x7F).toChar
      } else if ((b & 0xC0) == 0x80) {
        val b1 = buf.get()
        i += 1
        cp(dst) = (b & 0x3F | (b1.toShort & 0xFF) << 6).toChar
      } else {
        val b1 = buf.get()
        val b2 = buf.get()
        i += 2
        cp(dst) = (b & 0x3F | (b1.toShort & 0xFF) << 6 | (b2.toShort << 14)).toChar
      }
      i += 1
      dst += 1
    }
    new String(cp)
  }
}

abstract class StringCodecBase extends StringCodecFast {
  def decodeUTF8(len: Int, buf: ByteBuffer): String

  def encodeUTF8(s: String): ByteBuffer

  def decodeUTF16(len: Int, buf: ByteBuffer): String

  def encodeUTF16(s: String): ByteBuffer
}
