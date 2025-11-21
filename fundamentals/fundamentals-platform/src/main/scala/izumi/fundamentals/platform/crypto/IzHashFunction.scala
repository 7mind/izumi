package izumi.fundamentals.platform.crypto

import izumi.fundamentals.platform.IzPlatformFunctionCollection
import izumi.fundamentals.platform.bytes.IzBytes.*

import java.nio.charset.StandardCharsets

trait IzHashFunction extends IzPlatformFunctionCollection {
  def id: IzHashId

  def hash(bytes: Array[Byte]): Array[Byte]

  final def hash(str: String): String = hash(str.getBytes(StandardCharsets.UTF_8)).toHex
}

sealed trait IzHashId

object IzHashId {
  case object SHA_256 extends IzHashId
}
