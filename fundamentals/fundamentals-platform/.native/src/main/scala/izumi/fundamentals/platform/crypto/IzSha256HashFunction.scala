package izumi.fundamentals.platform.crypto

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

object IzSha256HashFunction extends IzHashFunction {
  private final val HashBytes = 32

  override def hash(bytes: Array[Byte]): Array[Byte] = {
    val result = new Array[Byte](HashBytes)
    val input = if (bytes.isEmpty) null else bytes.at(0)
    val written = OpenSSLDigest.SHA256(input, bytes.length.toUSize, result.at(0))
    if (written == null) throw new IllegalStateException("OpenSSL SHA-256 failed")
    result
  }

  def setImported(): Unit = {}
  def getImpl: IzHashFunction = this

  override def id: IzHashId = IzHashId.SHA_256
}

@link("crypto")
@extern
private object OpenSSLDigest {
  def SHA256(input: Ptr[Byte], size: CSize, output: Ptr[Byte]): Ptr[Byte] = extern
}
