package com.github.pshirshov.izumi.fundamentals.platform.serialization

import java.io.{ByteArrayInputStream, ObjectInputStream}


class IzByteArray(bytes: Array[Byte]) {
  def readObject[T]: T = {
    val byteArrayInputStream = new ByteArrayInputStream(bytes)
    val objectInputStream = new ObjectInputStream(byteArrayInputStream)
    objectInputStream.readObject().asInstanceOf[T]
  }
}
