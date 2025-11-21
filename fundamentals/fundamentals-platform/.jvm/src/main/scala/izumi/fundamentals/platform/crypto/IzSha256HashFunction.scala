package izumi.fundamentals.platform.crypto

import java.security.MessageDigest

object IzSha256HashFunction extends IzHashFunction {
  override def hash(bytes: Array[Byte]): Array[Byte] = {
    MessageDigest.getInstance("SHA-256").digest(bytes)
  }

  // scalajs workaround compatibility
  def setImported(): Unit = {}
  def getImpl: IzHashFunction = this

  override def id: IzHashId = IzHashId.SHA_256
}
