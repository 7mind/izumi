package izumi.fundamentals.platform.crypto

/** SHA-256 as specified in FIPS 180-4. Platform-independent; the JS platform has no `MessageDigest`. */
private[platform] object IzSha256 {
  private final val BlockSize = 64
  private final val LengthFieldSize = 8
  private final val DigestSize = 32
  private final val Rounds = 64

  private val K: Array[Int] = Array(
    0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5, 0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74,
    0x80DEB1FE, 0x9BDC06A7, 0xC19BF174, 0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA, 0x983E5152, 0xA831C66D,
    0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967, 0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E,
    0x92722C85, 0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070, 0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5,
    0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3, 0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2,
  )

  private val InitialState: Array[Int] = Array(0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19)

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
      w(t) = ((data(o) & 0xFF) << 24) | ((data(o + 1) & 0xFF) << 16) | ((data(o + 2) & 0xFF) << 8) | (data(o + 3) & 0xFF)
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
