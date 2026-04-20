package izumi.functional.bio.test

import izumi.functional.bio.*
import izumi.functional.bio.cache.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import zio.IO

import scala.concurrent.duration.*

class BIOCachePropertyTest extends AnyWordSpec with Matchers with ScalaCheckPropertyChecks {

  private val runner: UnsafeRun2[IO] = UnsafeRun2.createZIO()
  private val F: IO2[IO] = implicitly

  private def unsafeRun[A](f: IO[Nothing, A]): A = runner.unsafeRun(f)

  "BIOCache property tests" should {

    "put-then-get is identity" in forAll { (key: String, value: Int) =>
      val result = unsafeRun {
        F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
          F.flatMap(cache.put(key, value)) { _ =>
            cache.get(key)
          }
        }
      }
      result shouldBe Some(value)
    }

    "last put wins" in forAll { (key: String, v1: Int, v2: Int) =>
      val result = unsafeRun {
        F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
          F.flatMap(cache.put(key, v1)) { _ =>
            F.flatMap(cache.put(key, v2)) { _ =>
              cache.get(key)
            }
          }
        }
      }
      result shouldBe Some(v2)
    }

    "invalidate removes the key" in forAll { (key: String, value: Int) =>
      val result = unsafeRun {
        F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
          F.flatMap(cache.put(key, value)) { _ =>
            F.flatMap(cache.invalidate(key)) { _ =>
              cache.get(key)
            }
          }
        }
      }
      result shouldBe None
    }

    "computeIfAbsent is idempotent on success" in forAll { (key: String, value: Int) =>
      val result = unsafeRun {
        F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
          F.flatMap(cache.computeIfAbsent[Nothing](key, F.pure(value))) { v1 =>
            F.map(cache.computeIfAbsent[Nothing](key, F.pure(value + 1000))) { v2 =>
              (v1, v2)
            }
          }
        }
      }
      result shouldBe ((value, value))
    }

    "size equals number of distinct keys put" in forAll { (keys: Set[String]) =>
      whenever(keys.size <= 500) {
        val result = unsafeRun {
          F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
            F.flatMap(F.traverse(keys.toList)(k => cache.put(k, k.hashCode))) { _ =>
              cache.size
            }
          }
        }
        result shouldBe keys.size
      }
    }

    "keys returns exactly the keys that were put" in forAll { (entries: Map[String, Int]) =>
      whenever(entries.size <= 500) {
        val result = unsafeRun {
          F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
            F.flatMap(F.traverse(entries.toList) { case (k, v) => cache.put(k, v) }) { _ =>
              cache.keys
            }
          }
        }
        result shouldBe entries.keySet
      }
    }

    "invalidateAll makes all gets return None" in forAll { (entries: Map[String, Int]) =>
      whenever(entries.nonEmpty && entries.size <= 500) {
        val result = unsafeRun {
          F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
            F.flatMap(F.traverse(entries.toList) { case (k, v) => cache.put(k, v) }) { _ =>
              F.flatMap(cache.invalidateAll) { _ =>
                F.flatMap(cache.size) { s =>
                  F.map(F.traverse(entries.keys.toList)(cache.get)) { gets =>
                    (s, gets)
                  }
                }
              }
            }
          }
        }
        result._1 shouldBe 0
        result._2.forall(_.isEmpty) shouldBe true
      }
    }

    "multiple puts to same key don't change size" in forAll { (key: String, values: List[Int]) =>
      whenever(values.nonEmpty && values.size <= 500) {
        val result = unsafeRun {
          F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
            F.flatMap(F.traverse(values)(v => cache.put(key, v))) { _ =>
              cache.size
            }
          }
        }
        result shouldBe 1
      }
    }

    "computeIfAbsent failure does not leave stale entries" in forAll { (key: String) =>
      val result = unsafeRun {
        F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
          F.flatMap(F.attempt(cache.computeIfAbsent[String](key, F.fail("err")))) { _ =>
            F.flatMap(cache.get(key)) { v =>
              F.map(cache.size) { s =>
                (v, s)
              }
            }
          }
        }
      }
      result shouldBe ((None, 0))
    }

    "entries with TTL expire after the duration" in forAll { (key: String, value: Int) =>
      val result = unsafeRun {
        F.flatMap(BIOCache.make[IO, String, Int](CacheConfig(defaultTTL = Some(1.millis)))) { cache =>
          F.flatMap(cache.put(key, value)) { _ =>
            // Sleep just enough to expire
            F.flatMap(implicitly[Temporal2[IO]].sleep(5.millis)) { _ =>
              cache.get(key)
            }
          }
        }
      }
      result shouldBe None
    }

    "computeIfAbsent with different keys don't interfere" in forAll { (k1: String, k2: String, v1: Int, v2: Int) =>
      whenever(k1 != k2) {
        val result = unsafeRun {
          F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
            F.flatMap(cache.computeIfAbsent[Nothing](k1, F.pure(v1))) { r1 =>
              F.map(cache.computeIfAbsent[Nothing](k2, F.pure(v2))) { r2 =>
                (r1, r2)
              }
            }
          }
        }
        result shouldBe ((v1, v2))
      }
    }
  }
}
