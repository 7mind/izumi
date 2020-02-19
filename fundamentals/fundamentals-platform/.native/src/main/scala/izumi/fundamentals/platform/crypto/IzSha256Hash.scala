package izumi.fundamentals.platform.crypto

import java.security.MessageDigest

object IzSha256Hash extends IzHash {
  // scalajs workarounds
  def setImported(): Unit = {}
  def getImpl: IzHash = this

  override def hash(bytes: Array[Byte]): Array[Byte] = {
    MessageDigest.getInstance("SHA-256").digest(bytes)
  }
}
