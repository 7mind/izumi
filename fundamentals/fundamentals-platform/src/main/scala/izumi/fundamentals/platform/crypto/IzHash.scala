package izumi.fundamentals.platform.crypto

trait IzHash {
  def sha256(bytes: Array[Byte]): Array[Byte]
}

object IzHash extends IzHash {
  override def sha256(bytes: Array[Byte]): Array[Byte] = {
    IzSha256HashFunction.hash(bytes)
  }
}
