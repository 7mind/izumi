package izumi.fundamentals.platform.bytes

import java.io.{ByteArrayInputStream, ObjectInputStream}

final class IzByteArray(private val bytes: Array[Byte]) extends AnyVal {
  def readObject[T]: T = {
    val byteArrayInputStream = new ByteArrayInputStream(bytes)
    val objectInputStream = new ObjectInputStream(byteArrayInputStream)
    objectInputStream.readObject().asInstanceOf[T]
  }

  def toHex: String = {
    bytes.toSeq.map(v => "%02x".format(v & 0xFF)).mkString
  }
}
