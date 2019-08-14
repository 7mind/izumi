package com.github.pshirshov.izumi.fundamentals.platform.crypto

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array

@js.native
@JSImport("hash.js", "sha256")
class SHA256() extends js.Object {
  def update(@deprecated("scalajs", "warnings") msg: Uint8Array): Unit = js.native
  def digest(@deprecated("scalajs", "warnings") enc: String): String = js.native
}

object IzSha256Hash extends IzHash {
  override def hash(bytes: Array[Byte]): Array[Byte] = {
    val sha256 = new SHA256()
    sha256.update(new Uint8Array(bytes.toJSArray))
    val hexdigest = sha256.digest("hex")
    hexdigest.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }
}
