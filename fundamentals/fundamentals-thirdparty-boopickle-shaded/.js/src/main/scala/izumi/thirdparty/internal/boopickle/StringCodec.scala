package izumi.thirdparty.internal.boopickle

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import scala.scalajs.js.typedarray._

/**
  * This is a fork-point from original `boopickle` codebase.
  *
  * LightTypeTags are encoded at compile-time and decoded at run-time,
  * but Scala does not use the JVM version of the artifact to run macros
  * from. Instead it tries to run the Scala.js version compiled for JVM,
  * but the optimized encoder in boopickle is using native JavaScript classes
  * so it can't work.
  *
  * Here we include both the JVM and JS versions of StringCodec and use the JVM
  * version for encode* methods and JS version for decode* methods.
  */
private[izumi] object StringCodec extends StringCodecBase {
  @inline override def decodeFast(len: Int, buf: ByteBuffer): String = {
    JSStringCodec.decodeFast(len, buf)
  }

  @inline override def decodeUTF8(len: Int, buf: ByteBuffer): String = {
    JSStringCodec.decodeUTF8(len, buf)
  }

  @inline override def decodeUTF16(len: Int, buf: ByteBuffer): String = {
    JSStringCodec.decodeUTF16(len, buf)
  }

  @inline override def encodeUTF8(str: String): ByteBuffer = {
    JVMStringCodec.encodeUTF8(str)
  }

  @inline override def encodeUTF16(str: String): ByteBuffer = {
    JVMStringCodec.encodeUTF16(str)
  }
}

/**
  * This is a fork-point from original `boopickle` codebase.
  *
  * ByteBufferProvider is only used for encoding. JVM encoder is incompatible with `DirectByteBufferProvider`,
  * so use `HeapByteBufferProvider`
  * */
private[izumi] object DefaultByteBufferProvider extends DefaultByteBufferProviderFuncs {
//  override def provider = new DirectByteBufferProvider
  override def provider = new HeapByteBufferProvider
}

private[boopickle] object JVMStringCodec extends StringCodecBase {
  override def decodeUTF8(len: Int, buf: ByteBuffer): String = {
    val a = new Array[Byte](len)
    buf.get(a)
    new String(a, StandardCharsets.UTF_8)
  }

  override def encodeUTF8(str: String): ByteBuffer = {
    ByteBuffer.wrap(str.getBytes(StandardCharsets.UTF_8))
  }

  override def decodeUTF16(len: Int, buf: ByteBuffer): String = {
    val a = new Array[Byte](len)
    buf.get(a)
    new String(a, StandardCharsets.UTF_16LE)
  }

  override def encodeUTF16(str: String): ByteBuffer = {
    ByteBuffer.wrap(str.getBytes(StandardCharsets.UTF_16LE))
  }
}

/**
  * Facade for native JS engine provided TextDecoder
  */
@js.native
@JSGlobal
private[boopickle] class TextDecoder(utfLabel: js.UndefOr[String] = js.undefined) extends js.Object {
  def decode(data: ArrayBufferView): String = js.native
}

/**
  * Facade for native JS engine provided TextEncoder
  */
@js.native
@JSGlobal
private[boopickle] class TextEncoder(utfLabel: js.UndefOr[String] = js.undefined) extends js.Object {
  def encode(str: String): Uint8Array = js.native
}

private[boopickle] object JSStringCodec extends StringCodecBase {
  private lazy val utf8decoder: (Int8Array) => String = {
    val td = new TextDecoder
    // use native TextDecoder
    (data: Int8Array) =>
      td.decode(data)
  }

  private lazy val utf8encoder: (String) => Int8Array = {
    val te = new TextEncoder
    // use native TextEncoder
    (str: String) =>
      new Int8Array(te.encode(str))
  }

  private lazy val utf16decoder: (Uint16Array) => String = {
    /*
      try {
          // do not use native TextDecoder as it's slow
          val td = new TextDecoder("utf-16none")
          (data: Uint16Array) => td.decode(data)
        } catch {
          case e: Throwable =>
     */
    (data: Uint16Array) =>
      js.Dynamic.global.String.fromCharCode.applyDynamic("apply")(null, data).asInstanceOf[String]
  }

  private lazy val utf16encoder: (String) => Int8Array = {
    /*
      try {
          // do not use native TextEncoder as it's slow
          val te = new TextEncoder("utf-16none")
          (str: String) => te.encode(str)
        } catch {
          case e: Throwable =>
          }
     */
    (str: String) =>
      {
        val ta = new Uint16Array(str.length)
        var i  = 0
        while (i < str.length) {
          ta(i) = str.charAt(i).toInt
          i += 1
        }
        new Int8Array(ta.buffer)
      }
  }

  override def decodeUTF8(len: Int, buf: ByteBuffer): String = {
    if (buf.isDirect && !js.isUndefined(js.Dynamic.global.TextDecoder)) {
      // get the underlying Int8Array
      val ta = buf.typedArray()
      val s  = utf8decoder(ta.subarray(buf.position(), buf.position() + len))
      (buf: java.nio.Buffer).position(buf.position() + len)
      s
    } else {
      val a = new Array[Byte](len)
      buf.get(a)
      new String(a, StandardCharsets.UTF_8)
    }
  }

  override def encodeUTF8(str: String): ByteBuffer = {
    if (js.isUndefined(js.Dynamic.global.TextEncoder)) {
      ByteBuffer.wrap(str.getBytes(StandardCharsets.UTF_8))
    } else {
      TypedArrayBuffer.wrap(utf8encoder(str))
    }
  }

  override def decodeUTF16(len: Int, buf: ByteBuffer): String = {
    if (buf.isDirect) {
      val ta = new Uint16Array(buf.typedArray().buffer, buf.position() + buf.typedArray().byteOffset, len / 2)
      (buf: java.nio.Buffer).position(buf.position() + len)
      utf16decoder(ta)
      //new String(ta.toArray) // alt implementation
    } else {
      val a = new Array[Byte](len)
      buf.get(a)
      new String(a, StandardCharsets.UTF_16LE)
    }
  }

  override def encodeUTF16(str: String): ByteBuffer = {
    TypedArrayBuffer.wrap(utf16encoder(str))
  }

  override def decodeFast(len: Int, buf: ByteBuffer): String = {
    if (buf.hasArray)
      decodeFastArray(len, buf)
    else
      decodeFastTypedArray(len, buf)
  }

  override def encodeFast(s: String, bb: ByteBuffer): Unit = {
    if (bb.hasArray)
      encodeFastArray(s, bb)
    else
      encodeFastTypedArray(s, bb)
  }

  protected def encodeFastTypedArray(s: String, bb: ByteBuffer): Unit = {
    val len     = s.length()
    val buf     = bb.typedArray()
    var dst     = bb.position()
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
    (bb: java.nio.Buffer).position(dst)
  }

  private def charArray2String(chars: js.Array[Int], offset: Int, len: Int): String = {
    // for some reason, on Chrome, calling `cp.jsSlice` makes `fromCharCode` 2-3x faster!
    js.Dynamic.global.String.fromCharCode
      .applyDynamic("apply")(null, chars.jsSlice(offset, offset + len))
      .asInstanceOf[String]
  }

  protected def decodeFastTypedArray(len: Int, buf: ByteBuffer): String = {
    val cp     = new js.Array[Int](len)
    val src    = buf.typedArray()
    var offset = buf.position()
    var dst    = 0
    while (dst < len) {
      val b = src(offset) & 0xFF
      offset += 1
      if ((b & 0x80) == 0) {
        cp(dst) = b
      } else if ((b & 0xC0) == 0x80) {
        val b1 = src(offset) & 0xFF
        offset += 1
        cp(dst) = b & 0x3F | b1 << 6
      } else {
        val b1 = src(offset) & 0xFF
        val b2 = src(offset + 1) & 0xFF
        offset += 2
        cp(dst) = b & 0x3F | b1 << 6 | b2 << 14
      }
      dst += 1
    }
    (buf: java.nio.Buffer).position(offset)

    // for really long strings, convert in pieces to avoid JS engine overflows
    if (len > 4096) {
      offset = 0
      var s = ""
      while (offset < len) {
        s += charArray2String(cp, offset, math.min(4096, len - offset))
        offset += 4096
      }
      s
    } else {
      charArray2String(cp, 0, len)
    }
  }
}
