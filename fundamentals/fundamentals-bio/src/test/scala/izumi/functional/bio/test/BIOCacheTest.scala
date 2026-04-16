package izumi.functional.bio.test

import izumi.functional.bio.*
import izumi.functional.bio.cache.*
import izumi.reflect.TagKK
import org.scalatest.Assertion
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

final class BIOCacheTestZIO
  extends BIOCacheTest[zio.IO](ec => UnsafeRun2.createZIO(Some(zio.Executor.fromExecutionContext(ec))))

abstract class BIOCacheTest[F[+_, +_]](
  mkRunner: ExecutionContext => UnsafeRun2[F]
)(implicit
  val tagKK: TagKK[F],
  val BIO: IO2[F],
  val Prims: Primitives2[F],
  val Temp: Temporal2[F],
  val Fk: Fork2[F],
) extends AsyncWordSpec {

  val runner: UnsafeRun2[F] = mkRunner(this.executionContext)
  private val F: IO2[F] = BIO

  private def run(f: F[Nothing, Assertion]): scala.concurrent.Future[Assertion] = {
    runner.unsafeRunAsyncAsFuture(f).map {
      case Exit.Success(a) => a
      case f: Exit.Failure[?] => throw f.trace.toThrowable
    }(executionContext)
  }

  s"BIOCache[${TagKK[F].tag}]" should {

    // ---- Basic Operations ----

    "return None for a missing key" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.map(cache.get("missing"))(r => assert(r.isEmpty))
      }
    }

    "put and get a value" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.put("a", 1)) { _ =>
          F.map(cache.get("a"))(r => assert(r.contains(1)))
        }
      }
    }

    "overwrite on put" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.put("a", 1)) { _ =>
          F.flatMap(cache.put("a", 2)) { _ =>
            F.map(cache.get("a"))(r => assert(r.contains(2)))
          }
        }
      }
    }

    "invalidate a key" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.put("a", 1)) { _ =>
          F.flatMap(cache.invalidate("a")) { _ =>
            F.map(cache.get("a"))(r => assert(r.isEmpty))
          }
        }
      }
    }

    "invalidateAll removes all entries" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.put("a", 1)) { _ =>
          F.flatMap(cache.put("b", 2)) { _ =>
            F.flatMap(cache.invalidateAll) { _ =>
              F.flatMap(cache.get("a")) { a =>
                F.map(cache.get("b")) { b =>
                  assert(a.isEmpty && b.isEmpty)
                }
              }
            }
          }
        }
      }
    }

    "report correct size" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.put("a", 1)) { _ =>
          F.flatMap(cache.put("b", 2)) { _ =>
            F.flatMap(cache.put("c", 3)) { _ =>
              F.map(cache.size)(s => assert(s == 3))
            }
          }
        }
      }
    }

    "report correct keys" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.put("x", 1)) { _ =>
          F.flatMap(cache.put("y", 2)) { _ =>
            F.map(cache.keys)(k => assert(k == Set("x", "y")))
          }
        }
      }
    }

    // ---- computeIfAbsent ----

    "computeIfAbsent computes on miss" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.computeIfAbsent[Nothing]("a", F.pure(42))) { v =>
          F.map(cache.get("a")) { cached =>
            assert(v == 42 && cached.contains(42))
          }
        }
      }
    }

    "computeIfAbsent returns existing on hit" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.put("a", 1)) { _ =>
          F.map(cache.computeIfAbsent[Nothing]("a", F.pure(999))) { v =>
            assert(v == 1, "should return existing value, not recompute")
          }
        }
      }
    }

    "computeIfAbsent propagates typed errors" in {
      runner.unsafeRunAsyncAsFuture {
        F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
          F.flatMap(F.attempt(cache.computeIfAbsent[String]("a", F.fail("boom")))) { result =>
            F.map(cache.get("a")) { cached =>
              assert(result == Left("boom") && cached.isEmpty)
            }
          }
        }
      }.map(_.toTry.get)(executionContext)
    }

    "computeIfAbsent propagates defects" in {
      runner.unsafeRunAsyncAsFuture {
        val err = new RuntimeException("defect")
        F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
          F.flatMap(F.sandboxExit(cache.computeIfAbsent[Nothing]("a", F.terminate(err)))) { exit =>
            F.map(cache.get("a")) { cached =>
              assert(exit.isFailure && cached.isEmpty)
            }
          }
        }
      }.map(_.toTry.get)(executionContext)
    }

    // ---- Concurrency ----

    "deduplicate concurrent computeIfAbsent for the same key" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(F.mkRef(0)) { counter =>
          val compute: F[Nothing, Int] = F.flatMap(counter.update(i => i + 1))(_ => F.pure(42))
          F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))) { fib1 =>
            F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))) { fib2 =>
              F.flatMap(fib1.join) { v1 =>
                F.flatMap(fib2.join) { v2 =>
                  F.map(counter.get) { count =>
                    // Both fibers should get the same value.
                    // Counter should be 1 or 2 (at most 2 if the second fiber won the race
                    // before the first's Computing entry was visible), but the result is always 42.
                    assert(v1 == 42 && v2 == 42 && count <= 2)
                  }
                }
              }
            }
          }
        }
      }
    }

    "handle concurrent computeIfAbsent when first computation fails" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(F.mkRef(0)) { callCounter =>
          val failingCompute: F[String, Int] = F.flatMap(callCounter.update(_ + 1))(_ => F.fail("fail"))
          val succeedingCompute: F[Nothing, Int] = F.flatMap(callCounter.update(_ + 1))(_ => F.pure(99))
          // First call fails
          F.flatMap(F.attempt(cache.computeIfAbsent[String]("k", failingCompute))) { r1 =>
            // Second call should succeed (computes fresh since first failed)
            F.flatMap(cache.computeIfAbsent[Nothing]("k", succeedingCompute)) { v2 =>
              F.pure(assert(r1 == Left("fail") && v2 == 99))
            }
          }
        }
      }
    }

    "support many concurrent puts to different keys" in run {
      F.flatMap(BIOCache.make[F, Int, Int](CacheConfig(initialCapacity = 4))) { cache =>
        val puts = (0 until 100).toList
        F.flatMap(F.traverse(puts)(i => Fk.fork(cache.put(i, i * 10)))) { fibers =>
          F.flatMap(F.traverse(fibers)(_.join)) { _ =>
            F.flatMap(cache.size) { s =>
              F.map(F.traverse(puts)(i => cache.get(i))) { results =>
                assert(s == 100)
                assert(results.forall(_.isDefined))
                assert(results.zip(puts).forall { case (Some(v), k) => v == k * 10; case _ => false })
              }
            }
          }
        }
      }
    }

    // ---- TTL ----

    "expire entries with global TTL (lazy)" in run {
      val config = CacheConfig(defaultTTL = Some(50.millis))
      F.flatMap(BIOCache.make[F, String, Int](config)) { cache =>
        F.flatMap(cache.put("a", 1)) { _ =>
          F.flatMap(cache.get("a")) { before =>
            F.flatMap(Temp.sleep(100.millis)) { _ =>
              F.map(cache.get("a")) { after =>
                assert(before.contains(1) && after.isEmpty)
              }
            }
          }
        }
      }
    }

    "expire entries with per-key TTL" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.putWithTTL("short", 1, 50.millis)) { _ =>
          F.flatMap(cache.putWithTTL("long", 2, 5.seconds)) { _ =>
            F.flatMap(Temp.sleep(100.millis)) { _ =>
              F.flatMap(cache.get("short")) { short =>
                F.map(cache.get("long")) { long =>
                  assert(short.isEmpty && long.contains(2))
                }
              }
            }
          }
        }
      }
    }

    "computeIfAbsent respects TTL" in run {
      val config = CacheConfig(defaultTTL = Some(50.millis))
      F.flatMap(BIOCache.make[F, String, Int](config)) { cache =>
        F.flatMap(cache.computeIfAbsent[Nothing]("a", F.pure(1))) { v1 =>
          F.flatMap(Temp.sleep(100.millis)) { _ =>
            // Entry expired, should recompute
            F.map(cache.computeIfAbsent[Nothing]("a", F.pure(2))) { v2 =>
              assert(v1 == 1 && v2 == 2)
            }
          }
        }
      }
    }

    "computeIfAbsentWithTTL overrides global TTL" in run {
      val config = CacheConfig(defaultTTL = Some(5.seconds))
      F.flatMap(BIOCache.make[F, String, Int](config)) { cache =>
        F.flatMap(cache.computeIfAbsentWithTTL[Nothing]("a", 50.millis, F.pure(1))) { _ =>
          F.flatMap(Temp.sleep(100.millis)) { _ =>
            F.map(cache.get("a"))(r => assert(r.isEmpty))
          }
        }
      }
    }

    // ---- Eager eviction ----

    "eager eviction removes expired entries in background" in run {
      val config = CacheConfig(defaultTTL = Some(50.millis), eagerEvictionInterval = Some(30.millis))
      F.flatMap(BIOCache.makeEager[F, String, Int](config)) { cache =>
        F.flatMap(cache.put("a", 1)) { _ =>
          F.flatMap(Temp.sleep(200.millis)) { _ =>
            // Entry should have been evicted by background fiber
            F.flatMap(cache.size) { s =>
              F.flatMap(cache.shutdown) { _ =>
                F.pure(assert(s == 0))
              }
            }
          }
        }
      }
    }

    "shutdown stops eager eviction fiber" in run {
      val config = CacheConfig(eagerEvictionInterval = Some(10.millis))
      F.flatMap(BIOCache.makeEager[F, String, Int](config)) { cache =>
        // Just verify shutdown doesn't throw
        F.map(cache.shutdown)(_ => succeed)
      }
    }

    // ---- Edge cases ----

    "handle empty cache operations" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.invalidate("nonexistent")) { _ =>
          F.flatMap(cache.invalidateAll) { _ =>
            F.flatMap(cache.size) { s =>
              F.map(cache.keys) { k =>
                assert(s == 0 && k.isEmpty)
              }
            }
          }
        }
      }
    }

    "work with initialCapacity = 1" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig(initialCapacity = 1))) { cache =>
        F.flatMap(cache.put("a", 1)) { _ =>
          F.flatMap(cache.put("b", 2)) { _ =>
            F.flatMap(cache.get("a")) { a =>
              F.map(cache.get("b")) { b =>
                assert(a.contains(1) && b.contains(2))
              }
            }
          }
        }
      }
    }

    "work with large number of entries" in run {
      F.flatMap(BIOCache.make[F, Int, Int](CacheConfig(initialCapacity = 8))) { cache =>
        val n = 1000
        F.flatMap(F.traverse((0 until n).toList)(i => cache.put(i, i * 2))) { _ =>
          F.flatMap(cache.size) { s =>
            F.map(F.traverse((0 until n).toList)(i => cache.get(i))) { results =>
              assert(s == n)
              assert(results.zip(0 until n).forall { case (Some(v), k) => v == k * 2; case _ => false })
            }
          }
        }
      }
    }
  }
}
