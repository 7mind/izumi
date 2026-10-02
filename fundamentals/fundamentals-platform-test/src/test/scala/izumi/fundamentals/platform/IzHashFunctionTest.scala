package izumi.fundamentals.platform

import izumi.fundamentals.platform.Sha256TestVectors.{binaryVectors, textVectors}
import izumi.fundamentals.platform.bytes.IzBytes.*
import izumi.fundamentals.platform.crypto.IzHash
import org.scalatest.wordspec.AnyWordSpec

class IzHashFunctionTest extends AnyWordSpec {
  "sha256 hash" should {
    "match the FIPS 180-4 and NIST CAVP test vectors" in {
      textVectors.foreach {
        case (input, expected) =>
          assert(IzHash.sha256(input) == expected, input.take(32))
      }
      binaryVectors.foreach {
        case (input, expected) =>
          assert(IzHash.sha256(input).toHex == expected, input.toHex)
      }
    }
  }
}
