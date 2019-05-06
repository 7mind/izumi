
package com.github.pshirshov.izumi.fundamentals.platform

import com.github.pshirshov.izumi.fundamentals.platform.crypto.{IzMurmur32Hash, IzSha256Hash}
import org.scalatest.WordSpec



class IzHashTest extends WordSpec {

  "murmur32 hash" should {
    "produce hex output" in {
      assert(IzMurmur32Hash.hash("abc") == "184951eb")
    }
  }

  "sha256 hash" should {
    "produce hex output" in {
      assert(IzSha256Hash.hash("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
  }
}

