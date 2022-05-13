package izumi.fundamentals.platform.serialization

import java.io.{ByteArrayOutputStream, ObjectOutputStream}
import java.nio.ByteBuffer

import scala.language.implicitConversions

final class IzSerializable(private val s: Serializable) extends AnyVal {
  def toByteBuffer: ByteBuffer = {
    val byteArrayOutputStream = new ByteArrayOutputStream
    val objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)
    objectOutputStream.writeObject(s)
    ByteBuffer.wrap(byteArrayOutputStream.toByteArray)
  }
}

object IzSerializable {
  implicit def toRichSerializable(s: Serializable): IzSerializable = new IzSerializable(s)
}
