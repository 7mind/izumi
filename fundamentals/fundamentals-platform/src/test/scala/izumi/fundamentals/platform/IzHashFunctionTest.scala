package izumi.fundamentals.platform

import izumi.fundamentals.platform.crypto.IzHash
import org.scalatest.wordspec.AnyWordSpec

class IzHashFunctionTest extends AnyWordSpec {
  "sha256 hash" should {
    "produce hex output" in {
      import izumi.fundamentals.platform.crypto.IzSha256HashFunction
      IzSha256HashFunction.setImported()

      for (_ <- 0 to 2) {
        assert(IzHash.sha256("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
      }
    }
  }
}
