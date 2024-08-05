package izumi.fundamentals.platform.crypto

import izumi.fundamentals.platform.IzPlatformFunctionCollection
import izumi.fundamentals.platform.bytes.IzBytes.*

import java.nio.charset.StandardCharsets

trait IzHash extends IzPlatformFunctionCollection {
  def hash(bytes: Array[Byte]): Array[Byte]

  final def hash(str: String): String = hash(str.getBytes(StandardCharsets.UTF_8)).toHex
}
