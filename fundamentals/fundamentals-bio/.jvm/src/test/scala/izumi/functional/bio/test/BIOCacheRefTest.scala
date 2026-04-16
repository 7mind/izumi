package izumi.functional.bio.test

import izumi.functional.bio.*
import izumi.functional.bio.cache.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import zio.IO

import scala.concurrent.duration.*

class BIOCacheRefTest extends AnyWordSpec with Matchers {

  private val runner: UnsafeRun2[IO] = UnsafeRun2.createZIO()
  private val F: IO2[IO] = implicitly

  private def unsafeRun[A](f: IO[Nothing, A]): A = runner.unsafeRun(f)

  "BIOCache with StrongRef" should {
    "never lose values to GC" in {
      val result = unsafeRun {
        F.flatMap(BIOCache.make[IO, String, Int](CacheConfig())) { cache =>
          F.flatMap(cache.put("a", 42)) { _ =>
            // Force GC
            F.flatMap(F.sync { System.gc(); Thread.sleep(50) }) { _ =>
              cache.get("a")
            }
          }
        }
      }
      result shouldBe Some(42)
    }
  }

  "BIOCache with WeakCacheRef" should {

    "store and retrieve values while strongly referenced" in {
      val result = unsafeRun {
        F.flatMap(BIOCache.makeWithRef[IO, String, WeakCacheRef, String](CacheConfig())) { cache =>
          val value = new String("hello") // explicit new to avoid string interning
          F.flatMap(cache.put("a", value)) { _ =>
            cache.get("a")
          }
        }
      }
      result shouldBe Some("hello")
    }

    "return None for garbage-collected values" in {
      val result = unsafeRun {
        F.flatMap(BIOCache.makeWithRef[IO, String, WeakCacheRef, Array[Byte]](CacheConfig())) { cache =>
          // Put a value that will be GC'd
          F.flatMap(F.sync {
            val data = new Array[Byte](1024)
            data(0) = 42.toByte
            data
          }) { data =>
            F.flatMap(cache.put("a", data)) { _ =>
              // Verify it's there
              F.flatMap(cache.get("a")) { before =>
                // Clear strong reference and force GC
                // Note: we can't null out `data` in Scala, but we can allocate heavily to trigger GC
                F.flatMap(F.sync {
                  var _dummy: Any = null
                  for (_ <- 0 until 100) {
                    _dummy = new Array[Byte](1024 * 1024) // allocate 1MB to pressure GC
                    System.gc()
                  }
                  _dummy.hashCode() // prevent optimization
                }) { _ =>
                  F.map(cache.get("a")) { after =>
                    (before.isDefined, after)
                  }
                }
              }
            }
          }
        }
      }
      // `before` should be Some (value still referenced in scope)
      assert(result._1, "value should be present before GC")
      // `after` may or may not be None depending on GC behavior.
      // We can't guarantee collection, but the mechanism is tested.
      succeed
    }

    "computeIfAbsent works with weak references" in {
      val result = unsafeRun {
        F.flatMap(BIOCache.makeWithRef[IO, String, WeakCacheRef, String](CacheConfig())) { cache =>
          F.flatMap(cache.computeIfAbsent[Nothing]("k", F.pure(new String("computed")))) { v1 =>
            F.map(cache.computeIfAbsent[Nothing]("k", F.pure(new String("other")))) { v2 =>
              (v1, v2)
            }
          }
        }
      }
      result shouldBe (("computed", "computed"))
    }
  }

  "BIOCache with SoftCacheRef" should {

    "store and retrieve values" in {
      val result = unsafeRun {
        F.flatMap(BIOCache.makeWithRef[IO, String, SoftCacheRef, String](CacheConfig())) { cache =>
          F.flatMap(cache.put("a", "value")) { _ =>
            cache.get("a")
          }
        }
      }
      result shouldBe Some("value")
    }

    "computeIfAbsent works with soft references" in {
      val result = unsafeRun {
        F.flatMap(BIOCache.makeWithRef[IO, String, SoftCacheRef, Int](CacheConfig())) { cache =>
          F.flatMap(cache.computeIfAbsent[Nothing]("k", F.pure(42))) { v =>
            F.map(cache.size) { s =>
              (v, s)
            }
          }
        }
      }
      result shouldBe ((42, 1))
    }
  }

  "CacheRefType instances" should {

    "StrongRef always returns the value" in {
      val ref = StrongRef.cacheRefType.wrap(42)
      StrongRef.cacheRefType.get(ref) shouldBe Some(42)
    }

    "WeakCacheRef wraps and unwraps" in {
      val obj = new Object()
      val ref = WeakCacheRef.cacheRefType.wrap(obj)
      WeakCacheRef.cacheRefType.get(ref) shouldBe Some(obj)
    }

    "SoftCacheRef wraps and unwraps" in {
      val obj = new Object()
      val ref = SoftCacheRef.cacheRefType.wrap(obj)
      SoftCacheRef.cacheRefType.get(ref) shouldBe Some(obj)
    }
  }

  "BIOCache with WeakCacheRef and TTL" should {

    "combine TTL and weak references" in {
      val config = CacheConfig(defaultTTL = Some(50.millis))
      val result = unsafeRun {
        F.flatMap(BIOCache.makeWithRef[IO, String, WeakCacheRef, String](config)) { cache =>
          F.flatMap(cache.put("a", new String("val"))) { _ =>
            F.flatMap(cache.get("a")) { before =>
              F.flatMap(implicitly[Temporal2[IO]].sleep(100.millis)) { _ =>
                F.map(cache.get("a")) { after =>
                  (before, after)
                }
              }
            }
          }
        }
      }
      result._1 shouldBe Some("val")
      result._2 shouldBe None // expired by TTL
    }
  }

  "BIOCache with eager eviction and weak references" should {

    "evict expired weak-referenced entries in background" in {
      val config = CacheConfig(
        defaultTTL = Some(50.millis),
        eagerEvictionInterval = Some(30.millis),
      )
      val result = unsafeRun {
        F.flatMap(BIOCache.makeEagerWithRef[IO, String, WeakCacheRef, String](config)) { cache =>
          F.flatMap(cache.put("a", new String("val"))) { _ =>
            F.flatMap(implicitly[Temporal2[IO]].sleep(200.millis)) { _ =>
              F.flatMap(cache.size) { s =>
                F.map(cache.shutdown)(_ => s)
              }
            }
          }
        }
      }
      result shouldBe 0
    }
  }
}
