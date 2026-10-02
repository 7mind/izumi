package izumi.fundamentals.platform.uuid

import org.scalatest.wordspec.AnyWordSpec

class NativeSecureRandomTest extends AnyWordSpec {
  private final val MultipleRequestBufferBytes = 1000
  private final val ObservationWindowBytes = 100

  "Native secure random" should {
    for (size <- Seq(0, 255, 256, 257, 1000)) {
      s"fill a $size-byte buffer across getentropy request boundaries" in {
        val random = new __SecureRandomPlatformSpecific.SecureRandomImpl()
        random.nextBytes(new Array[Byte](size))
        succeed
      }
    }

    "write throughout a buffer requiring multiple entropy requests" in {
      val random = new __SecureRandomPlatformSpecific.SecureRandomImpl()
      val bytes = new Array[Byte](MultipleRequestBufferBytes)
      random.nextBytes(bytes)
      bytes.grouped(ObservationWindowBytes).foreach(window => assert(window.exists(_ != 0)))
    }
  }
}
