package izumi.fundamentals.platform.crypto

import java.nio.charset.StandardCharsets

import izumi.fundamentals.platform.bytes.IzBytes._

trait IzHash {
  def hash(bytes: Array[Byte]): Array[Byte]

  final def hash(str: String): String = hash(str.getBytes(StandardCharsets.UTF_8)).toHex
}
