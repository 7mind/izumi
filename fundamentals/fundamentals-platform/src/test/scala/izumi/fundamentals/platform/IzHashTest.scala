
package com.github.pshirshov.izumi.fundamentals.platform

import org.scalatest.WordSpec

class IzHashTest extends WordSpec {

//  "murmur32 hash" should {
//    "produce hex output" in {
//      import com.github.pshirshov.izumi.fundamentals.platform.crypto.IzMurmur32Hash
//      assert(IzMurmur32Hash.hash("abc") == "88ab65b3")
//    }
//  }

  "sha256 hash" should {
    "produce hex output" in {
      import com.github.pshirshov.izumi.fundamentals.platform.crypto.IzSha256Hash
      assert(IzSha256Hash.hash("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
  }
}

