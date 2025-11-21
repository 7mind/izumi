package izumi.fundamentals.platform.bytes

import izumi.fundamentals.platform.IzPlatformSyntax

import java.nio.ByteBuffer
import scala.language.implicitConversions

trait IzBytes extends IzPlatformSyntax {
  implicit def toRichByteBuffer(bytes: ByteBuffer): IzByteBuffer = new IzByteBuffer(bytes)
  implicit def toRichByteArray(bytes: Array[Byte]): IzByteArray = new IzByteArray(bytes)
}

object IzBytes extends IzBytes {}
