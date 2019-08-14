package com.github.pshirshov.izumi.fundamentals.platform.bytes

import java.nio.ByteBuffer

import scala.language.implicitConversions

object IzBytes {
  implicit def toRichByteBuffer(bytes: ByteBuffer): IzByteBuffer = new IzByteBuffer(bytes)
  implicit def toRichByteArray(bytes: Array[Byte]): IzByteArray = new IzByteArray(bytes)
}
