package com.github.pshirshov.izumi.fundamentals.platform.serialization

import java.nio.ByteBuffer


class IzByteBuffer(bytes: ByteBuffer) {
  def toByteArray: Array[Byte] = {
    val b = new Array[Byte](bytes.remaining())
    bytes.get(b)
    b
  }

  def readObject[T]: T = {
    import IzSerializable._
    toByteArray.readObject[T]
  }
}
