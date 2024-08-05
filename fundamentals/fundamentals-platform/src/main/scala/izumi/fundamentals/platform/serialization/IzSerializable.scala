package izumi.fundamentals.platform.serialization

import izumi.fundamentals.platform.IzPlatformSyntax

import java.io.{ByteArrayOutputStream, ObjectOutputStream}
import java.nio.ByteBuffer
import scala.language.implicitConversions

final class SerializableExt(private val s: Serializable) extends AnyVal {
  def toByteBuffer: ByteBuffer = {
    val byteArrayOutputStream = new ByteArrayOutputStream()
    val objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)
    objectOutputStream.writeObject(s)
    ByteBuffer.wrap(byteArrayOutputStream.toByteArray)
  }
}

trait IzSerializable extends IzPlatformSyntax {
  implicit def toRichSerializable(s: Serializable): SerializableExt = new SerializableExt(s)

}

object IzSerializable extends IzSerializable {}
