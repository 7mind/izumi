package izumi.fundamentals.platform.crypto

trait IzHash {
  def sha256(bytes: Array[Byte]): Array[Byte]
  def sha256(str: String): String
}

object IzHash extends IzHash {
  override def sha256(bytes: Array[Byte]): Array[Byte] = {
    IzSha256HashFunction.hash(bytes)
  }

  override def sha256(str: String): String = {
    IzSha256HashFunction.hash(str)
  }
}
