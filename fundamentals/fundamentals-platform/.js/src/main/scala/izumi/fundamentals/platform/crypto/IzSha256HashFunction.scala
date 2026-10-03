package izumi.fundamentals.platform.crypto

object IzSha256HashFunction extends IzHashFunction {
  override def hash(bytes: Array[Byte]): Array[Byte] = IzSha256.digest(bytes)

  override def id: IzHashId = IzHashId.SHA_256
}
