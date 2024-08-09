package izumi.fundamentals.platform.crypto

import java.security.MessageDigest

object IzSha256Hash extends IzHashFunction {
  // scalajs workarounds
  def setImported(): Unit = {}
  def getImpl: IzHashFunction = this

  override def hash(bytes: Array[Byte]): Array[Byte] = {
    MessageDigest.getInstance("SHA-256").digest(bytes)
  }
}
