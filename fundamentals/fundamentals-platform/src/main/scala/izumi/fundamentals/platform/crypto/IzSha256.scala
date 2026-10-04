package izumi.fundamentals.platform.crypto

/** SHA-256 as specified in FIPS 180-4. Platform-independent; the JS platform has no `MessageDigest`. */
private[platform] object IzSha256 {
  private final val BlockSize = 64
  private final val LengthFieldSize = 8
  private final val DigestSize = 32
  private final val Rounds = 64

  private val K: Array[Int] = Array(
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74,
    0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d,
    0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e,
    0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
  )

  private val InitialState: Array[Int] = Array(0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19)

  def digest(message: Array[Byte]): Array[Byte] = {
    val padded = pad(message)
    val state = InitialState.clone()
    val schedule = new Array[Int](Rounds)
    var block = 0
    while (block < padded.length) {
      compress(state, schedule, padded, block)
      block += BlockSize
    }
    val out = new Array[Byte](DigestSize)
    var i = 0
    while (i < state.length) {
      out(i * 4) = (state(i) >>> 24).toByte
      out(i * 4 + 1) = (state(i) >>> 16).toByte
      out(i * 4 + 2) = (state(i) >>> 8).toByte
      out(i * 4 + 3) = state(i).toByte
      i += 1
    }
    out
  }

  private def pad(message: Array[Byte]): Array[Byte] = {
    val paddedLength = ((message.length + LengthFieldSize) / BlockSize + 1) * BlockSize
    val padded = new Array[Byte](paddedLength)
    System.arraycopy(message, 0, padded, 0, message.length)
    padded(message.length) = 0x80.toByte
    val bitLength = message.length.toLong * 8
    var i = 0
    while (i < LengthFieldSize) {
      padded(paddedLength - 1 - i) = (bitLength >>> (8 * i)).toByte
      i += 1
    }
    padded
  }

  private def compress(state: Array[Int], w: Array[Int], data: Array[Byte], offset: Int): Unit = {
    var t = 0
    while (t < 16) {
      val o = offset + t * 4
      w(t) = ((data(o) & 0xff) << 24) | ((data(o + 1) & 0xff) << 16) | ((data(o + 2) & 0xff) << 8) | (data(o + 3) & 0xff)
      t += 1
    }
    while (t < Rounds) {
      val s0 = Integer.rotateRight(w(t - 15), 7) ^ Integer.rotateRight(w(t - 15), 18) ^ (w(t - 15) >>> 3)
      val s1 = Integer.rotateRight(w(t - 2), 17) ^ Integer.rotateRight(w(t - 2), 19) ^ (w(t - 2) >>> 10)
      w(t) = w(t - 16) + s0 + w(t - 7) + s1
      t += 1
    }

    var a = state(0)
    var b = state(1)
    var c = state(2)
    var d = state(3)
    var e = state(4)
    var f = state(5)
    var g = state(6)
    var h = state(7)
    t = 0
    while (t < Rounds) {
      val bigS1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25)
      val ch = (e & f) ^ (~e & g)
      val temp1 = h + bigS1 + ch + K(t) + w(t)
      val bigS0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22)
      val maj = (a & b) ^ (a & c) ^ (b & c)
      val temp2 = bigS0 + maj
      h = g
      g = f
      f = e
      e = d + temp1
      d = c
      c = b
      b = a
      a = temp1 + temp2
      t += 1
    }
    state(0) += a
    state(1) += b
    state(2) += c
    state(3) += d
    state(4) += e
    state(5) += f
    state(6) += g
    state(7) += h
  }
}
