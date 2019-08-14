package com.github.pshirshov.izumi.fundamentals.platform.crypto

import java.nio.charset.StandardCharsets

import com.github.pshirshov.izumi.fundamentals.platform.bytes.IzBytes._

trait IzHash {
  def hash(bytes: Array[Byte]): Array[Byte]

  final def hash(str: String): String = hash(str.getBytes(StandardCharsets.UTF_8)).toHex
}




