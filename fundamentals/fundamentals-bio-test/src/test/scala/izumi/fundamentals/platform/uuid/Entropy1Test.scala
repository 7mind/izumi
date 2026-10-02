package izumi.fundamentals.platform.uuid

import izumi.functional.bio.Entropy1
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

class Entropy1Test extends AnyWordSpec {

  val entropy: Entropy1[Identity] = Entropy1.Standard

  "Entropy1" should {

    "generate different booleans over multiple calls" in {
      val first = entropy.nextBoolean()
      val second = (1 to 1000).iterator.map(_ => entropy.nextBoolean()).find(_ != first)
      assert(!second.contains(first))
    }

    "generate different ints" in {
      val result1 = entropy.nextInt()
      val result2 = entropy.nextInt()
      val result3 = entropy.nextInt()
      assert(result1 != result2 || result2 != result3)
    }

    "generate different ints with max bound" in {
      val results = (1 to 100).map(_ => entropy.nextInt(1000))
      assert(results.distinct.size > 1)
      assert(results.forall(r => r >= 0 && r < 1000))
    }

    "reject non-positive max in nextInt" in {
      assertThrows[IllegalArgumentException] {
        entropy.nextInt(0)
      }
      assertThrows[IllegalArgumentException] {
        entropy.nextInt(-1)
      }
    }

    "generate different longs" in {
      val result1 = entropy.nextLong()
      val result2 = entropy.nextLong()
      val result3 = entropy.nextLong()
      assert(result1 != result2 || result2 != result3)
    }

    "generate different longs with max bound" in {
      val results = (1 to 100).map(_ => entropy.nextLong(1000L))
      assert(results.distinct.size > 1)
      assert(results.forall(r => r >= 0L && r < 1000L))
    }

    "reject non-positive max in nextLong" in {
      assertThrows[IllegalArgumentException] {
        entropy.nextLong(0L)
      }
      assertThrows[IllegalArgumentException] {
        entropy.nextLong(-1L)
      }
    }

    "generate different floats" in {
      val results = (1 to 100).map(_ => entropy.nextFloat())
      assert(results.distinct.size > 1)
      assert(results.forall(r => r >= 0.0f && r <= 1.0f))
    }

    "generate different doubles" in {
      val results = (1 to 100).map(_ => entropy.nextDouble())
      assert(results.distinct.size > 1)
      assert(results.forall(r => r >= 0.0 && r <= 1.0))
    }

    "generate different gaussians" in {
      val results = (1 to 100).map(_ => entropy.nextGaussian())
      assert(results.distinct.size > 1)
      assert(results.forall(r => !r.isNaN && !r.isInfinite))
    }

    "generate different byte arrays" in {
      val result1 = entropy.nextBytes(16)
      val result2 = entropy.nextBytes(16)
      assert(result1.length == 16)
      assert(result2.length == 16)
      assert(!java.util.Arrays.equals(result1, result2))
    }

    "generate empty bytes array" in {
      val result = entropy.nextBytes(0)
      assert(result.length == 0)
    }

    "generate different printable chars" in {
      val results = (1 to 100).map(_ => entropy.nextPrintableChar())
      assert(results.distinct.size > 1)
      assert(results.forall(c => c >= '!' && c <= '~'))
    }

    "generate different strings" in {
      val result1 = entropy.nextString(10)
      val result2 = entropy.nextString(10)
      assert(result1.length == 10)
      assert(result2.length == 10)
      assert(result1 != result2)
    }

    "generate different time UUIDs" in {
      val result1 = entropy.nextTimeUUID()
      val result2 = entropy.nextTimeUUID()
      assert(result1 != result2)
      assert(result1.version() == 1)
      assert(result2.version() == 1)
    }

    "generate different random UUIDs" in {
      val result1 = entropy.nextUUID()
      val result2 = entropy.nextUUID()
      assert(result1 != result2)
      assert(result1.version() == 4)
      assert(result2.version() == 4)
    }

    "shuffle collection differently" in {
      val original = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      val shuffled1 = entropy.shuffle(original)
      val shuffled2 = entropy.shuffle(original)
      assert(shuffled1.toSet == original.toSet)
      assert(shuffled2.toSet == original.toSet)
      assert(shuffled1 != original || shuffled2 != original)
    }

    "create deterministic entropy with seed" in {
      val seeded1 = entropy.withSeed(42L)
      val seeded2 = entropy.withSeed(42L)
      val results1 = (1 to 10).map(_ => seeded1.nextInt())
      val results2 = (1 to 10).map(_ => seeded2.nextInt())
      assert(results1 == results2)
    }

    "set seed for deterministic output" in {
      val mutableEntropy = entropy.withSeed(0L)
      mutableEntropy.setSeed(42L)
      val seeded = entropy.withSeed(42L)
      val result1 = mutableEntropy.nextInt()
      val result2 = seeded.nextInt()
      assert(result1 == result2)
    }

    "write different random bytes each time" in {
      val bytes1 = new Array[Byte](16)
      val bytes2 = new Array[Byte](16)
      entropy.writeRandomBytes(bytes1)
      entropy.writeRandomBytes(bytes2)
      assert(!java.util.Arrays.equals(bytes1, bytes2))
    }

  }

}
