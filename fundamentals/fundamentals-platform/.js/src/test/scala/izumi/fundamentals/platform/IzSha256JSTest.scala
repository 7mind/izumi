package izumi.fundamentals.platform

import izumi.fundamentals.platform.bytes.IzBytes.*
import izumi.fundamentals.platform.crypto.IzSha256
import org.scalatest.wordspec.AnyWordSpec

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.typedarray.Int8Array
import scala.util.Random

class IzSha256JSTest extends AnyWordSpec {
  private def nodeSha256(bytes: Array[Byte]): String = {
    val crypto = js.Dynamic.global.require("crypto")
    crypto.createHash("sha256").update(new Int8Array(bytes.toJSArray)).digest("hex").asInstanceOf[String]
  }

  "portable sha256" should {
    "agree with node:crypto for every message length around the block boundaries" in {
      val random = new Random(0)
      (0 to 300).foreach {
        length =>
          val message = new Array[Byte](length)
          random.nextBytes(message)
          assert(IzSha256.digest(message).toHex == nodeSha256(message), s"length=$length")
      }
    }

    "agree with node:crypto for large messages" in {
      val random = new Random(1)
      Seq(4095, 65536, 1000003).foreach {
        length =>
          val message = new Array[Byte](length)
          random.nextBytes(message)
          assert(IzSha256.digest(message).toHex == nodeSha256(message), s"length=$length")
      }
    }
  }
}
