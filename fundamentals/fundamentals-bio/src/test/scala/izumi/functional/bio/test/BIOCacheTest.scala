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

  /** Deterministically verify a fiber is blocked (not completed) within the given window.
    *
    * This replaces sleep-based coordination for waiter-race tests: if the fiber had taken
    * the trivial "arrived after the mutation" path, it would have completed within the
    * window. If it is truly parked on an `existingPromise.await`, the timeout elapses
    * with the fiber still running, and we get `None`.
    *
    * Safe with respect to the observed fiber: timing out our `observe` call stops our
    * observation, it does not propagate the interrupt to the observed fiber itself.
    */
  private def awaitBlocked[E, A](fiber: Fiber2[F, E, A], within: FiniteDuration): F[Nothing, Unit] = {
    F.flatMap(Temp.timeout(within)(fiber.observe)) {
      case None => F.unit
      case Some(exit) => F.sync(fail(s"fiber expected to be blocked as a waiter, but completed with: $exit"))
    }
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

    "put returns None for a fresh slot and Some(previous) on overwrite" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        for {
          first <- cache.put("a", 1)
          second <- cache.put("a", 2)
          third <- cache.put("a", 3)
          afterInvalidate <- cache.invalidate("a").flatMap(_ => cache.put("a", 9))
        } yield {
          assert(first.isEmpty, s"first put on empty slot returns None; got $first")
          assert(second.contains(1), s"put on existing Ready(1) returns Some(1); got $second")
          assert(third.contains(2), s"put on existing Ready(2) returns Some(2); got $third")
          assert(afterInvalidate.isEmpty, s"put after invalidate finds an empty slot; got $afterInvalidate")
        }
      }
    }

    "put returns None if the existing Ready is TTL-expired (treated as absent)" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        for {
          fresh <- cache.putWithTTL("a", 1, 1.nano)
          // Wait long enough for the 1-ns TTL to expire.
          _ <- Temp.sleep(20.millis)
          afterExpiry <- cache.put("a", 2)
          currentGet <- cache.get("a")
        } yield {
          assert(fresh.isEmpty, s"fresh slot returns None; got $fresh")
          assert(afterExpiry.isEmpty, s"put over an expired Ready returns None (expired entries aren't observable); got $afterExpiry")
          assert(currentGet.contains(2), s"cache reflects the new put; got $currentGet")
        }
      }
    }

    "put returns None post-close (silent no-op)" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        for {
          _ <- cache.put("a", 1)
          _ <- cache.close
          postClose <- cache.put("a", 2)
          cached <- cache.get("a")
        } yield {
          assert(postClose.isEmpty, s"post-close put returns None; got $postClose")
          // The atomic swap inside close clears the structure, so the pre-close "a" is gone too.
          assert(cached.isEmpty, s"post-close cache is empty; got $cached")
        }
      }
    }

    "toMap snapshots all live entries" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig(initialCapacity = 4))) { cache =>
        val pairs = (0 until 20).map(i => (s"k$i", i * 10))
        for {
          _ <- F.traverse(pairs.toList) { case (k, v) => cache.put(k, v) }
          snap <- cache.toMap
        } yield {
          assert(snap == pairs.toMap, s"toMap returns the full set of puts; got $snap vs expected ${pairs.toMap}")
        }
      }
    }

    "toMap excludes expired entries and entries overwritten by later puts" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        for {
          _ <- cache.put("alive", 1)
          _ <- cache.putWithTTL("expiring", 99, 1.nano)
          _ <- Temp.sleep(20.millis)
          _ <- cache.put("alive", 2) // overwrite
          snap <- cache.toMap
        } yield {
          assert(snap == Map("alive" -> 2), s"toMap reflects overwrites and drops expired; got $snap")
        }
      }
    }

    "toMap is empty after invalidateAll" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        for {
          _ <- F.traverse((0 until 8).toList)(i => cache.put(s"k$i", i))
          before <- cache.toMap
          _ <- cache.invalidateAll
          after <- cache.toMap
        } yield {
          assert(before.size == 8, s"before invalidateAll: got $before")
          assert(after.isEmpty, s"after invalidateAll: got $after")
        }
      }
    }

    "toMap skips keys with in-flight Computing (no cached value yet)" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          F.flatMap(Prims.mkLatch) { mayFinish =>
            val compute: F[Nothing, Int] =
              F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 42))
            for {
              _ <- cache.put("alreadyCached", 7)
              producerFib <- Fk.fork(cache.computeIfAbsent[Nothing]("inFlight", compute))
              _ <- started.await
              snapWhileInFlight <- cache.toMap
              _ <- mayFinish.succeed(())
              _ <- producerFib.join
              snapAfter <- cache.toMap
            } yield {
              assert(
                snapWhileInFlight == Map("alreadyCached" -> 7),
                s"in-flight Computing is NOT surfaced by toMap; got $snapWhileInFlight",
              )
              assert(
                snapAfter == Map("alreadyCached" -> 7, "inFlight" -> 42),
                s"once producer publishes, toMap includes the new pair; got $snapAfter",
              )
            }
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
              F.flatMap(cache.close) { _ =>
                F.pure(assert(s == 0))
              }
            }
          }
        }
      }
    }

    "close stops eager eviction fiber" in run {
      val config = CacheConfig(eagerEvictionInterval = Some(10.millis))
      F.flatMap(BIOCache.makeEager[F, String, Int](config)) { cache =>
        // Shutdown must complete within a reasonable window — proves the background fiber
        // was successfully interrupted rather than leaking. If `createWithEviction` failed
        // to register the fiber in `fiberRef`, close would silently no-op and this test
        // would still pass — that path is covered separately by the leak-regression test
        // "createWithEviction does not leak eviction fiber on interrupt".
        F.flatMap(Temp.timeout(5.seconds)(cache.close)) { firstShutdown =>
          // Idempotent: a second close must also complete (not hang on an absent fiber).
          F.flatMap(Temp.timeout(5.seconds)(cache.close)) { secondShutdown =>
            // Post-close full-close: put is a silent no-op, get returns None,
            // size/keys return 0/empty. The cache is terminal.
            F.flatMap(cache.put("a", 1)) { _ =>
              F.flatMap(cache.get("a")) { got =>
                F.flatMap(cache.size) { sz =>
                  F.map(cache.keys) { ks =>
                    assert(firstShutdown.isDefined, "first close must complete promptly (fiber gone)")
                    assert(secondShutdown.isDefined, "second close must be idempotent — no hang")
                    assert(got.isEmpty, s"post-close put is a no-op; get returns None. Got $got")
                    assert(sz == 0, s"post-close cache is empty; size = 0. Got $sz")
                    assert(ks.isEmpty, s"post-close cache has no keys. Got $ks")
                  }
                }
              }
            }
          }
        }
      }
    }

    "put releases waiter on a wedged producer WITHOUT needing close (release semantics)" in run {
      // Scenario: A is wedged in I/O. Waiter B is parked on A's promise. `put`
      // displaces A's Computing and signals A's promise `None` immediately —
      // releasing B. B retries `computeImpl`, hits put's Ready(42), returns 42.
      // No close, no caller timeout, no tracker.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { aStarted =>
          val hangForever: F[Nothing, Int] =
            F.flatMap(aStarted.succeed(())) { _ =>
              F.flatMap(Prims.mkPromise[Nothing, Int])(_.await)
            }
          F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", hangForever))) { producerFib =>
            F.flatMap(aStarted.await) { _ =>
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(99)))) { waiterFib =>
                F.flatMap(awaitBlocked(waiterFib, 200.millis)) { _ =>
                  F.flatMap(cache.put("k", 42)) { _ =>
                    F.flatMap(Temp.timeout(2.seconds)(waiterFib.join)) { waiterResult =>
                      F.map(producerFib.interrupt) { _ =>
                        assert(
                          waiterResult.contains(42),
                          s"put released waiter and waiter hit put's Ready; got $waiterResult",
                        )
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

    "close releases parked waiters on a wedged producer with IllegalStateException (Codex HIGH regressions)" in run {
      // Codex-flagged scenarios:
      //   (HIGH #2) Without a close-side release, a producer stuck in external I/O
      //   leaves parked waiters hung forever.
      //   (HIGH, subsequent round) If close released waiters by simply signaling
      //   `None`, they would retry via `computeImpl` and start fresh loaders AFTER
      //   teardown — duplicating side effects.
      //
      // The combined fix: close sets `closedRef`, sweeps every bucket (removes
      // `Computing` markers), and signals their promises with `None`. The waiter's
      // `awaitAndRetry` sees `None` → calls `checkOpen`, which observes `closedRef =
      // true` and fails fast with IllegalStateException via `F.terminate`. No retry,
      // no new compute, but the waiter is unblocked.
      //
      // We use `sandboxExit` to observe the defect without it escaping as a runtime
      // exception. A clean `Exit.Success` would mean the waiter either resumed with
      // a value (producer wasn't actually wedged) or retried-to-completion (leak);
      // we expect `Exit.FailureUninterrupted` carrying the IllegalStateException.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { producerStarted =>
          val hangForever: F[Nothing, Int] =
            F.flatMap(producerStarted.succeed(())) { _ =>
              F.flatMap(Prims.mkPromise[Nothing, Int])(_.await)
            }
          F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", hangForever))) { producerFib =>
            F.flatMap(producerStarted.await) { _ =>
              F.flatMap(Fk.fork(F.sandboxExit(cache.computeIfAbsent[Nothing]("k", F.pure(99))))) { waiterFib =>
                F.flatMap(awaitBlocked(waiterFib, 200.millis)) { _ =>
                  F.flatMap(cache.close) { _ =>
                    F.flatMap(Temp.timeout(3.seconds)(waiterFib.join)) { waiterExitOpt =>
                      F.map(producerFib.interrupt) { _ =>
                        assert(
                          waiterExitOpt.isDefined,
                          "close must release parked waiter — without the close-signal path, " +
                            "the waiter hangs forever on a wedged producer's promise.",
                        )
                        val exit = waiterExitOpt.get
                        val isIllegalState = exit match {
                          case Exit.Termination(t, _, _) => t.isInstanceOf[IllegalStateException]
                          case _ => false
                        }
                        assert(
                          isIllegalState,
                          s"released waiter must fail with IllegalStateException (cache closed) — NOT " +
                            s"retry and run a new loader after close. Got exit: $exit",
                        )
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

    "createWithEviction construction under interrupt: every successful build has a working close" in run {
      // Smoke test for the fork+set race fix in `createWithEviction`.
      //
      // Without `F.uninterruptible`, an interrupt between `FK.fork(evictionLoop)` and
      // `fiberRef.set(Some(fiber))` would cause the outer construction to fail while the
      // daemon-forked eviction fiber keeps running with no way to stop it (BIO's `fork`
      // delegates to ZIO's `forkDaemon`, which is not auto-killed on parent interrupt).
      //
      // Limitation: a genuinely leaked fiber is not externally observable without
      // instrumentation — the cache reference is lost, and close cannot be called on
      // it. This test therefore does NOT directly observe the leak; it validates the
      // complementary invariant: for every outcome where the construction succeeded, the
      // fiber IS registered and close completes promptly. This catches a weaker
      // failure mode (fiberRef left unset despite construction succeeding) and exercises
      // the construction path under racing interrupts.
      val config = CacheConfig(eagerEvictionInterval = Some(5.millis))
      F.flatMap(F.traverse((0 until 20).toList) { _ =>
        for {
          fib <- Fk.fork(BIOCache.makeEager[F, String, Int](config))
          _ <- fib.interrupt
          exit <- fib.observe
        } yield exit
      }) { exits =>
        F.flatMap(F.traverse(exits) {
          case Exit.Success(cache) =>
            F.map(Temp.timeout(5.seconds)(cache.close))(r => Some(r.isDefined))
          case _ =>
            F.pure(None)
        }) { shutdownResults =>
          val constructed = shutdownResults.flatten
          F.pure {
            assert(
              constructed.forall(identity),
              s"every successfully-constructed cache must shut down promptly; got $constructed",
            )
          }
        }
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

    // ---- Linearizability / race regression tests ----

    "concurrent put + computeIfAbsent: producer returns own computed value (Guava loader-result)" in run {
      // Guava semantics: computeIfAbsent's caller receives the value `compute`
      // produced — NOT a racing put's value. Swapping in put's value would orphan
      // the producer's computed result (critical for side-effecting loaders that
      // allocate resources or external records tied to the returned value).
      //
      // The cache, however, reflects put's value for NEW callers — caller and
      // cache diverge, and that is by design.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { computeStarted =>
          F.flatMap(Prims.mkLatch) { putDone =>
            val slowCompute: F[Nothing, Int] = F.flatMap(computeStarted.succeed(()))(_ =>
              F.map(putDone.await)(_ => 1)
            )
            F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", slowCompute))) { computeFiber =>
              F.flatMap(computeStarted.await) { _ =>
                F.flatMap(cache.put("k", 999)) { _ =>
                  F.flatMap(putDone.succeed(())) { _ =>
                    F.flatMap(computeFiber.join) { computeResult =>
                      F.map(cache.get("k")) { cached =>
                        assert(
                          computeResult == 1,
                          s"computeIfAbsent must return the value its `compute` produced " +
                            s"(1), not the racing put's value. Got $computeResult. Returning " +
                            "put's value would orphan the producer's result for side-effecting " +
                            "loaders.",
                        )
                        assert(cached.contains(999), s"cache reflects put's value for NEW callers; got $cached")
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

    "invalidate does NOT preempt in-flight producer; compute runs exactly once; cache stays empty (strict freshness)" in run {
      // Strict freshness barrier: `invalidate` removes the in-flight producer's
      // `Computing`. When the producer finishes, its publish-modify sees no own
      // `Computing` in the slot and REJECTS publication. The cache is left empty;
      // the next caller recomputes. The producer's DIRECT caller still receives
      // its own `v` (loader-result contract); only the cache's public state
      // reflects the invalidation boundary.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { computeStarted =>
          F.flatMap(Prims.mkLatch) { invalidateDone =>
            F.flatMap(Prims.mkRef(0)) { runCount =>
              val compute: F[Nothing, Int] = F.flatMap(runCount.update(_ + 1)) { _ =>
                F.flatMap(computeStarted.succeed(()))(_ => F.map(invalidateDone.await)(_ => 42))
              }
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))) { fiberA =>
                F.flatMap(computeStarted.await) { _ =>
                  F.flatMap(cache.invalidate("k")) { _ =>
                    F.flatMap(invalidateDone.succeed(())) { _ =>
                      F.flatMap(Temp.timeout(5.seconds)(fiberA.join)) { aResult =>
                        F.flatMap(cache.get("k")) { cached =>
                          F.map(runCount.get) { count =>
                            assert(aResult.contains(42), s"producer returns its own computed value 42 (loader-result); got $aResult")
                            assert(cached.isEmpty, s"cache stays empty — invalidate fenced our publish (strict freshness); got $cached")
                            assert(count == 1, s"compute runs exactly once (at-most-once); got $count")
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

    "invalidateAll does NOT preempt in-flight producer; compute runs exactly once; cache stays empty (strict freshness)" in run {
      // Same strict-freshness contract as `invalidate`. invalidateAll wipes bucket
      // state for all keys but does NOT signal in-flight Computing promises. The
      // producer runs once, returns its computed value to its direct caller, and
      // its publish is REJECTED because its own Computing was removed.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { computeStarted =>
          F.flatMap(Prims.mkLatch) { clearDone =>
            F.flatMap(Prims.mkRef(0)) { runCount =>
              val compute: F[Nothing, Int] = F.flatMap(runCount.update(_ + 1)) { _ =>
                F.flatMap(computeStarted.succeed(()))(_ => F.map(clearDone.await)(_ => 42))
              }
              F.flatMap(cache.put("other", 1)) { _ =>
                F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))) { fiberA =>
                  F.flatMap(computeStarted.await) { _ =>
                    F.flatMap(cache.invalidateAll) { _ =>
                      F.flatMap(clearDone.succeed(())) { _ =>
                        F.flatMap(Temp.timeout(5.seconds)(fiberA.join)) { aResult =>
                          F.flatMap(cache.get("k")) { k =>
                            F.flatMap(cache.get("other")) { other =>
                              F.map(runCount.get) { count =>
                                assert(aResult.contains(42), s"producer returns 42 (loader-result); got $aResult")
                                assert(k.isEmpty, s"cache stays empty — invalidateAll fenced our publish; got $k")
                                assert(other.isEmpty, s"unrelated keys remain cleared, got $other")
                                assert(count == 1, s"compute runs exactly once (at-most-once); got $count")
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
      }
    }

    "producer's return value is own computed v (Guava loader-result), unaffected by racing mutations" in run {
      // Under Guava semantics the producer ALWAYS returns the value its `compute`
      // produced — never substituted by a racing put. This test exercises the
      // strictest timing: a racing `put(999)` + a racing `invalidate` firing AFTER
      // the producer signals its promise. No matter what, the producer's caller
      // must receive `1` (the value `compute` produced).
      //
      // Instrumentation: the first cache-created promise (the producer's pA) has
      // its `succeed` wrapped to fire `invalidate` immediately after signaling.
      // This simulates a worst-case racing control-op storm right at the moment of
      // publication; the producer's return must not depend on post-signal bucket
      // state.
      import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
      val promiseIndex = new AtomicInteger(0)
      val cacheRef = new AtomicReference[BIOCache[F, String, Int]](null)
      val defaultPrimsForCache: Primitives2[F] = Prims
      val instrumentedPrims: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          val idx = promiseIndex.getAndIncrement()
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = inner.await
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
              override def succeed(a: A): F[Nothing, Boolean] = {
                if (idx == 0) {
                  F.flatMap(inner.succeed(a)) { result =>
                    val c = cacheRef.get()
                    if (c ne null) F.map(c.invalidate("k"))(_ => result)
                    else F.pure(result)
                  }
                } else inner.succeed(a)
              }
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumentedPrims,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(F.sync(cacheRef.set(cache))) { _ =>
          F.flatMap(Prims.mkLatch) { started =>
            F.flatMap(Prims.mkLatch) { mayFinish =>
              val producerCompute: F[Nothing, Int] =
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 1))
              for {
                producerFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", producerCompute))
                _ <- started.await
                _ <- cache.put("k", 999)
                _ <- mayFinish.succeed(())
                producerResult <- producerFib.join
              } yield {
                assert(
                  producerResult == 1,
                  s"producer must return its own computed value (1), regardless of a racing put(999) " +
                    s"and a post-signal invalidate. Got $producerResult. If 999, the producer's " +
                    "return was hijacked to the displacer's value — violates Guava loader-result semantics.",
                )
              }
            }
          }
        }
      }
    }

    "put AFTER producer signaled: waiter still returns producer's value (Guava loader-result dedup)" in run {
      // Under Guava loader-result dedup, a waiter that already received
      // `Some(v_A)` from its producer's promise returns `v_A` even if a later
      // `put(k, v_B)` linearized between the producer's signal and the waiter's
      // wake. The cache itself reflects `v_B` for subsequent callers; the
      // waiter's decision is tied to the producer it was parked on.
      //
      // The earlier "strict freshness" variant of this test asserted the
      // opposite (waiter retries through `computeImpl` and hits put's Ready).
      // We dropped that invariant together with the per-key `keyEpochs` map —
      // it was carrying a whole per-bucket `Map[K, Long]` to enforce a racing
      // property with no caller-observable benefit (the producer's call
      // already returned `v_A` to its own caller).
      //
      // Deterministic via an instrumented Primitives2 that fires `put(999)` as a
      // side-effect of the producer's `succeed(...)` call, guaranteeing that put
      // runs AFTER the promise is resolved but before the waiter resumes.
      import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
      val promiseIndex = new AtomicInteger(0)
      val cacheRef = new AtomicReference[BIOCache[F, String, Int]](null)
      val defaultPrimsForCache: Primitives2[F] = Prims
      val instrumented: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          val idx = promiseIndex.getAndIncrement()
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = inner.await
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
              override def succeed(a: A): F[Nothing, Boolean] = {
                // First cache-created promise is the producer's pA. After its succeed,
                // fire a racing put to overwrite the just-published Ready.
                if (idx == 0) {
                  F.flatMap(inner.succeed(a)) { result =>
                    val c = cacheRef.get()
                    if (c ne null) F.map(c.put("k", 999))(_ => result)
                    else F.pure(result)
                  }
                } else inner.succeed(a)
              }
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumented,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(F.sync(cacheRef.set(cache))) { _ =>
          F.flatMap(Prims.mkLatch) { started =>
            F.flatMap(Prims.mkLatch) { mayFinish =>
              val compute: F[Nothing, Int] =
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 1))
              for {
                producerFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))
                _ <- started.await
                // Fork waiter BEFORE releasing producer; waiter parks on pA.
                waiterFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-999)))
                _ <- awaitBlocked(waiterFib, 300.millis)
                // Let producer finish. The instrumented `succeed` will fire put(999)
                // AFTER signaling the waiter's promise. Waiter then resumes and reads
                // the promise payload (producer's value, 1) — NOT the put's value (999).
                _ <- mayFinish.succeed(())
                producerResult <- producerFib.join
                waiterResult <- Temp.timeout(5.seconds)(waiterFib.join)
                cached <- cache.get("k")
              } yield {
                assert(producerResult == 1, s"producer returns its own computed value 1 (loader-result); got $producerResult")
                assert(
                  waiterResult.contains(1),
                  s"waiter receives producer's v=1 via promise payload (Guava loader-result dedup); got $waiterResult. " +
                    "A value of 999 would mean strict-freshness-for-waiters was still being enforced — that's the invariant we deliberately removed when we dropped keyEpochs.",
                )
                // Cache reflects put's value for NEW callers, even though the waiter saw the producer's value.
                assert(cached.contains(999), s"cache reflects put(999) for post-put callers; got $cached")
              }
            }
          }
        }
      }
    }

    "expired Ready + later put: waiter still returns producer's value (Guava loader-result dedup)" in run {
      // Guava loader-result dedup: an already-parked waiter that receives
      // `Some(v_A)` from its producer's promise returns `v_A`, even if a
      // `put(k, v_B)` linearized between the producer's signal and the waiter's
      // wake. The expired-Ready setup used to exercise the per-key epoch
      // barrier; we deleted that barrier (it carried a `Map[K, Long]` per
      // bucket with no caller-observable benefit), so the test now pins the
      // dedup invariant instead: producer's call returns its own `v`, and so
      // does the waiter.
      //
      // Scenario:
      //   1. Producer A publishes Ready(TA) with 1ns TTL (immediately expired).
      //   2. Waiter B parks on pA.
      //   3. put(k, 999, 1.hour) runs AFTER A's Ready has TTL-expired.
      //   4. A signals pA.succeed(Some(1)). B wakes, returns 1.
      import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
      val promiseIndex = new AtomicInteger(0)
      val cacheRef = new AtomicReference[BIOCache[F, String, Int]](null)
      val defaultPrimsForCache: Primitives2[F] = Prims
      val instrumented: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          val idx = promiseIndex.getAndIncrement()
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = inner.await
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
              override def succeed(a: A): F[Nothing, Boolean] = {
                if (idx == 0) {
                  val c = cacheRef.get()
                  if (c ne null) {
                    F.flatMap(Temp.sleep(20.millis)) { _ =>
                      F.flatMap(c.putWithTTL("k", 999, 1.hour)) { _ =>
                        inner.succeed(a)
                      }
                    }
                  } else inner.succeed(a)
                } else inner.succeed(a)
              }
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumented,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(F.sync(cacheRef.set(cache))) { _ =>
          F.flatMap(Prims.mkLatch) { started =>
            F.flatMap(Prims.mkLatch) { mayFinish =>
              val compute: F[Nothing, Int] =
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 1))
              for {
                producerFib <- Fk.fork(cache.computeIfAbsentWithTTL[Nothing]("k", 1.nano, compute))
                _ <- started.await
                waiterFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-11)))
                _ <- awaitBlocked(waiterFib, 300.millis)
                _ <- mayFinish.succeed(())
                producerResult <- producerFib.join
                waiterResult <- Temp.timeout(5.seconds)(waiterFib.join)
              } yield {
                assert(producerResult == 1, s"producer returns own v (loader-result); got $producerResult")
                assert(
                  waiterResult.contains(1),
                  s"waiter receives producer's v=1 via promise payload (Guava loader-result dedup); got $waiterResult. " +
                    "A value of 999 would mean the per-key epoch barrier came back — we intentionally removed it.",
                )
              }
            }
          }
        }
      }
    }

    "publish → invalidate → successor publish → invalidate → old waiter wake: waiter returns producer's v (Guava loader-result dedup)" in run {
      // Under Guava loader-result dedup, an already-parked waiter whose
      // producer signaled `Some(v_A)` returns `v_A` regardless of any
      // interleaved `invalidate` / successor-publish / re-invalidate
      // sequence. We dropped the per-origin tombstone machinery together
      // with the `keyEpochs` map; this test now pins that dedup invariant.
      //
      // Sequence (driven by the instrumented succeed below):
      //   1. Producer A publishes Ready(origin=TA). B parks on pA with TA.
      //   2. invalidate(k) displaces no Computing here (A already published).
      //   3. Successor C publishes Ready(TC).
      //   4. Another invalidate(k) removes Ready(TC).
      //   5. A signals pA.succeed(Some(1)). B wakes, returns 1.
      import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
      val promiseIndex = new AtomicInteger(0)
      val cacheRef = new AtomicReference[BIOCache[F, String, Int]](null)
      val defaultPrimsForCache: Primitives2[F] = Prims
      val instrumented: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          val idx = promiseIndex.getAndIncrement()
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = inner.await
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
              override def succeed(a: A): F[Nothing, Boolean] = {
                if (idx == 0) {
                  val c = cacheRef.get()
                  if (c ne null) {
                    F.flatMap(c.invalidate("k")) { _ =>
                      F.flatMap(c.computeIfAbsent[Nothing]("k", F.pure(999))) { _ =>
                        F.flatMap(c.invalidate("k")) { _ =>
                          inner.succeed(a)
                        }
                      }
                    }
                  } else inner.succeed(a)
                } else inner.succeed(a)
              }
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumented,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(F.sync(cacheRef.set(cache))) { _ =>
          F.flatMap(Prims.mkLatch) { started =>
            F.flatMap(Prims.mkLatch) { mayFinish =>
              val compute: F[Nothing, Int] =
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 1))
              for {
                producerFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))
                _ <- started.await
                waiterFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-55)))
                _ <- awaitBlocked(waiterFib, 300.millis)
                _ <- mayFinish.succeed(())
                producerResult <- producerFib.join
                waiterResult <- Temp.timeout(5.seconds)(waiterFib.join)
              } yield {
                assert(producerResult == 1, s"producer returns own v (loader-result); got $producerResult")
                assert(
                  waiterResult.contains(1),
                  s"waiter receives producer's v=1 via promise payload (Guava loader-result dedup); got $waiterResult. " +
                    "Anything else would mean strict freshness for waiters was still being enforced — we intentionally removed it.",
                )
              }
            }
          }
        }
      }
    }

    "publish → invalidate → successor publish → old waiter wake: waiter returns producer's v (Guava loader-result dedup)" in run {
      // Guava loader-result dedup: a waiter whose producer signaled
      // `Some(v_A)` returns `v_A` regardless of whether `invalidate(k)` or
      // a successor's `compute` / publish linearized in between. We removed
      // the per-origin tombstone machinery; the producer's promise payload
      // is authoritative for already-deduped waiters.
      //
      // Sequence (driven by the instrumented succeed below):
      //   1. Producer A publishes its Ready. Waiter B parks on pA.
      //   2. invalidate(k) runs.
      //   3. Successor C publishes Ready(999).
      //   4. A signals pA.succeed(Some(1)). B wakes, returns 1.
      import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
      val promiseIndex = new AtomicInteger(0)
      val cacheRef = new AtomicReference[BIOCache[F, String, Int]](null)
      val defaultPrimsForCache: Primitives2[F] = Prims
      val instrumented: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          val idx = promiseIndex.getAndIncrement()
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = inner.await
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
              override def succeed(a: A): F[Nothing, Boolean] = {
                if (idx == 0) {
                  val c = cacheRef.get()
                  if (c ne null) {
                    F.flatMap(c.invalidate("k")) { _ =>
                      F.flatMap(c.computeIfAbsent[Nothing]("k", F.pure(999))) { _ =>
                        inner.succeed(a)
                      }
                    }
                  } else inner.succeed(a)
                } else inner.succeed(a)
              }
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumented,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(F.sync(cacheRef.set(cache))) { _ =>
          F.flatMap(Prims.mkLatch) { started =>
            F.flatMap(Prims.mkLatch) { mayFinish =>
              val compute: F[Nothing, Int] =
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 1))
              for {
                producerFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))
                _ <- started.await
                waiterFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-77)))
                _ <- awaitBlocked(waiterFib, 300.millis)
                _ <- mayFinish.succeed(())
                producerResult <- producerFib.join
                waiterResult <- Temp.timeout(5.seconds)(waiterFib.join)
              } yield {
                assert(producerResult == 1, s"producer returns own v (loader-result); got $producerResult")
                assert(
                  waiterResult.contains(1),
                  s"waiter receives producer's v=1 via promise payload (Guava loader-result dedup); got $waiterResult. " +
                    "A value of 999 would mean the waiter retried past an invalidate barrier — that invariant was intentionally dropped with keyEpochs.",
                )
              }
            }
          }
        }
      }
    }

    "publish → invalidate → invalidate: waiter returns producer's v (Guava loader-result dedup)" in run {
      // Guava loader-result dedup: a waiter whose producer signaled
      // `Some(v_A)` returns `v_A` regardless of any sequence of
      // `invalidate` calls that linearize between the signal and the wake.
      // We dropped the tombstone / per-origin barrier machinery; the
      // producer's promise payload is authoritative.
      //
      // Sequence:
      //   1. Producer A publishes Ready. B parks on pA.
      //   2. invalidate(k); invalidate(k) again.
      //   3. A signals pA.succeed(Some(1)). B wakes, returns 1.
      import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
      val promiseIndex = new AtomicInteger(0)
      val cacheRef = new AtomicReference[BIOCache[F, String, Int]](null)
      val defaultPrimsForCache: Primitives2[F] = Prims
      val instrumented: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          val idx = promiseIndex.getAndIncrement()
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = inner.await
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
              override def succeed(a: A): F[Nothing, Boolean] = {
                if (idx == 0) {
                  val c = cacheRef.get()
                  if (c ne null) {
                    F.flatMap(c.invalidate("k")) { _ =>
                      F.flatMap(c.invalidate("k")) { _ =>
                        inner.succeed(a)
                      }
                    }
                  } else inner.succeed(a)
                } else inner.succeed(a)
              }
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumented,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(F.sync(cacheRef.set(cache))) { _ =>
          F.flatMap(Prims.mkLatch) { started =>
            F.flatMap(Prims.mkLatch) { mayFinish =>
              val compute: F[Nothing, Int] =
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 1))
              for {
                producerFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))
                _ <- started.await
                waiterFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-77)))
                _ <- awaitBlocked(waiterFib, 300.millis)
                _ <- mayFinish.succeed(())
                producerResult <- producerFib.join
                waiterResult <- Temp.timeout(5.seconds)(waiterFib.join)
              } yield {
                assert(producerResult == 1, s"producer returns own v (loader-result); got $producerResult")
                assert(
                  waiterResult.contains(1),
                  s"waiter receives producer's v=1 via promise payload (Guava loader-result dedup); got $waiterResult. " +
                    "Any other value would mean a tombstone-style barrier survived a follow-up invalidate — that invariant was intentionally dropped.",
                )
              }
            }
          }
        }
      }
    }

    "dedup preserved: already-deduped waiter returns producer's v even after benign TTL/GC + successor [Codex HIGH regression]" in run {
      // Regression for Codex HIGH: "Already-deduped waiters can be hijacked by a
      // later TTL/GC miss". Scenario (no explicit barrier fires):
      //   1. Producer A publishes Ready(origin=TA) with 1ns TTL so A's Ready is
      //      immediately expired.
      //   2. Waiter B is parked on pA (not yet woken).
      //   3. Successor caller C calls computeIfAbsent. `cleanBucket` in C's
      //      modify removes A's expired Ready (benign TTL cleanup — NO
      //      tombstone, no put/invalidate ran). C installs Computing(TC), runs
      //      its own compute, publishes Ready(TC, origin=TC).
      //   4. Producer A signals pA.succeed(Some(1)). B wakes.
      //   5. WITHOUT fix: B sees foreign Ready(TC), retries, gets C's value
      //      (888). That duplicates loader execution for an already-deduped
      //      caller — violates Guava loader-result contract.
      //   6. WITH fix (tombstone-only retry): no tombstone → B accepts A's v=1.
      //
      // Deterministic via an instrumented Primitives2 that sequences the
      // successor's computeIfAbsent BEFORE the producer's `succeed`.
      import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
      val promiseIndex = new AtomicInteger(0)
      val cacheRef = new AtomicReference[BIOCache[F, String, Int]](null)
      val defaultPrimsForCache: Primitives2[F] = Prims
      val instrumented: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          val idx = promiseIndex.getAndIncrement()
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = inner.await
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
              override def succeed(a: A): F[Nothing, Boolean] = {
                if (idx == 0) {
                  val c = cacheRef.get()
                  if (c ne null) {
                    F.flatMap(Temp.sleep(20.millis)) { _ =>
                      F.flatMap(c.computeIfAbsent[Nothing]("k", F.pure(888))) { _ =>
                        inner.succeed(a)
                      }
                    }
                  } else inner.succeed(a)
                } else inner.succeed(a)
              }
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumented,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(F.sync(cacheRef.set(cache))) { _ =>
          F.flatMap(Prims.mkLatch) { started =>
            F.flatMap(Prims.mkLatch) { mayFinish =>
              val compute: F[Nothing, Int] =
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 1))
              val waiterSentinel = -42
              for {
                producerFib <- Fk.fork(cache.computeIfAbsentWithTTL[Nothing]("k", 1.nano, compute))
                _ <- started.await
                waiterFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(waiterSentinel)))
                _ <- awaitBlocked(waiterFib, 300.millis)
                _ <- mayFinish.succeed(())
                producerResult <- producerFib.join
                waiterResult <- Temp.timeout(5.seconds)(waiterFib.join)
              } yield {
                assert(producerResult == 1, s"producer returns own v (loader-result); got $producerResult")
                assert(
                  waiterResult.contains(1),
                  s"Codex HIGH regression: already-deduped waiter hijacked by successor C's " +
                    s"compute. Expected Some(1) (A's dedup result); got $waiterResult. " +
                    "If 888 → retry ran on foreign Ready/Computing with no tombstone (bug); " +
                    s"if $waiterSentinel → retry re-entered computeImpl on empty slot.",
                )
              }
            }
          }
        }
      }
    }

    "put with expired TTL + cleanBucket sweeps put's Ready: waiter still returns producer's v (Guava loader-result dedup)" in run {
      // Under Guava loader-result dedup, a waiter whose producer signaled
      // `Some(v_A)` returns `v_A` even if a `put(k, _, 1.nano)` and a
      // follow-up `cleanBucket`-triggering `get` linearize between the
      // signal and the wake. The per-origin tombstone machinery that used
      // to enforce retry here was removed together with the `keyEpochs`
      // map; the producer's promise payload is authoritative.
      //
      // Sequence (driven by the instrumented succeed below):
      //   1. Producer A publishes Ready(originToken=TA), waiter B parked.
      //   2. put(k, 999, 1.nano); sleep; get(k) (sweeps the put's Ready).
      //   3. A signals pA.succeed(Some(1)). B wakes, returns 1.
      import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
      val promiseIndex = new AtomicInteger(0)
      val cacheRef = new AtomicReference[BIOCache[F, String, Int]](null)
      val defaultPrimsForCache: Primitives2[F] = Prims
      val instrumented: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          val idx = promiseIndex.getAndIncrement()
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = inner.await
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
              override def succeed(a: A): F[Nothing, Boolean] = {
                if (idx == 0) {
                  val c = cacheRef.get()
                  if (c ne null) {
                    F.flatMap(c.putWithTTL("k", 999, 1.nano)) { _ =>
                      F.flatMap(Temp.sleep(20.millis)) { _ =>
                        F.flatMap(c.get("k")) { _ =>
                          inner.succeed(a)
                        }
                      }
                    }
                  } else inner.succeed(a)
                } else inner.succeed(a)
              }
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumented,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(F.sync(cacheRef.set(cache))) { _ =>
          F.flatMap(Prims.mkLatch) { started =>
            F.flatMap(Prims.mkLatch) { mayFinish =>
              val compute: F[Nothing, Int] =
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 1))
              for {
                producerFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))
                _ <- started.await
                waiterFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-42)))
                _ <- awaitBlocked(waiterFib, 300.millis)
                _ <- mayFinish.succeed(())
                producerResult <- producerFib.join
                waiterResult <- Temp.timeout(5.seconds)(waiterFib.join)
              } yield {
                assert(producerResult == 1, s"producer returns own v (loader-result); got $producerResult")
                assert(
                  waiterResult.contains(1),
                  s"waiter receives producer's v=1 via promise payload (Guava loader-result dedup); got $waiterResult. " +
                    "Any other value would mean a tombstone-style post-wake retry fired — that invariant was intentionally dropped with keyEpochs.",
                )
              }
            }
          }
        }
      }
    }

    "producer cleanup after racing put (Ready.origin = null) does not NPE" in run {
      // Codex-flagged scenario: producer A is interrupted AFTER a concurrent `put`
      // has replaced A's Computing(pA) with Ready(origin=null). `doCompute`'s
      // `guaranteeOnFailure` cleanup runs `filterNot(isOurEntry)`, which for
      // Ready entries evaluates `origin eq originToken`. If `null eq originToken`
      // NPE'd at the JVM level, the producer would defect without signaling its
      // promise — stranding the waiter forever.
      //
      // Scala's `eq` compiles to a JVM reference-equality instruction
      // (`if_acmpeq`), not a method call, so the null-receiver never dispatches
      // through a virtual call. This test empirically verifies there is no NPE
      // AND that the waiter exercises the `ActionWait → pA.await` path before
      // the cleanup runs — if the waiter took a different path (e.g., ActionHit
      // after put), the cleanup's null-origin branch would not be exercised and
      // the test would pass vacuously.
      //
      // Deterministic park proof: an instrumented `Primitives2` counts every
      // `.await` call on cache-created promises. Under this scenario the ONLY
      // cache-promise await is the waiter's `pA.await` — producers never await
      // their own promises, and the test's latches use the uninstrumented
      // default `Prims`. We block progression until the count reaches 1, proving
      // the waiter is parked on pA BEFORE `put` and BEFORE producer interrupt.
      import java.util.concurrent.atomic.AtomicInteger
      val cacheAwaitCount = new AtomicInteger(0)
      val defaultPrimsForCache: Primitives2[F] = Prims
      val instrumentedPrims: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = {
                F.flatMap(F.sync { val _ = cacheAwaitCount.incrementAndGet() })(_ => inner.await)
              }
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def succeed(a: A): F[Nothing, Boolean] = inner.succeed(a)
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumentedPrims,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        // Latches use the default Prims so their awaits do NOT contribute to
        // cacheAwaitCount — only the waiter's `pA.await` moves that counter.
        F.flatMap(Prims.mkLatch) { started =>
          val hangForever: F[Nothing, Int] = F.flatMap(Prims.mkPromise[Nothing, Int])(_.await)
          val hangingCompute: F[Nothing, Int] = F.flatMap(started.succeed(()))(_ => hangForever)
          // Poll until the waiter has entered pA.await (counter >= 1). Bounded to
          // 100 × 10ms = 1s — if the waiter doesn't park within that window, the
          // scenario we want to exercise isn't happening and the test fails loudly.
          def waitForWaiterParked(remainingPolls: Int): F[Nothing, Unit] = {
            if (cacheAwaitCount.get() >= 1) F.unit
            else if (remainingPolls <= 0)
              F.sync(fail(s"waiter never entered pA.await; cacheAwaitCount=${cacheAwaitCount.get()}"))
            else F.flatMap(Temp.sleep(10.millis))(_ => waitForWaiterParked(remainingPolls - 1))
          }
          F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", hangingCompute))) { producerFib =>
            F.flatMap(started.await) { _ =>
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-1)))) { waiterFib =>
                // DETERMINISTIC: block until waiter has actually called pA.await.
                F.flatMap(waitForWaiterParked(100)) { _ =>
                  // put installs Ready(999, origin=null) and removes Computing(pA).
                  // The waiter is already parked on pA — it will not see the
                  // Ready(999) via ActionHit.
                  F.flatMap(cache.put("k", 999)) { _ =>
                    // Interrupt producer — cleanup iterates and evaluates
                    // `null eq promiseRef` against put's Ready(999, origin=null).
                    F.flatMap(producerFib.interrupt) { _ =>
                      F.map(Temp.timeout(5.seconds)(waiterFib.join)) { waiterResult =>
                        assert(
                          waiterResult.isDefined,
                          "Waiter must unblock after producer interrupt. If it hangs, " +
                            "the cleanup NPE'd on `null eq promiseRef` and never signaled " +
                            "the promise — Codex's P1 regression.",
                        )
                        assert(
                          waiterResult.contains(999),
                          s"Waiter's retry should find put's Ready(999); got $waiterResult",
                        )
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

    "repeated invalidate against in-flight loader does not amplify compute count" in run {
      // Codex-recommended regression test: pin the bound on loader executions under a
      // storm of invalidations. Under the pre-Guava design, each invalidate signaled the
      // Computing promise, waking any waiters who would then race in a retry — spawning
      // a fresh compute per invalidate cycle. Under Guava semantics, invalidate only
      // mutates the bucket; it doesn't wake anything, so repeated invalidates during a
      // single in-flight load result in ZERO additional compute runs.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          F.flatMap(Prims.mkLatch) { mayFinish =>
            F.flatMap(Prims.mkRef(0)) { runCount =>
              val compute: F[Nothing, Int] = F.flatMap(runCount.update(_ + 1)) { _ =>
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 42))
              }
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))) { producerFib =>
                F.flatMap(started.await) { _ =>
                  // Invalidate many times while the single producer is in flight. None of
                  // these wake anything or cause an additional compute to start.
                  F.flatMap(F.traverse_((0 until 20).toList)(_ => cache.invalidate("k"))) { _ =>
                    F.flatMap(mayFinish.succeed(())) { _ =>
                      F.flatMap(Temp.timeout(5.seconds)(producerFib.join)) { result =>
                        F.flatMap(cache.get("k")) { cached =>
                          F.map(runCount.get) { count =>
                            assert(result.contains(42), s"producer returns its computed value 42 (loader-result); got $result")
                            assert(cached.isEmpty, s"cache stays empty — strict freshness barrier after repeated invalidates; got $cached")
                            assert(
                              count == 1,
                              s"compute runs exactly ONCE despite 20 invalidates — at-most-once contract. " +
                                s"Got $count. If > 1, control ops are preempting loaders — regression.",
                            )
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

    "A → invalidate → B → A succeeds → B succeeds: newer compute wins, cache ends with B's value (freshness boundary)" in run {
      // Codex-flagged freshness scenario. Without the put-vs-compute distinction in
      // Ready.origin, A's post-invalidate publish would install Ready(A.v) and B's
      // later publish would see a "foreign Ready" and skip — leaving stale A.v in the
      // cache despite B being the explicit post-invalidate refresh. Under the current
      // rule (only put-Ready, origin=null, is preserved), B's successful compute
      // correctly overwrites A's Ready.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { aStarted =>
          F.flatMap(Prims.mkLatch) { aMayFinish =>
            F.flatMap(Prims.mkLatch) { bStarted =>
              F.flatMap(Prims.mkLatch) { bMayFinish =>
                val slowA: F[Nothing, Int] =
                  F.flatMap(aStarted.succeed(()))(_ => F.map(aMayFinish.await)(_ => 1))
                val slowB: F[Nothing, Int] =
                  F.flatMap(bStarted.succeed(()))(_ => F.map(bMayFinish.await)(_ => 2))
                F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", slowA))) { fiberA =>
                  F.flatMap(aStarted.await) { _ =>
                    F.flatMap(cache.invalidate("k")) { _ =>
                      F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", slowB))) { fiberB =>
                        F.flatMap(bStarted.await) { _ =>
                          // A finishes first: publishes Ready(1) over Computing(B).
                          F.flatMap(aMayFinish.succeed(())) { _ =>
                            F.flatMap(Temp.timeout(5.seconds)(fiberA.join)) { aRes =>
                              // B finishes: sees A's Ready with non-null origin, treats
                              // as overridable compute-Ready, publishes Ready(2).
                              F.flatMap(bMayFinish.succeed(())) { _ =>
                                F.flatMap(Temp.timeout(5.seconds)(fiberB.join)) { bRes =>
                                  F.map(cache.get("k")) { cached =>
                                    assert(aRes.contains(1), s"A returns its own value 1; got $aRes")
                                    assert(bRes.contains(2), s"B returns its own value 2; got $bRes")
                                    assert(
                                      cached.contains(2),
                                      s"cache must end with B's value (newer post-invalidate compute wins). " +
                                        s"Got $cached. If Some(1), older compute suppressed the refresh — freshness-boundary regression.",
                                    )
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
          }
        }
      }
    }

    "A → invalidate → B → B publishes → A publishes late: A's stale value does NOT overwrite B's Ready" in run {
      // Codex-flagged deterministic scenario: under a previous version of the publish
      // rule that allowed overwriting compute-produced Ready (origin != null), a late
      // pre-invalidate producer A could overwrite B's post-invalidate Ready, defeating
      // the freshness boundary. Under the current rule (any foreign Ready blocks
      // publication), A's late publish is suppressed and B's value survives.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { aStarted =>
          F.flatMap(Prims.mkLatch) { aMayFinish =>
            val slowA: F[Nothing, Int] =
              F.flatMap(aStarted.succeed(()))(_ => F.map(aMayFinish.await)(_ => 1))
            F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", slowA))) { fiberA =>
              F.flatMap(aStarted.await) { _ =>
                F.flatMap(cache.invalidate("k")) { _ =>
                  // B runs synchronously to completion, publishing Ready(2) before A finishes.
                  F.flatMap(cache.computeIfAbsent[Nothing]("k", F.pure(2))) { bResult =>
                    F.flatMap(cache.get("k")) { cachedBeforeA =>
                      // Release A; A's publish-modify sees foreign Ready(2) and refuses.
                      F.flatMap(aMayFinish.succeed(())) { _ =>
                        F.flatMap(Temp.timeout(5.seconds)(fiberA.join)) { aResult =>
                          F.map(cache.get("k")) { cachedAfterA =>
                            assert(bResult == 2, s"B returns its own value 2; got $bResult")
                            assert(cachedBeforeA.contains(2), s"cache holds B's Ready(2) after B publishes; got $cachedBeforeA")
                            assert(aResult.contains(1), s"A's caller still receives its own v=1 (loader-result); got $aResult")
                            assert(
                              cachedAfterA.contains(2),
                              s"cache MUST still hold B's Ready(2) — late pre-invalidate A cannot overwrite " +
                                s"post-invalidate refresh. Got $cachedAfterA. If Some(1), the freshness " +
                                "barrier regressed: stale data re-entered after a successful refresh.",
                            )
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

    "A → invalidate → B → B fails first → A succeeds: A does NOT resurrect stale data (strict freshness)" in run {
      // Codex-flagged scenario: without strict "publish only if own Computing
      // survives", the following leaks pre-invalidate data. A starts. invalidate
      // removes A's Computing. B starts (Computing(B)). B fails — its cleanup
      // removes Computing(B), leaving the bucket empty. A finishes last; a
      // permissive "publish if empty" rule would let A publish here and resurrect
      // A's pre-invalidate value past the invalidation boundary.
      //
      // Under the strict rule (publish only if own Computing is still in slot),
      // A's publish sees an empty bucket, observes "our Computing is gone", and
      // REFUSES to publish. Cache stays empty. Next caller recomputes.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { aStarted =>
          F.flatMap(Prims.mkLatch) { aMayFinish =>
            F.flatMap(Prims.mkLatch) { bStarted =>
              F.flatMap(Prims.mkLatch) { bMayFail =>
                val slowA: F[Nothing, Int] =
                  F.flatMap(aStarted.succeed(()))(_ => F.map(aMayFinish.await)(_ => 1))
                val slowB: F[String, Int] = {
                  val prep: F[Nothing, Unit] =
                    F.flatMap(bStarted.succeed(()))(_ => bMayFail.await)
                  F.flatMap[String, Unit, Int](prep)(_ => F.fail("B failed"))
                }
                F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", slowA))) { fiberA =>
                  F.flatMap(aStarted.await) { _ =>
                    F.flatMap(cache.invalidate("k")) { _ =>
                      F.flatMap(Fk.fork(cache.computeIfAbsent[String]("k", slowB).redeemPure(_ => -1, identity))) { fiberB =>
                        F.flatMap(bStarted.await) { _ =>
                          // Release B first so B fails and its cleanup empties the slot.
                          F.flatMap(bMayFail.succeed(())) { _ =>
                            F.flatMap(Temp.timeout(5.seconds)(fiberB.join)) { bRes =>
                              // Now release A. Slot is empty; A must NOT publish.
                              F.flatMap(aMayFinish.succeed(())) { _ =>
                                F.flatMap(Temp.timeout(5.seconds)(fiberA.join)) { aRes =>
                                  F.map(cache.get("k")) { cached =>
                                    assert(aRes.contains(1), s"A's caller still receives its own v=1 (loader-result); got $aRes")
                                    assert(bRes.contains(-1), s"B's failure propagates; got $bRes")
                                    assert(
                                      cached.isEmpty,
                                      s"cache MUST stay empty — A is pre-invalidate and cannot repopulate just because " +
                                        s"B's failure briefly emptied the slot. Got $cached. If Some(1), strict freshness " +
                                        "regressed: pre-invalidate data resurrected through a failed refresh.",
                                    )
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
          }
        }
      }
    }

    "A → invalidate → B → A succeeds → B fails: A's stale value is correctly suppressed (freshness barrier)" in run {
      // Codex-flagged deterministic scenario for the invalidate freshness invariant:
      //   1. A starts a slow compute (installs Computing(A)).
      //   2. invalidate removes Computing(A) from the bucket.
      //   3. B starts computeIfAbsent with a distinct slow compute → installs Computing(B).
      //   4. A is released and succeeds with value 1. A's publish-modify sees the
      //      foreign Computing(B) and REFUSES to publish — A is a pre-invalidate
      //      load and must not overwrite the post-invalidate refresh.
      //   5. B is released with a failure. B's cleanup removes Computing(B), leaving
      //      the cache EMPTY. The next caller recomputes fresh.
      //
      // A's direct caller still receives its own v=1 via the loader-result contract.
      // The cache rejects A's value at the cache-state level because `invalidate`
      // communicated that the prior value is stale; preserving A through a failed
      // refresh would let pre-invalidate data survive indefinitely — exactly the
      // freshness-boundary violation Codex flagged.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { aStarted =>
          F.flatMap(Prims.mkLatch) { aMayFinish =>
            F.flatMap(Prims.mkLatch) { bStarted =>
              F.flatMap(Prims.mkLatch) { bMayFinish =>
                F.flatMap(Prims.mkRef(0)) { aRuns =>
                  F.flatMap(Prims.mkRef(0)) { bRuns =>
                    val slowA: F[Nothing, Int] = F.flatMap(aRuns.update(_ + 1))(_ =>
                      F.flatMap(aStarted.succeed(()))(_ => F.map(aMayFinish.await)(_ => 1))
                    )
                    val slowB: F[String, Int] = {
                      val prep: F[Nothing, Unit] =
                        F.flatMap(bRuns.update(_ + 1))(_ =>
                          F.flatMap(bStarted.succeed(()))(_ => bMayFinish.await)
                        )
                      F.flatMap[String, Unit, Int](prep)(_ => F.fail("B failed"))
                    }
                    F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", slowA))) { fiberA =>
                      F.flatMap(aStarted.await) { _ =>
                        F.flatMap(cache.invalidate("k")) { _ =>
                          F.flatMap(Fk.fork(cache.computeIfAbsent[String]("k", slowB).redeemPure(_ => -1, identity))) { fiberB =>
                            F.flatMap(bStarted.await) { _ =>
                              // Release A first; A tries to publish but is blocked by Computing(B).
                              F.flatMap(aMayFinish.succeed(())) { _ =>
                                F.flatMap(Temp.timeout(5.seconds)(fiberA.join)) { aRes =>
                                  // Release B. B fails; its cleanup leaves the slot empty.
                                  F.flatMap(bMayFinish.succeed(())) { _ =>
                                    F.flatMap(Temp.timeout(5.seconds)(fiberB.join)) { bRes =>
                                      F.flatMap(cache.get("k")) { cached =>
                                        F.flatMap(aRuns.get) { aN =>
                                          F.map(bRuns.get) { bN =>
                                            assert(aRes.contains(1), s"A's caller still receives its own v=1 (loader-result contract); got $aRes")
                                            assert(bRes.contains(-1), s"B's failure propagates; got $bRes")
                                            assert(
                                              cached.isEmpty,
                                              s"cache MUST be empty — A is pre-invalidate and cannot repopulate after a failed post-invalidate refresh. " +
                                                s"Got $cached. If Some(1), the freshness barrier regressed: stale data survives invalidation.",
                                            )
                                            assert(aN == 1, s"A's compute runs exactly once; got $aN")
                                            assert(bN == 1, s"B's compute runs exactly once; got $bN")
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
                  }
                }
              }
            }
          }
        }
      }
    }

    "invalidate immediately wakes parked waiters (release semantics — no wedge on hung producer)" in run {
      // Parked waiter on a hung producer. invalidate signals the displaced Computing's
      // promise `None` INSIDE its uninterruptible modify step. Waiter wakes, retries via
      // computeImpl, sees empty bucket, runs its own fallback — NO need for the producer
      // to complete and NO dependence on caller-layer timeouts for liveness.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          val hangForever: F[Nothing, Int] = F.flatMap(Prims.mkPromise[Nothing, Int])(_.await)
          val hangingCompute: F[Nothing, Int] = F.flatMap(started.succeed(()))(_ => hangForever)
          F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", hangingCompute))) { producerFib =>
            F.flatMap(started.await) { _ =>
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-1)))) { waiterFib =>
                F.flatMap(awaitBlocked(waiterFib, 200.millis)) { _ =>
                  F.flatMap(cache.invalidate("k")) { _ =>
                    // Waiter must unblock without interrupting the producer first.
                    F.flatMap(Temp.timeout(2.seconds)(waiterFib.join)) { waiterResult =>
                      F.map(producerFib.interrupt) { _ =>
                        assert(
                          waiterResult.contains(-1),
                          s"waiter released by invalidate, retries and runs fallback=-1; got $waiterResult",
                        )
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

    "invalidateAll immediately wakes parked waiters (release semantics)" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          val hangForever: F[Nothing, Int] = F.flatMap(Prims.mkPromise[Nothing, Int])(_.await)
          val hangingCompute: F[Nothing, Int] = F.flatMap(started.succeed(()))(_ => hangForever)
          F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", hangingCompute))) { producerFib =>
            F.flatMap(started.await) { _ =>
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-1)))) { waiterFib =>
                F.flatMap(awaitBlocked(waiterFib, 200.millis)) { _ =>
                  F.flatMap(cache.invalidateAll) { _ =>
                    F.flatMap(Temp.timeout(2.seconds)(waiterFib.join)) { waiterResult =>
                      F.map(producerFib.interrupt) { _ =>
                        assert(
                          waiterResult.contains(-1),
                          s"waiter released by invalidateAll, retries and runs fallback=-1; got $waiterResult",
                        )
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

    "put immediately wakes parked waiters (release semantics — waiter observes put's value)" in run {
      // put signals the displaced Computing promise `None` and installs its Ready(100)
      // atomically. Waiter wakes, retries computeImpl, hits put's Ready, returns 100.
      // No wait for producer completion; no caller-layer timeout needed.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          val hangForever: F[Nothing, Int] = F.flatMap(Prims.mkPromise[Nothing, Int])(_.await)
          val hangingCompute: F[Nothing, Int] = F.flatMap(started.succeed(()))(_ => hangForever)
          F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", hangingCompute))) { producerFib =>
            F.flatMap(started.await) { _ =>
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-1)))) { waiterFib =>
                F.flatMap(awaitBlocked(waiterFib, 200.millis)) { _ =>
                  F.flatMap(cache.put("k", 100)) { _ =>
                    F.flatMap(Temp.timeout(2.seconds)(waiterFib.join)) { waiterResult =>
                      F.map(producerFib.interrupt) { _ =>
                        assert(
                          waiterResult.contains(100),
                          s"waiter released by put, retries and hits put's Ready(100); got $waiterResult",
                        )
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

    "eager eviction + zero TTL: waiter still receives producer's value via promise" in run {
      // The hardest variant of the zero-TTL waiter handoff: the background eager-eviction
      // fiber can run `evictExpired` in parallel with the waiter's wake, potentially
      // cleaning the producer's Ready from the bucket before the waiter reads. A
      // bucket-based handoff (the previous fix) loses the race; the value-carrying
      // promise (current fix) bypasses the bucket entirely and remains correct.
      val config = CacheConfig(
        defaultTTL = Some(Duration.Zero),
        eagerEvictionInterval = Some(5.millis),
      )
      F.flatMap(BIOCache.makeEager[F, String, Int](config)) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          F.flatMap(Prims.mkLatch) { mayFinish =>
            F.flatMap(Prims.mkRef(0)) { runCount =>
              val compute: F[Nothing, Int] = F.flatMap(runCount.update(_ + 1)) { _ =>
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 42))
              }
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))) { producerFib =>
                F.flatMap(started.await) { _ =>
                  F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))) { waiterFib =>
                    F.flatMap(awaitBlocked(waiterFib, 300.millis)) { _ =>
                      F.flatMap(mayFinish.succeed(())) { _ =>
                        // Give the eager eviction fiber several cycles to potentially
                        // run its cleanBucket over the freshly-published expired Ready.
                        F.flatMap(Temp.sleep(50.millis)) { _ =>
                          F.flatMap(Temp.timeout(5.seconds)(producerFib.join)) { pResult =>
                            F.flatMap(Temp.timeout(5.seconds)(waiterFib.join)) { wResult =>
                              F.flatMap(cache.close) { _ =>
                                F.map(runCount.get) { count =>
                                  assert(pResult.contains(42), s"producer returns 42; got $pResult")
                                  assert(
                                    wResult.contains(42),
                                    s"waiter receives 42 via promise payload despite eager eviction cleaning the bucket. " +
                                      s"Got $wResult. If the waiter re-ran compute, eager eviction won the race against " +
                                      "the waiter's bucket read — the exact zero-TTL+eager-eviction regression.",
                                  )
                                  assert(
                                    count == 1,
                                    s"compute runs exactly once across producer+waiter+eager-eviction. Got $count.",
                                  )
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
        }
      }
    }

    "concurrent callers with zero TTL: producer runs compute once; waiter receives producer's value" in run {
      // Codex-identified regression: under the signal-only waiter handoff, a waiter that
      // wakes after the producer published a zero-TTL (immediately-expired) Ready used to
      // read the cache via `get`, which filters expired entries. The waiter would then
      // observe `None` and retry `compute` — violating at-most-once for concurrent
      // callers sharing a single in-flight load.
      //
      // Fix: the waiter reads the bucket RAW, bypassing TTL filtering. The producer's
      // signal is the commit point: if a Ready for the key is present at wake, the
      // waiter returns its value regardless of TTL. TTL still governs visibility to
      // SUBSEQUENT callers (they see the expired Ready filtered by `get`/cleanBucket).
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          F.flatMap(Prims.mkLatch) { mayFinish =>
            F.flatMap(Prims.mkRef(0)) { runCount =>
              val compute: F[Nothing, Int] = F.flatMap(runCount.update(_ + 1)) { _ =>
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 42))
              }
              // TTL = 0 → Ready(v, expiresAt = publish-time) is already expired at wake.
              val zeroTtl: FiniteDuration = Duration.Zero
              F.flatMap(Fk.fork(cache.computeIfAbsentWithTTL[Nothing]("k", zeroTtl, compute))) { producerFib =>
                F.flatMap(started.await) { _ =>
                  F.flatMap(Fk.fork(cache.computeIfAbsentWithTTL[Nothing]("k", zeroTtl, compute))) { waiterFib =>
                    F.flatMap(awaitBlocked(waiterFib, 300.millis)) { _ =>
                      F.flatMap(mayFinish.succeed(())) { _ =>
                        F.flatMap(Temp.timeout(5.seconds)(producerFib.join)) { producerResult =>
                          F.flatMap(Temp.timeout(5.seconds)(waiterFib.join)) { waiterResult =>
                            F.map(runCount.get) { count =>
                              assert(producerResult.contains(42), s"producer returns 42; got $producerResult")
                              assert(
                                waiterResult.contains(42),
                                s"waiter must receive producer's value despite expired TTL. Got $waiterResult. " +
                                  "If the waiter's retry ran (compute count > 1), the at-most-once guarantee " +
                                  "for concurrent callers is broken under zero/short TTLs.",
                              )
                              assert(
                                count == 1,
                                s"compute must run exactly ONCE across the producer/waiter pair. Got $count. " +
                                  ">1 means the waiter re-ran compute because it observed `None` via a TTL-filtering " +
                                  "cache read after the producer had published — the exact Codex-identified regression.",
                              )
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
    }

    "caller-side Temp.timeout on producer unblocks parked waiters (recommended liveness pattern)" in run {
      // Documents and pins the idiomatic liveness story for Guava semantics: the
      // producer wraps its computeIfAbsent in Temp.timeout. When the timeout fires, the
      // producer's fiber is interrupted; its guaranteeOnFailure cleanup removes the
      // Computing entry and signals the promise, waking any parked waiter which then
      // reads the (now empty) cache, retries, and runs its own compute.
      //
      // This is the answer to "hung loader hangs waiters" under strict Guava:
      // bound the producer's lifetime at the caller layer, and the cache's own cleanup
      // handles the rest.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          val hangForever: F[Nothing, Int] = F.flatMap(Prims.mkPromise[Nothing, Int])(_.await)
          val hangingCompute: F[Nothing, Int] = F.flatMap(started.succeed(()))(_ => hangForever)
          // Producer wraps its call in Temp.timeout — the recommended pattern.
          val producerWithTimeout: F[Nothing, Option[Int]] =
            Temp.timeout(500.millis)(cache.computeIfAbsent[Nothing]("k", hangingCompute))
          F.flatMap(Fk.fork(producerWithTimeout)) { producerFib =>
            F.flatMap(started.await) { _ =>
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(42)))) { waiterFib =>
                F.flatMap(awaitBlocked(waiterFib, 200.millis)) { _ =>
                  // Waiter is parked. No control ops fired. The producer's own timeout
                  // must eventually release the waiter via producer-cleanup → pA.signal.
                  F.flatMap(Temp.timeout(5.seconds)(producerFib.join)) { producerExit =>
                    F.flatMap(Temp.timeout(5.seconds)(waiterFib.join)) { waiterResult =>
                      F.map(cache.get("k")) { cached =>
                        assert(producerExit.contains(None), s"producer's Temp.timeout fired, returning None; got $producerExit")
                        assert(waiterResult.contains(42), s"waiter's retry runs its own compute after producer cleanup; got $waiterResult")
                        assert(cached.contains(42), s"cache reflects waiter's published Ready(42); got $cached")
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

    "waiter's caller-side timeout frees only itself, not peers (waiter-unwind contract)" in run {
      // Completes the liveness story: a waiter's OWN timeout interrupts that waiter's
      // fiber but does NOT signal the producer's Computing promise. Peer waiters remain
      // parked until the producer itself completes.
      //
      // Under Guava semantics, only the producer's cleanup (via `guaranteeOnFailure` in
      // `doCompute`) removes the Computing entry and signals pA. Waiters never enter
      // `doCompute` (they took the ActionWait branch), so their unwind has no cleanup
      // hook — they just exit. This test pins that waiter-level timeouts provide per-
      // call liveness only, NOT shared-key liveness. The shared-key liveness bound is
      // the producer's timeout.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          val hangForever: F[Nothing, Int] = F.flatMap(Prims.mkPromise[Nothing, Int])(_.await)
          val hangingCompute: F[Nothing, Int] = F.flatMap(started.succeed(()))(_ => hangForever)
          F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", hangingCompute))) { producerFib =>
            F.flatMap(started.await) { _ =>
              // Waiter 1 wraps its own call in a timeout — it alone will give up.
              val waiter1WithTimeout: F[Nothing, Option[Int]] =
                Temp.timeout(300.millis)(cache.computeIfAbsent[Nothing]("k", F.pure(42)))
              F.flatMap(Fk.fork(waiter1WithTimeout)) { waiter1Fib =>
                // Waiter 2 has no timeout — it must stay parked after waiter 1 times out.
                F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(42)))) { waiter2Fib =>
                  F.flatMap(awaitBlocked(waiter1Fib, 100.millis)) { _ =>
                    F.flatMap(awaitBlocked(waiter2Fib, 100.millis)) { _ =>
                      // Let waiter1 time out, then verify waiter2 is STILL blocked.
                      F.flatMap(Temp.timeout(3.seconds)(waiter1Fib.join)) { waiter1Exit =>
                        F.flatMap(awaitBlocked(waiter2Fib, 300.millis)) { _ =>
                          // Release waiter2 by interrupting the producer (its cleanup
                          // signals pA — the only path that wakes waiter2).
                          F.flatMap(producerFib.interrupt) { _ =>
                            F.map(Temp.timeout(3.seconds)(waiter2Fib.join)) { waiter2Result =>
                              assert(
                                waiter1Exit.contains(None),
                                s"waiter1's Temp.timeout fires, returning None; got $waiter1Exit",
                              )
                              assert(
                                waiter2Result.contains(42),
                                s"waiter2 wakes ONLY after producer's cleanup signals pA; got $waiter2Result. " +
                                  "If waiter2 unblocked earlier, waiter1's timeout spuriously signaled pA — " +
                                  "that would contradict the waiter-unwind contract.",
                              )
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
    }

    "interrupting computing fiber should unblock waiters" in run {
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { computeStarted =>
          val hangForever: F[Nothing, Int] = F.flatMap(Prims.mkPromise[Nothing, Int])(_.await)
          val hangingCompute: F[Nothing, Int] = F.flatMap(computeStarted.succeed(()))(_ => hangForever)
          F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", hangingCompute))) { computeFiber =>
            F.flatMap(computeStarted.await) { _ =>
              val waiterCompute: F[Nothing, Int] = F.pure(99)
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", waiterCompute))) { waiterFiber =>
                // Deterministically verify waiter has parked on A's promise (not run its own compute)
                F.flatMap(awaitBlocked(waiterFiber, 300.millis)) { _ =>
                  F.flatMap(computeFiber.interrupt) { _ =>
                    // The waiter should retry and succeed with its own compute
                    F.flatMap(Temp.timeout(5.seconds)(waiterFiber.join)) { result =>
                      F.map(cache.get("k")) { cached =>
                        assert(result.contains(99), s"waiter should have retried and succeeded, got $result")
                        assert(cached.contains(99), s"cache should contain the waiter's computed value, got $cached")
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

    // ---- Waiter race tests: producer completion + cache re-read converge on correct value ----

    "put during in-flight compute: producer returns own value; waiter gets put's value (freshness barrier)" in run {
      // Under the freshness-barrier publish rule: put replaces Computing with
      // Ready(put_val). Producer runs `compute` to completion and returns ITS OWN
      // computed value to its caller (loader-result contract). But since the slot
      // now holds a foreign Ready, producer's publish is REJECTED and the promise
      // is signaled `None`. Waiter wakes, retries via `computeImpl`, sees put's
      // Ready(999), and returns that — it does NOT run its own fallback. This
      // routes waiters to the freshest available cache state instead of leaking
      // the (now stale relative to put) producer result.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { computeStarted =>
          F.flatMap(Prims.mkLatch) { mutationDone =>
            F.flatMap(Prims.mkRef(0)) { waiterFallbackRuns =>
              val slowCompute: F[Nothing, Int] =
                F.flatMap(computeStarted.succeed(()))(_ => F.map(mutationDone.await)(_ => 1))
              val waiterFallback: F[Nothing, Int] =
                F.flatMap(waiterFallbackRuns.update(_ + 1))(_ => F.pure(-999))
              F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", slowCompute))) { fiberA =>
                F.flatMap(computeStarted.await) { _ =>
                  F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", waiterFallback))) { fiberB =>
                    F.flatMap(awaitBlocked(fiberB, 300.millis)) { _ =>
                      F.flatMap(cache.put("k", 999)) { _ =>
                        F.flatMap(mutationDone.succeed(())) { _ =>
                          F.flatMap(Temp.timeout(5.seconds)(fiberA.join)) { aResult =>
                            F.flatMap(Temp.timeout(5.seconds)(fiberB.join)) { bResult =>
                              F.flatMap(cache.get("k")) { cached =>
                                F.map(waiterFallbackRuns.get) { fallbackCount =>
                                  assert(
                                    aResult.contains(1),
                                    s"producer A returns its OWN computed value (1), loader-result contract " +
                                      s"— NOT put's value. Got $aResult.",
                                  )
                                  assert(
                                    bResult.contains(999),
                                    s"waiter B routes via None→retry→hit on put's Ready and returns put's " +
                                      s"value (999) — NOT producer's pre-put value (which would leak stale " +
                                      s"data past the put barrier). Got $bResult.",
                                  )
                                  assert(cached.contains(999), s"cache holds put's value; got $cached")
                                  assert(
                                    fallbackCount == 0,
                                    s"waiter's fallback must NOT run — it hits put's Ready on retry. Got $fallbackCount.",
                                  )
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
        }
      }
    }

    "invalidate during in-flight compute: producer returns own v; waiter retries and re-runs compute; cache stays empty" in run {
      // Strict freshness barrier: invalidate removes the producer's Computing.
      // Producer's direct caller gets its own `v` (loader-result). Publish is
      // rejected (no own Computing). Promise is signaled `None`. Waiter retries
      // via computeImpl, sees empty bucket, installs its OWN Computing and runs
      // its OWN fallback (since there's no one else to dedup with). The waiter's
      // compute runs ONCE (after retry); producer's compute runs ONCE. Cache ends
      // holding whatever the waiter published — IF the waiter's own Computing was
      // still there at its publish time (which it is, since nothing else is in
      // flight). So cache ends up = Ready(waiter's fallback).
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { computeStarted =>
          F.flatMap(Prims.mkLatch) { mutationDone =>
            F.flatMap(Prims.mkRef(0)) { producerRuns =>
              F.flatMap(Prims.mkRef(0)) { waiterRuns =>
                val slowCompute: F[Nothing, Int] =
                  F.flatMap(producerRuns.update(_ + 1))(_ =>
                    F.flatMap(computeStarted.succeed(()))(_ => F.map(mutationDone.await)(_ => 1))
                  )
                val waiterFallback: F[Nothing, Int] =
                  F.flatMap(waiterRuns.update(_ + 1))(_ => F.pure(42))

                F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", slowCompute))) { fiberA =>
                  F.flatMap(computeStarted.await) { _ =>
                    F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", waiterFallback))) { fiberB =>
                      F.flatMap(awaitBlocked(fiberB, 300.millis)) { _ =>
                        F.flatMap(cache.invalidate("k")) { _ =>
                          F.flatMap(mutationDone.succeed(())) { _ =>
                            F.flatMap(Temp.timeout(5.seconds)(fiberA.join)) { aResult =>
                              F.flatMap(Temp.timeout(5.seconds)(fiberB.join)) { bResult =>
                                F.flatMap(cache.get("k")) { cached =>
                                  F.flatMap(producerRuns.get) { pRuns =>
                                    F.map(waiterRuns.get) { wRuns =>
                                      assert(aResult.contains(1), s"producer returns its own computed value 1 (loader-result); got $aResult")
                                      assert(bResult.contains(42), s"waiter retries on None and runs its fallback=42; got $bResult")
                                      assert(cached.contains(42), s"cache = waiter's Ready(42) — waiter publishes after its own compute; got $cached")
                                      assert(pRuns == 1, s"producer's compute runs exactly once; got $pRuns")
                                      assert(wRuns == 1, s"waiter's fallback runs ONCE under strict freshness (retry after producer's None signal); got $wRuns")
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
            }
          }
        }
      }
    }

    "invalidateAll during in-flight compute: producer returns own v; waiter retries and re-runs compute" in run {
      // Same strict-freshness contract as the invalidate test above.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { computeStarted =>
          F.flatMap(Prims.mkLatch) { mutationDone =>
            F.flatMap(Prims.mkRef(0)) { producerRuns =>
              F.flatMap(Prims.mkRef(0)) { waiterRuns =>
                val slowCompute: F[Nothing, Int] =
                  F.flatMap(producerRuns.update(_ + 1))(_ =>
                    F.flatMap(computeStarted.succeed(()))(_ => F.map(mutationDone.await)(_ => 1))
                  )
                val waiterFallback: F[Nothing, Int] =
                  F.flatMap(waiterRuns.update(_ + 1))(_ => F.pure(42))

                F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", slowCompute))) { fiberA =>
                  F.flatMap(computeStarted.await) { _ =>
                    F.flatMap(Fk.fork(cache.computeIfAbsent[Nothing]("k", waiterFallback))) { fiberB =>
                      F.flatMap(awaitBlocked(fiberB, 300.millis)) { _ =>
                        F.flatMap(cache.invalidateAll) { _ =>
                          F.flatMap(mutationDone.succeed(())) { _ =>
                            F.flatMap(Temp.timeout(5.seconds)(fiberA.join)) { aResult =>
                              F.flatMap(Temp.timeout(5.seconds)(fiberB.join)) { bResult =>
                                F.flatMap(cache.get("k")) { cached =>
                                  F.flatMap(producerRuns.get) { pRuns =>
                                    F.map(waiterRuns.get) { wRuns =>
                                      assert(aResult.contains(1), s"producer returns own value 1 (loader-result); got $aResult")
                                      assert(bResult.contains(42), s"waiter retries on None and runs its fallback=42; got $bResult")
                                      assert(cached.contains(42), s"cache = waiter's Ready(42); got $cached")
                                      assert(pRuns == 1, s"producer's compute runs exactly once; got $pRuns")
                                      assert(wRuns == 1, s"waiter's fallback runs ONCE after retry; got $wRuns")
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
            }
          }
        }
      }
    }

    // ---- Guava semantics: control ops do NOT preempt in-flight loaders ----

    "stress: concurrent computeIfAbsent and put converge on cache-consistent final state" in run {
      // Exercise the publication/displacement interleaving: between a computing fiber's
      // compute finishing and its atomic `publish Ready IFF we still own Computing` CAS,
      // a concurrent `put` may replace the Computing with its own Ready. Under Guava
      // loader-result semantics:
      //   - Regardless of ownership, the producer ALWAYS returns its own computed
      //     value (c-i). A racing put does NOT hijack the return.
      //   - The producer's publish-modify republishes Ready(c-i) iff it still owns its
      //     Computing slot; otherwise it leaves whatever the displacer installed.
      //   - Compute runs exactly once per call (at-most-once).
      //
      // This stress test runs many independent races on distinct keys and asserts:
      //   1. cResult == s"c-$i" for EVERY iteration (producer-result preservation).
      //   2. Cache holds one of the two racing values (last writer wins).
      val iterations = 300
      F.flatMap(BIOCache.make[F, Int, String](CacheConfig(initialCapacity = 32))) { cache =>
        F.flatMap(F.traverse((0 until iterations).toList) { i =>
          val computedVal = s"c-$i"
          val putVal = s"p-$i"
          for {
            cFiber <- Fk.fork(cache.computeIfAbsent[Nothing](i, F.pure(computedVal)))
            pFiber <- Fk.fork(cache.put(i, putVal))
            cResult <- cFiber.join
            _ <- pFiber.join
            cached <- cache.get(i)
          } yield (cResult, cached, i)
        }) { results =>
          F.pure {
            val producerResultLeaks = results.filter { case (cResult, _, i) => cResult != s"c-$i" }
            assert(
              producerResultLeaks.isEmpty,
              s"producer must ALWAYS return its own computed c-i under Guava loader-result " +
                s"semantics; a racing put must never hijack the return value. " +
                s"Violations: ${producerResultLeaks.take(5)}",
            )
            val cacheStateViolations = results.filter { case (_, cached, i) =>
              val valid = Set(s"c-$i", s"p-$i")
              !cached.exists(valid.contains)
            }
            assert(
              cacheStateViolations.isEmpty,
              s"cache must hold one of the two racing values (last writer wins). " +
                s"Violations: ${cacheStateViolations.take(5)}",
            )
          }
        }
      }
    }

    "stress: concurrent computeIfAbsent with a waiter and racing put yields consistent results" in run {
      // Three-party race: A (slow compute), B (waiter parked on A's promise), and a
      // concurrent put that `pFiber.join`-awaits to commit before A is released.
      //
      // Under the freshness-barrier publish rule:
      //   - A ALWAYS returns its own computed c-i (loader-result contract —
      //     the fiber that invoked compute receives what compute produced).
      //   - put's Ready(p-i) occupies the slot by the time A's publish modify runs,
      //     so A's publish is rejected and the promise is signaled `None`.
      //   - B wakes with `None`, retries via `computeImpl`, sees Ready(p-i), returns
      //     p-i — freshest cache state. The waiter's fallback w-i must NEVER run.
      //   - cache = Ready(p-i).
      val iterations = 50
      F.flatMap(BIOCache.make[F, Int, String](CacheConfig(initialCapacity = 32))) { cache =>
        F.flatMap(F.traverse((0 until iterations).toList) { i =>
          val computedVal = s"c-$i"
          val waiterFallback = s"w-$i"
          val putVal = s"p-$i"
          for {
            started <- Prims.mkLatch
            mayFinish <- Prims.mkLatch
            slowCompute = F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => computedVal))
            aFiber <- Fk.fork(cache.computeIfAbsent[Nothing](i, slowCompute))
            _ <- started.await
            bFiber <- Fk.fork(cache.computeIfAbsent[Nothing](i, F.pure(waiterFallback)))
            _ <- awaitBlocked(bFiber, 200.millis)
            pFiber <- Fk.fork(cache.put(i, putVal))
            _ <- pFiber.join
            _ <- mayFinish.succeed(())
            aResult <- Temp.timeout(5.seconds)(aFiber.join)
            bResult <- Temp.timeout(5.seconds)(bFiber.join)
            cached <- cache.get(i)
          } yield (aResult, bResult, cached, i)
        }) { results =>
          F.pure {
            // 1. A ALWAYS returns its own c-i (loader-result contract).
            val producerLeaks = results.filter { case (a, _, _, i) => !a.contains(s"c-$i") }
            assert(
              producerLeaks.isEmpty,
              s"producer A must always return its own computed c-i (loader-result). " +
                s"Violations: ${producerLeaks.take(5)}",
            )
            // 2. B ALWAYS returns p-i — it retries after None and hits put's Ready.
            //    w-i would mean the waiter ran its own compute (dedup regression);
            //    c-i would mean A's Some(v) leaked past the freshness barrier.
            val waiterLeaks = results.filter { case (_, b, _, i) => !b.contains(s"p-$i") }
            assert(
              waiterLeaks.isEmpty,
              s"waiter B must always return p-i (put's value) after routing through None→retry→hit. " +
                s"c-i would be a freshness-barrier leak; w-i would be a dedup regression. " +
                s"Violations: ${waiterLeaks.take(5)}",
            )
            // 3. Cache always holds put's value at the end.
            val cacheMismatches = results.filter { case (_, _, cached, i) => !cached.contains(s"p-$i") }
            assert(
              cacheMismatches.isEmpty,
              s"cache must hold put's value (the final commit); got: ${cacheMismatches.take(5)}",
            )
          }
        }
      }
    }

    "N waiters woken with empty cache dedup their retries (at most 1 recompute across N)" in run {
      // Pins the waiter-retry dedup invariant under Guava semantics: when a producer's
      // promise is signaled (here via FAILURE cleanup) and the cache is empty, N parked
      // waiters all wake and race through their retry. The retry's bucket CAS dedups
      // them: exactly ONE installs a fresh Computing and runs its compute; the others
      // park on that new Computing and observe its published Ready.
      //
      // Total compute invocations across N waiters + 1 producer = 2, independent of N.
      //
      // Why this scenario matters: under Guava semantics, control ops NEVER wake waiters,
      // so the only way waiters can be simultaneously woken to an empty cache is via the
      // producer itself completing without publishing (i.e., producer failed or was
      // interrupted — as modeled here with a failing compute). The fanout bound is still
      // 1 extra compute, not N.
      //
      // DETERMINISTIC PARK PROOF: we use a custom `Primitives2` that instruments cache-
      // created promises. Every `.await` on a cache promise increments a counter. Since
      // (a) producers do NOT await their own Computing promise, (b) waiters create pWi
      // but don't await it (they await pProd via the `ActionWait` branch), and (c) the
      // producer's `.await(producerMayFail)` uses a latch created OUTSIDE the cache (via
      // the default `Prims`), the ONLY calls to `.await` on a cache-created promise come
      // from waiters hitting pProd.
      //
      // We therefore wait until the counter == N before releasing the producer —
      // deterministic proof that all N waiters have entered the pProd-await step.
      val n = 50
      import java.util.concurrent.atomic.AtomicInteger
      val cachePromiseAwaitCount = new AtomicInteger(0)
      val defaultPrimsForCache: Primitives2[F] = Prims
      // Non-implicit — we pass it explicitly to `create`. Scala 2.13 won't let a local
      // implicit shadow the class-level `Prims`, so we bypass implicit resolution for
      // the cache construction.
      val instrumentedPrims: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = {
                F.flatMap(F.sync { val _ = cachePromiseAwaitCount.incrementAndGet() })(_ => inner.await)
              }
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def succeed(a: A): F[Nothing, Boolean] = inner.succeed(a)
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
            }
          }
        }
      }
      // Build the cache with our instrumented Primitives2 (explicit args, not implicit).
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumentedPrims,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(Prims.mkRef(0)) { computeCount =>
          // Latches below use the ORIGINAL `Prims` (not the instrumented one) so their
          // awaits do NOT contribute to `cachePromiseAwaitCount`. This is critical: if
          // the producer's `producerMayFail.await` were instrumented, the counter would
          // already be 1 before any waiter parked, corrupting the "counter == N" signal.
          F.flatMap(Prims.mkLatch) { producerStarted =>
            F.flatMap(Prims.mkLatch) { producerMayFail =>
              val producerCompute: F[String, Int] =
                F.flatMap(producerStarted.succeed(())) { _ =>
                  F.flatMap(producerMayFail.await) { _ =>
                    F.flatMap(computeCount.update(_ + 1))(_ => F.fail("producer-fail"))
                  }
                }
              val waiterCompute: F[Nothing, Int] =
                F.flatMap(computeCount.update(_ + 1))(_ => F.pure(42))
              // Bucket-state guard: prove no waiter took `ActionCompute` (would add a
              // new Computing to the bucket, pushing size > 1). Combined with the
              // `cachePromiseAwaitCount == N` invariant below, this deterministically
              // proves every waiter went through `ActionWait` → `pProd.await`.
              val concrete = cache.asInstanceOf[ConcurrentHashMapCache[F, String, StrongRef, Int]]
              def waitForAllParkedOnPProd(remainingPolls: Int): F[Nothing, Unit] = {
                F.flatMap(concrete.countBucketEntriesForTesting("k")) { bucketCount =>
                  val awaits = cachePromiseAwaitCount.get()
                  if (bucketCount > 1) {
                    F.sync(fail(
                      s"some waiter took ActionCompute path: bucket has $bucketCount entries " +
                        s"(expected 1 Computing, producer's). cachePromiseAwaitCount=$awaits."
                    ))
                  } else if (awaits > n) {
                    F.sync(fail(
                      s"more awaits than waiters ($awaits > $n) — bug in the instrumentation " +
                        "or an unexpected fiber awaited the cache's promise."
                    ))
                  } else if (awaits == n && bucketCount == 1) {
                    F.unit // deterministic: all N waiters entered pProd.await
                  } else if (remainingPolls <= 0) {
                    F.sync(fail(
                      s"waiters did not all park on pProd: cachePromiseAwaitCount=$awaits " +
                        s"(expected $n), bucketCount=$bucketCount (expected 1)."
                    ))
                  } else {
                    F.flatMap(Temp.sleep(10.millis))(_ => waitForAllParkedOnPProd(remainingPolls - 1))
                  }
                }
              }
              for {
                producerFib <- Fk.fork(F.attempt(cache.computeIfAbsent[String]("k", producerCompute)))
                _ <- producerStarted.await
                waiters <- F.traverse((0 until n).toList)(_ => Fk.fork(cache.computeIfAbsent[Nothing]("k", waiterCompute)))
                // Poll up to 100 × 10ms = 1s for the counter to reach N.
                _ <- waitForAllParkedOnPProd(100)
                // Let the producer fail: all N parked waiters wake to empty cache and retry.
                _ <- producerMayFail.succeed(())
                producerExit <- Temp.timeout(5.seconds)(producerFib.join)
                waiterResults <- F.traverse(waiters)(w => Temp.timeout(5.seconds)(w.join))
                totalComputes <- computeCount.get
                cached <- cache.get("k")
                // Post-run: cachePromiseAwaitCount has grown past N because retry
                // waiters now await pRetry (also cache-created). That's expected; we
                // only cared about the pre-release snapshot, which was N.
              } yield {
                assert(producerExit.contains(Left("producer-fail")), s"producer must have failed, got $producerExit")
                assert(waiterResults.forall(_.contains(42)), s"all waiters must get the retry's value (42); got $waiterResults")
                assert(cached.contains(42), s"cache must hold the retry's Ready(42); got $cached")
                // The whole point of this test — the per-waiter-fanout bug would give >1 retry.
                assert(
                  totalComputes == 2,
                  s"compute must run exactly twice (producer + one waiter's dedup'd retry); got $totalComputes. " +
                    "If this is > 2, the multi-waiter retry dedup has regressed and weak/soft caches will " +
                    "fan out compute calls per-waiter under GC pressure.",
                )
              }
            }
          }
        }
      }
    }

    "computeImpl does not orphan Computing when compute is interrupted" in run {
      // Invariant under test: after a computeImpl fiber is interrupted, no `Computing(p)`
      // entry remains in the bucket with `p` unsignaled — otherwise a subsequent caller
      // would ActionWait on that orphan promise and hang forever.
      //
      // This test pins the cleanup contract of `doCompute`'s `guaranteeOnFailure`: on
      // interrupt during compute (inside the outer `uninterruptibleExcept`'s `restore`
      // region), the cleanup removes the Computing entry AND signals the promise.
      //
      // It also serves as a safety net for the outer `uninterruptibleExcept` in
      // `computeImpl`: that mask closes a theoretical interrupt window between `modify`
      // (installing Computing) and `guaranteeOnFailure` engaging. ZIO's scheduler does
      // not typically expose that window (tight flatMap chains run in one tick), so this
      // test cannot reliably exercise the narrow-window failure mode. The mask is
      // defensive; the test validates the broader invariant "interrupt never leaves a
      // stuck Computing" across many iterations.
      //
      // Strategy: stress many fork+interrupt cycles on distinct keys and assert that a
      // follow-up `computeIfAbsent` on each key completes promptly. If any orphan remains,
      // the follow-up would hang past the 2s timeout.
      val iterations = 200
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig(initialCapacity = 16))) { cache =>
        F.flatMap(F.traverse((0 until iterations).toList) { i =>
          val key = s"k-$i"
          // Cheap compute (still has to yield somewhere for an interrupt to land).
          val compute = F.flatMap(Temp.sleep(1.milli))(_ => F.pure(i))
          for {
            fib <- Fk.fork(cache.computeIfAbsent[Nothing](key, compute))
            // Race the interrupt against `modify`/dispatch/`guaranteeOnFailure`.
            _ <- fib.interrupt
            _ <- fib.observe
            // If an orphan Computing survived the interrupt, this call hangs forever.
            retry <- Temp.timeout(2.seconds)(cache.computeIfAbsent[Nothing](key, F.pure(99)))
          } yield retry
        }) { retries =>
          F.pure {
            val hung = retries.zipWithIndex.collect { case (None, i) => i }
            assert(
              hung.isEmpty,
              s"${hung.size} keys hung on a follow-up computeIfAbsent (interrupt orphaned Computing). First 5: ${hung.take(5)}",
            )
          }
        }
      }
    }

    "invalidateAll atomic-swap: pre-call puts are cleared; concurrent puts may land on old or new structure" in run {
      // Honest contract test: invalidateAll performs an atomic structure swap
      // at one linearization point. All puts that completed BEFORE the swap
      // are guaranteed cleared (their entries were in the orphaned vector).
      // Puts that race CONCURRENTLY may land on either side of the swap
      // depending on when each put's `bucketFor` captured `structureRef`:
      //   - If `bucketFor` captured the OLD vector before the swap, the put
      //     writes to an orphaned Ref2 → invisible to post-swap readers.
      //   - If `bucketFor` captured the NEW vector after the swap, the put
      //     writes to the live Ref2 → survives.
      //
      // Strategy: pre-populate, fork invalidateAll, race many fresh puts. Assert:
      //   (a) all pre-existing entries are gone (they existed before the swap
      //       was even forked).
      //   (b) the number of concurrent-put survivors is in [0, N] — we don't
      //       assert a specific count because the race is non-deterministic.
      F.flatMap(BIOCache.make[F, Int, Int](CacheConfig(initialCapacity = 16))) { cache =>
        // Pre-populate.
        F.flatMap(F.traverse((0 until 100).toList)(i => cache.put(i, i))) { _ =>
          F.flatMap(cache.size) { sizeBefore =>
            assert(sizeBefore == 100, s"precondition: cache has 100 entries, got $sizeBefore")
            // Fork invalidateAll concurrently with fresh puts to high-numbered keys.
            F.flatMap(Fk.fork(cache.invalidateAll)) { flushFiber =>
              // Racing writes. Some may land before the per-bucket clear; some after.
              F.flatMap(F.traverse((200 until 250).toList)(i => Fk.fork(cache.put(i, -i)))) { writers =>
                F.flatMap(flushFiber.join) { _ =>
                  F.flatMap(F.traverse(writers)(_.join)) { _ =>
                    // After both complete, cache holds some subset of:
                    //   (a) the 100 pre-existing entries — all should be cleared by the
                    //       flush since writes started AFTER invalidateAll was forked.
                    //   (b) the 50 concurrent-write entries — some or all may survive.
                    F.flatMap(cache.size) { sizeAfter =>
                      F.flatMap(F.traverse((0 until 100).toList)(i => cache.get(i))) { preResults =>
                        F.map(F.traverse((200 until 250).toList)(i => cache.get(i))) { writeResults =>
                          val preSurvivors = preResults.count(_.isDefined)
                          val writeSurvivors = writeResults.count(_.isDefined)
                          assert(
                            preSurvivors == 0,
                            s"pre-existing entries must all be cleared (invalidateAll fired strictly AFTER they existed), got $preSurvivors survivors",
                          )
                          // Concurrent writes are racy — 0 to 50 may survive. We just check
                          // it's in range and documents the non-atomicity.
                          assert(writeSurvivors >= 0 && writeSurvivors <= 50, s"concurrent-write survivors in [0, 50], got $writeSurvivors")
                          assert(sizeAfter == writeSurvivors, s"size consistent with survivors, got sizeAfter=$sizeAfter vs writeSurvivors=$writeSurvivors")
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

    // ---- Atomic structure swap regressions ----

    "invalidateAll swap: parked waiter detects structure replacement and retries [atomic-swap regression]" in run {
      // Regression for the atomic-swap design: when `invalidateAll` runs while
      // a waiter is parked, the bucket `Ref2` captured at park time is swapped
      // out. On wake the waiter MUST detect the identity mismatch (`bucketFor(key)
      // ne parkedBucketRef`) and retry against the fresh (empty) structure —
      // not accept the producer's pre-flush `Some(v)`.
      //
      // Deterministic setup: an instrumented producer promise runs
      // invalidateAll + a concurrent put BEFORE signaling the waiter's promise.
      // The waiter wakes with Some(v_producer) and must observe that its
      // bucket Ref2 was replaced; retry hits put's fresh Ready.
      import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
      val promiseIndex = new AtomicInteger(0)
      val cacheRef = new AtomicReference[BIOCache[F, String, Int]](null)
      val defaultPrimsForCache: Primitives2[F] = Prims
      val instrumented: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = defaultPrimsForCache.mkRef(a)
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrimsForCache.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          val idx = promiseIndex.getAndIncrement()
          F.map(defaultPrimsForCache.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = inner.await
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def fail(e: E): F[Nothing, Boolean] = inner.fail(e)
              override def terminate(t: Throwable): F[Nothing, Boolean] = inner.terminate(t)
              override def succeed(a: A): F[Nothing, Boolean] = {
                if (idx == 0) {
                  val c = cacheRef.get()
                  if (c ne null) {
                    F.flatMap(c.invalidateAll) { _ =>
                      F.flatMap(c.put("k", 999)) { _ =>
                        inner.succeed(a)
                      }
                    }
                  } else inner.succeed(a)
                } else inner.succeed(a)
              }
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig())(
          BIO,
          instrumented,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(F.sync(cacheRef.set(cache))) { _ =>
          F.flatMap(Prims.mkLatch) { started =>
            F.flatMap(Prims.mkLatch) { mayFinish =>
              val compute: F[Nothing, Int] =
                F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 1))
              for {
                producerFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))
                _ <- started.await
                waiterFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", F.pure(-1)))
                _ <- awaitBlocked(waiterFib, 300.millis)
                _ <- mayFinish.succeed(())
                producerResult <- producerFib.join
                waiterResult <- Temp.timeout(5.seconds)(waiterFib.join)
              } yield {
                assert(producerResult == 1, s"producer returns own v (loader-result); got $producerResult")
                assert(
                  waiterResult.contains(999),
                  s"atomic-swap regression: waiter must detect bucket Ref2 identity mismatch and retry. " +
                    s"Expected Some(999) (retry hits put's Ready in the fresh structure); got $waiterResult. " +
                    "If 1 → waiter accepted producer's pre-swap v; structure-swap detection regressed.",
                )
              }
            }
          }
        }
      }
    }

    "invalidateAll swap: pre-swap producer publishes to orphan bucket; post-swap get returns None [atomic-swap regression]" in run {
      // The producer started before invalidateAll. It captures the OLD bucket
      // Ref2 at Computing-install time. After invalidateAll swaps, the producer
      // completes and publishes its Ready — but to the orphaned Ref2 that the
      // cache's structureRef no longer references. A subsequent `get` on the
      // same key must return None (the new empty structure has no such Ready).
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          F.flatMap(Prims.mkLatch) { mayFinish =>
            val compute: F[Nothing, Int] =
              F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 42))
            for {
              producerFib <- Fk.fork(cache.computeIfAbsent[Nothing]("orphan", compute))
              _ <- started.await
              // Swap the structure out from under the producer.
              _ <- cache.invalidateAll
              // Release the producer; it publishes to the orphan bucket.
              _ <- mayFinish.succeed(())
              producerResult <- producerFib.join
              // Post-swap read: must NOT see the orphan publication.
              result <- cache.get("orphan")
            } yield {
              assert(producerResult == 42, s"producer's direct caller receives own v (loader-result); got $producerResult")
              assert(
                result.isEmpty,
                s"atomic-swap regression: producer's publish landed on orphaned bucket; post-swap get must return None. Got $result.",
              )
            }
          }
        }
      }
    }

    "invalidateAll + concurrent computeIfAbsent: no orphan-admission hangs [Codex HIGH regression]" in run {
      // Codex HIGH regression: a caller that captured oldVec pre-swap could
      // install Computing on the now-orphaned bucket. Future callers on the
      // same oldVec (rare, but possible with stashed references) would park
      // on a promise that no public op can signal — hang.
      //
      // The fix: admission modify checks `bucketRef ne bucketFor(key)`. If
      // orphan, aborts with ActionSwapped and retries from the top against
      // the fresh structure.
      //
      // Stress: 100 concurrent computeIfAbsent calls racing 50 invalidateAll
      // calls. If any admission hangs, the test times out.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig(initialCapacity = 8))) { cache =>
        F.flatMap(Fk.fork(F.tailRecM[Nothing, Int, Int](0) { i =>
          if (i >= 50) F.pure(Right(i))
          else F.map(F.*>(cache.invalidateAll, Temp.sleep(1.milli)))(_ => Left(i + 1))
        })) { invalidatorFib =>
          F.flatMap(F.traverse((0 until 100).toList)(i => Fk.fork(cache.computeIfAbsent[Nothing](s"k$i", F.pure(i))))) { computeFibs =>
            F.flatMap(invalidatorFib.join) { _ =>
              F.flatMap(Temp.timeout(10.seconds)(F.traverse(computeFibs)(_.join))) { results =>
                F.pure {
                  assert(
                    results.exists(r => r.size == 100),
                    s"admission hang regression: expected all 100 computeIfAbsent calls to complete; got ${results.map(_.size)}. " +
                      "Timeout → a caller parked on an orphan-bucket promise.",
                  )
                  assert(
                    results.exists(_.forall(_ >= 0)),
                    s"expected all results to be the requested i; got $results",
                  )
                }
              }
            }
          }
        }
      }
    }

    "close + concurrent computeIfAbsent: no orphan-admission hangs [Codex HIGH regression]" in run {
      // Same orphan-admission concern, but for close instead of invalidateAll.
      // A caller that captured oldVec pre-close could install Computing on
      // orphan after close's swap. Fix: admission modify checks BOTH
      // `closedFlag` and `bucketRef ne bucketFor(key)`. Either aborts.
      //
      // Stress: spawn many concurrent computeIfAbsent calls, call close. All
      // computeIfAbsent calls must terminate (succeed or fail) — NOT hang.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig(initialCapacity = 8))) { cache =>
        F.flatMap(F.traverse((0 until 50).toList)(i => Fk.fork(F.sandboxExit(cache.computeIfAbsent[Nothing](s"k$i", F.pure(i)))))) { computeFibs =>
          F.flatMap(cache.close) { _ =>
            F.flatMap(Temp.timeout(10.seconds)(F.traverse(computeFibs)(_.join))) { results =>
              F.pure {
                assert(
                  results.exists(_.size == 50),
                  s"close admission-hang regression: expected all 50 computeIfAbsent calls to complete (succeed or fail); got ${results.map(_.size)}. " +
                    "Timeout → a caller parked on an orphan-bucket promise post-close.",
                )
              }
            }
          }
        }
      }
    }

    "publish modify orphan check: pre-swap producer's Ready does NOT land on live structure [Codex HIGH regression]" in run {
      // Codex HIGH regression: before the orphan-check fix in doCompute's
      // publish modify, a producer whose bucketRef was captured pre-swap
      // could publish Ready on the orphan bucket. That Ready was invisible
      // to post-swap readers (good), but IF the publish's CAS serialized
      // after drain and the orphan bucket were somehow consulted by a
      // later caller, stale data could leak.
      //
      // Deterministic test: start a producer, invalidateAll, release the
      // producer. Its publish modify runs on orphan bucketRef, sees
      // `bucketRef ne bucketFor(key)`, and skips publication. A subsequent
      // `get(key)` returns None.
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(Prims.mkLatch) { started =>
          F.flatMap(Prims.mkLatch) { mayFinish =>
            val compute: F[Nothing, Int] =
              F.flatMap(started.succeed(()))(_ => F.map(mayFinish.await)(_ => 42))
            for {
              producerFib <- Fk.fork(cache.computeIfAbsent[Nothing]("k", compute))
              _ <- started.await
              // Swap the structure out from under the in-flight producer.
              _ <- cache.invalidateAll
              // Release the producer; its publish modify MUST see orphan
              // and skip publication.
              _ <- mayFinish.succeed(())
              producerResult <- producerFib.join
              // Post-swap read: the fresh empty structure has nothing.
              afterGet <- cache.get("k")
            } yield {
              assert(producerResult == 42, s"producer's direct caller receives own v (loader-result); got $producerResult")
              assert(
                afterGet.isEmpty,
                s"publish-orphan-check regression: producer's Ready must NOT be visible via the live structure post-swap. Got $afterGet",
              )
            }
          }
        }
      }
    }

    "close fence: no compute invocation starts AFTER close has returned [Codex HIGH regression]" in run {
      // Directly targets the post-CAS admission/close race: a caller whose
      // in-closure guards (`closedFlag`, `bucketFor(key)`) both evaluated to
      // "open" before `close` flipped them can still win the old-bucket CAS
      // afterward and call `compute`. The fix is a post-CAS re-check in
      // `computeImpl` that rolls back the `Computing` and fails-fast with
      // `IllegalStateException` if either fence has moved.
      //
      // Invariant: once `close` returns, NO further `compute` invocation may
      // start. We flip an external `AtomicBoolean` immediately after `close`
      // returns; any `compute` function that observes it true recorded a
      // violation. The test is stressed across many iterations with many
      // concurrent admissions so the interleaving is forced to materialize.
      val iterations = 80
      val concurrentCalls = 64
      F.map(
        F.traverse((0 until iterations).toList) { _ =>
          F.flatMap(BIOCache.make[F, String, Int](CacheConfig(initialCapacity = 8))) { cache =>
            val closeReturnedFlag = new java.util.concurrent.atomic.AtomicBoolean(false)
            val violationCount = new java.util.concurrent.atomic.AtomicInteger(0)
            val compute: F[Nothing, Int] = F.sync {
              if (closeReturnedFlag.get()) { val _ = violationCount.incrementAndGet() }
              1
            }
            F.flatMap(Prims.mkLatch) { startBarrier =>
              F.flatMap(F.traverse((0 until concurrentCalls).toList) { i =>
                Fk.fork(F.flatMap(startBarrier.await)(_ => F.sandboxExit(cache.computeIfAbsent[Nothing](s"k$i", compute))))
              }) { fibs =>
                F.flatMap(startBarrier.succeed(())) { _ =>
                  F.flatMap(cache.close) { _ =>
                    F.flatMap(F.sync(closeReturnedFlag.set(true))) { _ =>
                      F.map(F.traverse(fibs)(_.join))(_ => violationCount.get())
                    }
                  }
                }
              }
            }
          }
        }
      ) { perIterationViolations =>
        val total = perIterationViolations.sum
        assert(
          total == 0,
          s"close fence regression: $total compute invocations started AFTER close returned. Per-iteration non-zero: " +
            perIterationViolations.zipWithIndex.filter(_._1 != 0).map { case (n, i) => s"iter$i=$n" }.mkString(", "),
        )
      }
    }

    "concurrent close callers all return only after teardown is complete [Codex HIGH regression]" in run {
      // Stop-hook finding: `closedFlag.getAndSet(true)` returns the previous
      // value; the loser (observed `true`) previously returned F.unit
      // IMMEDIATELY while the winner was still interrupting the eviction
      // fiber, swapping structureRef, and draining promises. That breaks the
      // contract "once close returns, teardown is complete": a loser that
      // forked closure on close's return could observe a not-yet-closed
      // cache (e.g., structureRef still pointing at the pre-swap vector).
      //
      // Fix: shared CAS-installed `closedPromise`. Winner signals it after
      // the drain; losers await it.
      //
      // Property this test pins: after ANY `close` call returns, the atomic
      // structure swap has been observed. If a loser returned early
      // (pre-swap), a subsequent `get` on a pre-populated key would still
      // see its Ready. With the fix, losers block on the winner's post-drain
      // signal, so `get` is guaranteed to observe the fresh empty vector.
      //
      // Asserting ONLY on post-close computeIfAbsent defecting is NOT
      // sufficient: the winner flips `closedFlag` BEFORE teardown, so even
      // in the broken version, admission would defect on ActionClosed. We
      // have to observe post-swap state (pre-populated entries gone) to
      // actually verify teardown completion.
      val entriesToPopulate = 32
      val concurrentClosers = 16
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig(initialCapacity = 8))) { cache =>
        F.flatMap(F.traverse((0 until entriesToPopulate).toList)(i => cache.put(s"k$i", i))) { _ =>
          F.flatMap(Prims.mkLatch) { startBarrier =>
            F.flatMap(F.traverse((0 until concurrentClosers).toList) { _ =>
              Fk.fork(F.flatMap(startBarrier.await) { _ =>
                F.flatMap(cache.close) { _ =>
                  // Every pre-populated key MUST be gone after our close
                  // returns. Any `Some(_)` is proof the loser returned
                  // before the winner's `structureRef.getAndSet(emptyVec)`
                  // was observable — teardown wasn't complete.
                  F.map(F.traverse((0 until entriesToPopulate).toList)(i => cache.get(s"k$i"))) { results =>
                    results.count(_.isDefined)
                  }
                }
              })
            }) { fibs =>
              F.flatMap(startBarrier.succeed(())) { _ =>
                F.map(F.traverse(fibs)(_.join)) { perCallerLeaks =>
                  val totalLeaks = perCallerLeaks.sum
                  assert(
                    totalLeaks == 0,
                    s"concurrent-close ordering regression: $totalLeaks of ${concurrentClosers * entriesToPopulate} post-close `get` reads saw a pre-close Ready. " +
                      s"Per-caller leak counts: $perCallerLeaks. " +
                      "This means at least one `close` returned BEFORE the winner's structureRef swap was observable — " +
                      "violating the contract that teardown is complete when close returns.",
                  )
                }
              }
            }
          }
        }
      }
    }

    "concurrent close: losers observe the SAME defect as the winner [Codex HIGH regression]" in run {
      // Stop-hook finding: `winnerPromise.terminate(f.trace.toThrowable)`
      // would collapse a structured `Exit.Failure` (multi-defect
      // Termination or interrupt trace) into a single `Die(throwable)`
      // — losers' `await` would re-raise a DIFFERENT cause than the
      // winner's own `F.fromSandboxExit(exit)`. Fix: encode the full
      // `Exit.Uninterrupted[Nothing, Unit]` as the promise payload;
      // losers replay it identically via `fromSandboxExit`.
      //
      // To actually PROVE the invariant (not just the observable
      // consequence), this test uses an instrumented `Primitives2`
      // that wraps every `Promise2` to count `succeed` / `terminate`
      // / `await` calls. The fixed code path — Exit-valued promise
      // with `succeed(exit)` + shared `fromSandboxExit` — must exhibit
      // EXACTLY one `succeed` call, ZERO `terminate` calls, and
      // (concurrentClosers - 1) `await` calls. An output-only eq
      // check on the re-raised throwable would ALSO pass under the
      // broken `Promise2.terminate(throwable)` path (since that path
      // still re-raises the same sentinel singleton) — the structural
      // counts are what distinguish the two implementations.
      //
      // We also force a teardown defect by instrumenting `mkRef` to
      // terminate on the first `P.mkRef` call made by `freshBuckets`
      // inside the winner's `close`. All 8 concurrent `close` callers
      // — winner included — must observe a failure exit via
      // `F.sandboxExit`. A caller returning `Success` would prove the
      // previous `F.guarantee(..., succeed(()))` masking had returned.
      import java.util.concurrent.atomic.AtomicInteger
      val capacity = 8
      val concurrentClosers = 8
      // Construction uses `capacity` mkRefs for buckets + 1 for fiberRef = capacity + 1.
      // Winner's `close` then calls `freshBuckets` which runs `capacity` more mkRefs.
      // Failing on the FIRST of those (index = capacity + 1) reliably defects the
      // winner's teardown at a deterministic point.
      val failAtRefIndex = capacity + 1
      val refCounter = new AtomicInteger(0)
      val sentinelDefect = new RuntimeException("close-teardown sentinel defect")
      // Promise usage counters + payload capture. Only `close`'s
      // candidate promises are allocated in this test (no
      // `computeIfAbsent` calls → no producer promises), so every
      // counted call is attributable to the close serialization path.
      //
      // `succeededExitRef` captures the exact `Exit` INSTANCE that the
      // winner passes to `Promise2.succeed(exit)`. `awaitedExits`
      // collects the EXACT instances that each loser receives from
      // `Promise2.await`. Asserting they are `eq` proves that what
      // winner publishes is what losers replay — closing the
      // Exit-valued promise plumbing loop structurally.
      import java.util.concurrent.ConcurrentLinkedQueue
      val promiseSucceedCount = new AtomicInteger(0)
      val promiseTerminateCount = new AtomicInteger(0)
      val promiseFailCount = new AtomicInteger(0)
      val promiseAwaitCount = new AtomicInteger(0)
      val succeededExitRef = new java.util.concurrent.atomic.AtomicReference[AnyRef](null)
      val awaitedExits = new ConcurrentLinkedQueue[AnyRef]()
      val defaultPrims: Primitives2[F] = Prims
      val instrumented: Primitives2[F] = new Primitives2[F] {
        override def mkRef[A](a: A): F[Nothing, Ref2[F, A]] = {
          val idx = refCounter.getAndIncrement()
          if (idx == failAtRefIndex) F.terminate(sentinelDefect)
          else defaultPrims.mkRef(a)
        }
        override def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]] = defaultPrims.mkSemaphore(permits)
        override def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]] = {
          F.map(defaultPrims.mkPromise[E, A]) { inner =>
            new Promise2[F, E, A] {
              override def await: F[E, A] = {
                F.flatMap(F.sync(promiseAwaitCount.incrementAndGet())) { _ =>
                  F.map(inner.await) { a =>
                    awaitedExits.add(a.asInstanceOf[AnyRef])
                    a
                  }
                }
              }
              override def poll: F[Nothing, Option[F[E, A]]] = inner.poll
              override def succeed(a: A): F[Nothing, Boolean] = {
                F.flatMap(F.sync {
                  // Only the FIRST successful succeed's payload is
                  // semantically "the exit losers will see". Later
                  // calls (if any) are Promise2 no-ops. Capture the
                  // first via CAS.
                  succeededExitRef.compareAndSet(null, a.asInstanceOf[AnyRef])
                  promiseSucceedCount.incrementAndGet()
                })(_ => inner.succeed(a))
              }
              override def fail(e: E): F[Nothing, Boolean] = F.flatMap(F.sync(promiseFailCount.incrementAndGet()))(_ => inner.fail(e))
              override def terminate(t: Throwable): F[Nothing, Boolean] = F.flatMap(F.sync(promiseTerminateCount.incrementAndGet()))(_ => inner.terminate(t))
            }
          }
        }
      }
      val cacheF: F[Nothing, BIOCache[F, String, Int]] =
        ConcurrentHashMapCache.create[F, String, StrongRef, Int](CacheConfig(initialCapacity = capacity))(
          BIO,
          instrumented,
          implicitly[CacheRefType[StrongRef]],
        )
      F.flatMap(cacheF) { cache =>
        F.flatMap(Prims.mkLatch) { startBarrier =>
          F.flatMap(F.traverse((0 until concurrentClosers).toList) { _ =>
            Fk.fork(F.flatMap(startBarrier.await)(_ => F.sandboxExit(cache.close)))
          }) { fibs =>
            F.flatMap(startBarrier.succeed(())) { _ =>
              F.map(F.traverse(fibs)(_.join)) { exits =>
                val successes = exits.count {
                  case Exit.Success(_) => true
                  case _ => false
                }
                val terminations = exits.collect { case t: Exit.Termination => t }
                val observedThrowables = terminations.map(_.compoundException)
                val allMatchSentinel = observedThrowables.forall(_ eq sentinelDefect)
                assert(
                  successes == 0 && terminations.size == concurrentClosers,
                  s"defect-propagation regression (existence): winner's teardown defected but $successes of $concurrentClosers close callers observed SUCCESS (expected 0 successes + $concurrentClosers terminations, got $successes successes + ${terminations.size} terminations). " +
                    "A success on any loser would mean the defect was masked — the promise payload lost fidelity or signaling branched on success-only.",
                )
                assert(
                  allMatchSentinel,
                  s"defect-propagation regression (identity): expected every close caller's observable throwable to be the SAME sentinel defect (via `eq`); got ${observedThrowables.size} throwables of which ${observedThrowables.count(_ eq sentinelDefect)} matched.",
                )
                // Structural invariant checks — what actually pins
                // the Exit-valued promise replay path. Three layers:
                //
                // 1. Call-count check. Fixed path: exactly 1 succeed,
                //    0 fail/terminate, and `concurrentClosers` awaits
                //    — winner re-raises via the SAME `await` +
                //    `fromSandboxExit` path as losers, so every caller
                //    awaits the promise exactly once. A regression to
                //    `Promise2.terminate(throwable)` would give
                //    terminate > 0 or succeed < 1. A regression where
                //    winner re-raised from a local exit variable
                //    would give await count == losers, not all
                //    callers — catching the winner-path divergence
                //    regression Codex flagged.
                //
                // 2. Payload-identity check. The EXIT instance passed
                //    to `Promise2.succeed(exit)` must be byte-for-byte
                //    the same object that every `await` returns —
                //    for BOTH winner and losers. This proves the
                //    promise is the SOLE data channel, not a
                //    coordination signal layered on top of a local
                //    re-raise.
                //
                // 3. Observable-effect identity (already asserted):
                //    every caller's re-raised throwable is `eq` to
                //    the sentinel.
                //
                // A regression in any of these three layers fails
                // this test. Combined they structurally pin the
                // `succeed(exit)` + unified `await + fromSandboxExit`
                // replay path. Routing winner through `await` makes
                // the published Exit the SOLE source of every
                // caller's terminal effect — no alternate code path
                // can reproduce it.
                val expectedAwaits = concurrentClosers
                assert(
                  promiseSucceedCount.get() == 1,
                  s"replay-path regression (succeed count): expected EXACTLY 1 Promise2.succeed call (winner signaling the raw Exit); got ${promiseSucceedCount.get()}. " +
                    "If 0, the winner is not publishing the exit; if >1, CAS-install is not uniquely serializing.",
                )
                assert(
                  promiseTerminateCount.get() == 0 && promiseFailCount.get() == 0,
                  s"replay-path regression (channel): expected ZERO Promise2.terminate/fail calls (the fix routes ALL teardown results through succeed(exit) + fromSandboxExit); got terminate=${promiseTerminateCount.get()}, fail=${promiseFailCount.get()}. " +
                    "Any nonzero count proves the broken `Promise2.terminate(throwable)` signaling path has regressed.",
                )
                assert(
                  promiseAwaitCount.get() == expectedAwaits,
                  s"replay-path regression (await count): expected $expectedAwaits Promise2.await calls (one per close caller, INCLUDING the winner); got ${promiseAwaitCount.get()}. " +
                    "A count < concurrentClosers proves the winner re-raises from a local exit variable rather than the shared promise — the winner-path replay divergence regression.",
                )
                val publishedExit = succeededExitRef.get()
                assert(
                  publishedExit != null,
                  "replay-path regression (payload capture): winner did not publish any Exit via succeed; succeededExitRef is null.",
                )
                import scala.jdk.CollectionConverters.*
                val awaitedSnapshot = awaitedExits.iterator().asScala.toList
                val allEqToPublished = awaitedSnapshot.forall(_ eq publishedExit)
                assert(
                  awaitedSnapshot.size == expectedAwaits && allEqToPublished,
                  s"replay-path regression (payload identity): expected every loser's Promise2.await return value to be the SAME instance winner passed to Promise2.succeed; got ${awaitedSnapshot.size} await returns of which ${awaitedSnapshot.count(_ eq publishedExit)} were `eq` to the published exit. " +
                    "A per-caller synthesized Exit would trip this check even when counts match.",
                )
              }
            }
          }
        }
      }
    }

    "close swap: post-close computeIfAbsent admission defects with IllegalStateException [atomic-swap regression]" in run {
      // After close, closedFlag is true. Any new computeIfAbsent reads it inside
      // the admission modify and returns ActionClosed. The fiber terminates with
      // IllegalStateException via F.terminate (caught by sandboxExit as a defect).
      F.flatMap(BIOCache.make[F, String, Int](CacheConfig())) { cache =>
        F.flatMap(cache.close) { _ =>
          F.flatMap(F.sandboxExit(cache.computeIfAbsent[Nothing]("k", F.pure(1)))) { exit =>
            F.pure {
              val failed = exit match {
                case Exit.Success(_) => false
                case _: Exit.Failure[?] => true
              }
              assert(
                failed,
                s"post-close computeIfAbsent must defect (IllegalStateException). Got success: $exit",
              )
            }
          }
        }
      }
    }
  }
}
