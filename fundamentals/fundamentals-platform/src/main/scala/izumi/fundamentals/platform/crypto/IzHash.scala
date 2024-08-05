package izumi.fundamentals.platform.crypto

import izumi.fundamentals.platform.IzPlatformPureUtil

import java.nio.charset.StandardCharsets
import izumi.fundamentals.platform.bytes.IzBytes.*

trait IzHash extends IzPlatformPureUtil {
  def hash(bytes: Array[Byte]): Array[Byte]

  final def hash(str: String): String = hash(str.getBytes(StandardCharsets.UTF_8)).toHex
}
