package izumi.fundamentals.platform

import org.scalatest.wordspec.AnyWordSpec
import izumi.fundamentals.platform.bytes.IzBytes.*
import izumi.fundamentals.platform.crypto.IzHash

class NativePlatformTest extends AnyWordSpec {
  "Native platform" should {
    "hash empty, binary, padding-boundary, and multi-block inputs" in {
      val vectors = List(
        0 -> "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        1 -> "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d",
        55 -> "463eb28e72f82e0a96c0a4cc53690c571281131f672aa229e0d45ae59b598b59",
        56 -> "da2ae4d6b36748f2a318f23e7ab1dfdf45acdc9d049bd80e59de82a60895f562",
        63 -> "29af2686fd53374a36b0846694cc342177e428d1647515f078784d69cdb9e488",
        64 -> "fdeab9acf3710362bd2658cdc9a29e8f9c757fcf9811603a8c447cd1d9151108",
        65 -> "4bfd2c8b6f1eec7a2afeb48b934ee4b2694182027e6d0fc075074f2fabb31781",
        1000 -> "a8af099bf2e878609558dbf69d8f88f4a31040a8cf84b549a0cfa912f12ffc3f",
      )
      vectors.foreach { case (size, expected) =>
        val input = Array.tabulate[Byte](size)(_.toByte)
        assert(IzHash.sha256(input).toHex == expected)
      }
    }

    "identify the target runtime" in {
      assert(IzPlatform.platform == ScalaPlatform.Native)
      assert(!IzPlatform.isScalaJS)
      assert(!IzPlatform.isGraalNativeImage)
    }

    "represent unavailable JVM introspection explicitly" in {
      assert(IzPlatform.getClasspath().isEmpty)
      assert(IzPlatform.getRuntimeMXBeanJVMArgs().isEmpty)
    }
  }
}
