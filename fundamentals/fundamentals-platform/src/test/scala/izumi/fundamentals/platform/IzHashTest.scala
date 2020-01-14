
package izumi.fundamentals.platform

import org.scalatest.wordspec.AnyWordSpec

class IzHashTest extends AnyWordSpec {

//  "murmur32 hash" should {
//    "produce hex output" in {
//      import izumi.fundamentals.platform.crypto.IzMurmur32Hash
//      assert(IzMurmur32Hash.hash("abc") == "88ab65b3")
//    }
//  }

  "sha256 hash" should {
    "produce hex output" in {
      import izumi.fundamentals.platform.crypto.IzSha256Hash
      IzSha256Hash.setImported()
      for (_ <- 0 to 2) {
        assert(IzSha256Hash.getImpl.hash("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
      }
    }
  }
}

