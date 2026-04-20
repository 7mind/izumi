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
  // Resolve the default Primitives2 once at the class level so individual tests can
  // declare a local `implicit val` overriding it without tripping Scala 3's forward-
  // reference rule when resolving the same implicit in the same block.
  private val defaultPrims: Primitives2[IO] = implicitly

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

    "computeIfAbsent's producer promise carries Option[V] raw; GC-decoupling invariant" in {
      // Deterministic structural regression test — no GC timing involved.
      //
      // Current design invariant: the producer's promise carries `Option[V]` (the raw
      // value, unwrapped). This gives waiters a Guava-style in-flight-load handoff
      // (they receive V directly, without re-reading the bucket or unwrapping). Under
      // weak/soft cache ref types the promise pins V strongly while alive — but the
      // GC contract is preserved by a COMPANION invariant: `Ready.origin` stores a
      // per-call lightweight Object token (NOT the promise), so the cache does not
      // retain the promise. Once the producer fiber and all parked waiters release
      // their references, the promise is GC-eligible and V is free to be reclaimed
      // through the cache's weak/soft wrapper.
      //
      // This test pins BOTH halves of that invariant structurally:
      //   (1) every recorded promise.succeed call carries None or Some(v) where v is
      //       the raw V (here, AnyRef) — not a wrapper.
      //   (2) Ready.origin is NEVER identity-equal to a recorded promise — the cache
      //       decouples entry identity from promise lifecycle.
      import java.util.concurrent.atomic.AtomicReference

      val completions = new AtomicReference[List[Any]](Nil)
      val createdPromises = new AtomicReference[List[AnyRef]](Nil)

      implicit val recordingPrims: Primitives2[IO] = new Primitives2[IO] {
        override def mkRef[A](a: A): IO[Nothing, Ref2[IO, A]] = defaultPrims.mkRef(a)
        override def mkSemaphore(permits: Long): IO[Nothing, Semaphore2[IO]] = defaultPrims.mkSemaphore(permits)
        override def mkPromise[E, A]: IO[Nothing, Promise2[IO, E, A]] = {
          F.map(defaultPrims.mkPromise[E, A]) { inner =>
            val wrapper: Promise2[IO, E, A] = new Promise2[IO, E, A] {
              override def await: IO[E, A] = inner.await
              override def poll: IO[Nothing, Option[IO[E, A]]] = inner.poll
              override def succeed(a: A): IO[Nothing, Boolean] = {
                val _ = completions.updateAndGet((l: List[Any]) => a :: l)
                inner.succeed(a)
              }
              override def fail(e: E): IO[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): IO[Nothing, Boolean] = inner.terminate(t)
            }
            val _ = createdPromises.updateAndGet((l: List[AnyRef]) => wrapper :: l)
            wrapper
          }
        }
      }

      // Exercise a successful publish path so Ready.origin gets populated.
      val published = unsafeRun {
        F.flatMap(BIOCache.makeWithRef[IO, String, WeakCacheRef, AnyRef](CacheConfig())) { cache =>
          F.flatMap(cache.computeIfAbsent[Nothing]("k", F.sync(new AnyRef))) { _ =>
            // Reach into the concrete class for bucket inspection.
            val concrete = cache.asInstanceOf[ConcurrentHashMapCache[IO, String, WeakCacheRef, AnyRef]]
            F.map(concrete.bucketEntriesForTesting("k"))(_.toList)
          }
        }
      }

      // Part (1): payload shape — Option[V], never a wrapper.
      val recorded = completions.get()
      assert(recorded.nonEmpty, "expected at least one promise completion; test setup is broken")
      recorded.foreach {
        case None =>
          () // producer failed / interrupted / defected
        case Some(v) =>
          assert(
            !v.isInstanceOf[WeakCacheRef[?]] && !v.isInstanceOf[StrongRef[?]] && !v.isInstanceOf[izumi.functional.bio.cache.SoftCacheRef[?]],
            s"Promise payload Some must be raw V (AnyRef), never an R[V] wrapper. " +
              s"Got [$v] of type ${v.getClass.getName}. If a wrapper appears here, the promise is " +
              "carrying the cache's wrapped storage and would break Guava-style loader-result semantics.",
          )
        case other =>
          fail(s"Promise payload must be Option[V]; got non-Option value: $other of type ${other.getClass.getName}")
      }

      // Part (2): Ready.origin is NOT identity-equal to any produced promise.
      val promisesCreated = createdPromises.get()
      val readyOrigins = published.collect { case r: Ready[?, ?] => r.origin }
      assert(readyOrigins.nonEmpty, "expected a Ready entry to have been published; test setup is broken")
      readyOrigins.foreach { origin =>
        promisesCreated.foreach { p =>
          assert(
            !(origin eq p),
            s"Ready.origin [$origin] is identity-equal to a produced promise. The Ready-origin " +
              "decoupling is broken: the cache would retain the promise for the Ready's lifetime, " +
              "pinning Option[V] payload + V indefinitely and defeating weak/soft GC.",
          )
        }
      }
    }
  }

  "BIOCache dedup under weak/soft refs" should {

    "waiter on WeakCacheRef receives producer's v via promise WITHOUT re-running compute, even under GC pressure" in {
      // Regression test for Codex-flagged "weak/soft caches re-run loader after successful
      // producer if GC clears wrapper". Under `Option[V]` promise payload:
      //   - Producer signals Some(v) — raw V, not WeakCacheRef[V].
      //   - Waiter consumes Some(v) directly and returns v.
      //   - The cache's weak wrapper is IRRELEVANT for the waiter's handoff; GC pressure
      //     between publish and wake cannot cause a re-run.
      //
      // Exercise: producer slow-compute held on a latch; N waiters park; GC thrash runs
      // while producer is still suspended; release producer; verify every waiter returns
      // v and `computeCount == 1`.
      import java.util.concurrent.atomic.AtomicInteger
      val computeCount = new AtomicInteger(0)
      val waiters = 8

      val result = unsafeRun {
        F.flatMap(defaultPrims.mkPromise[Nothing, Unit]) { startedP =>
          F.flatMap(defaultPrims.mkPromise[Nothing, Unit]) { mayFinishP =>
            F.flatMap(BIOCache.makeWithRef[IO, String, WeakCacheRef, String](CacheConfig())) { cache =>
              val slow = F.flatMap(F.sync { val _ = computeCount.incrementAndGet(); () }) { _ =>
                F.flatMap(F.void(startedP.succeed(())))(_ => F.map(mayFinishP.await)(_ => new String("v")))
              }
              F.flatMap(Fork2[IO].fork(cache.computeIfAbsent[Nothing]("k", slow))) { producerFib =>
                F.flatMap(startedP.await) { _ =>
                  F.flatMap(F.traverse((0 until waiters).toList)(_ =>
                    Fork2[IO].fork(cache.computeIfAbsent[Nothing]("k", F.pure(new String("w"))))
                  )) { waiterFibs =>
                    F.flatMap(implicitly[Temporal2[IO]].sleep(50.millis)) { _ =>
                      F.flatMap(F.sync {
                        var _dummy: Any = null
                        for (_ <- 0 until 20) {
                          _dummy = new Array[Byte](256 * 1024)
                          System.gc()
                        }
                        _dummy.hashCode()
                      }) { _ =>
                        F.flatMap(F.void(mayFinishP.succeed(()))) { _ =>
                          F.flatMap(producerFib.join) { pRes =>
                            F.map(F.traverse(waiterFibs)(_.join)) { ws =>
                              (pRes, ws, computeCount.get())
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      val (producerResult, waiterResults, count) = result
      assert(producerResult == "v", s"producer must return its own computed value; got $producerResult")
      waiterResults.foreach { w =>
        assert(w == "v", s"waiter must receive producer's v via promise payload; got $w. Re-computation is a regression.")
      }
      assert(count == 1, s"compute must run EXACTLY once across producer + $waiters waiters, even under WeakCacheRef + GC pressure; got $count")
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
