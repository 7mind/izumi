package izumi.fundamentals.platform

import izumi.fundamentals.platform.crypto.IzSha256
import org.scalatest.wordspec.AnyWordSpec

import java.security.MessageDigest
import scala.util.Random

class IzSha256Test extends AnyWordSpec {
  "portable sha256" should {
    "agree with MessageDigest for every message length around the block boundaries" in {
      val random = new Random(0)
      (0 to 300).foreach {
        length =>
          val message = new Array[Byte](length)
          random.nextBytes(message)
          assert(IzSha256.digest(message).toSeq == MessageDigest.getInstance("SHA-256").digest(message).toSeq, s"length=$length")
      }
    }

    "agree with MessageDigest for large messages" in {
      val random = new Random(1)
      Seq(4095, 65536, 1000003).foreach {
        length =>
          val message = new Array[Byte](length)
          random.nextBytes(message)
          assert(IzSha256.digest(message).toSeq == MessageDigest.getInstance("SHA-256").digest(message).toSeq, s"length=$length")
      }
    }
  }
}
