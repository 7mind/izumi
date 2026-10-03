package izumi.fundamentals.platform.crypto

import java.security.MessageDigest

object IzSha256HashFunction extends IzHashFunction {
  override def hash(bytes: Array[Byte]): Array[Byte] = {
    MessageDigest.getInstance("SHA-256").digest(bytes)
  }

  override def id: IzHashId = IzHashId.SHA_256
}
