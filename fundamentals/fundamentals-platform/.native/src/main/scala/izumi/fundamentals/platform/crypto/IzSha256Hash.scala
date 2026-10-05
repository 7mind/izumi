package izumi.fundamentals.platform.crypto

import java.security.MessageDigest

object IzSha256Hash extends IzHashFunction {
  @deprecated("SHA-256 is implemented portably, no JS module needs to be imported", "1.3.0")
  def setImported(): Unit = ()

  @deprecated("Use IzSha256Hash directly", "1.3.0")
  def getImpl: IzHashFunction = this

  override def hash(bytes: Array[Byte]): Array[Byte] = {
    MessageDigest.getInstance("SHA-256").digest(bytes)
  }
}
