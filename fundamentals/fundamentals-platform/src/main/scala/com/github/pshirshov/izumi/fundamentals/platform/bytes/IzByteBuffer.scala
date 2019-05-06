package com.github.pshirshov.izumi.fundamentals.platform.bytes

import java.nio.ByteBuffer


class IzByteBuffer(bytes: ByteBuffer) {
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
