package izumi.fundamentals.platform.bytes

import java.nio.ByteBuffer

final class IzByteBuffer(private val bytes: ByteBuffer) extends AnyVal {
  def toByteArray: Array[Byte] = {
    val b = new Array[Byte](bytes.remaining())
    bytes.get(b)
    b
  }

  def readObject[T]: T = {
    import IzBytes._
    toByteArray.readObject[T]
  }
}
