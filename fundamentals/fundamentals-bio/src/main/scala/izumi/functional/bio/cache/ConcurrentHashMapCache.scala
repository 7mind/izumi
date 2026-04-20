package izumi.functional.bio.cache

import izumi.functional.bio.*
import izumi.functional.bio.data.RestoreInterruption2

import scala.concurrent.duration.FiniteDuration

/** Lock-free concurrent hash map cache with Guava-style [[BIOCache#computeIfAbsent]].
  *
  * Uses an array of [[Ref2]] buckets. Each bucket holds an immutable list of entries.
  * Modifications are atomic via [[Ref2#modify]] (CAS with optimistic retry on conflict).
  * Operations on different buckets have zero contention.
  *
  * Concurrent `computeIfAbsent` calls for the same key are deduplicated by a
  * [[Promise2]] per `Computing` entry. The promise carries `Option[V]` (the raw,
  * unwrapped value): `Some(v)` on success (the producer hands its computed value
  * directly to parked waiters) and `None` on failure/interrupt. Routing the value
  * through the promise avoids races between producer signal and waiter read — in
  * particular the eager-eviction fiber cannot sweep the bucket out from under the
  * waiter.
  *
  * GC-eligibility for `WeakCacheRef`/`SoftCacheRef`: the promise does hold `Some(v)`
  * strongly, but the promise is NOT referenced from any long-lived cache state —
  * `Ready.origin` holds a separate lightweight `Object` token, not the promise. The
  * promise is therefore GC-eligible as soon as the producer fiber and all parked
  * waiters release their local references (typically microseconds). V is pinned only
  * during that short window; beyond that, the cache's weak/soft wrapper in
  * `Ready.stored` is the only path to V, so the cache-level GC contract is preserved.
  *
  * ==At-most-once compute per caller==
  *
  * `compute` runs AT MOST ONCE per `computeIfAbsent` call. Control-plane operations
  * (`put`/`invalidate`/`invalidateAll`) do NOT interrupt an in-flight producer fiber,
  * but they DO release the producer's parked waiters by signaling the displaced
  * Computing's promise with `None` in the same uninterruptible step as the bucket
  * mutation. Producer fibers run to completion on their own; their direct callers
  * receive their own `v` (loader-result contract). Across multiple independent
  * `computeIfAbsent` calls on the same key racing with `invalidate`, each call's
  * `compute` still runs at most once but total compute invocations across callers
  * can grow with missing-cache retries after the invalidate.
  *
  * ==Waiter wake-up semantics==
  *
  * A waiter wakes when EITHER the producing fiber signals its promise (on exit)
  * OR a control-plane op on the same key signals the displaced promise. Control-plane
  * release is always via `None`, so waiters retry through `computeImpl` and observe
  * the freshest available cache state. This guarantees no waiter can be wedged by a
  * hung producer — any control-plane operation (including `close`) unblocks it.
  *
  * ==Producer publication (Guava semantics)==
  *
  * When a producer finishes `compute`, it publishes `Ready(v)` only if its OWN
  * `Computing` marker is still in the slot. Any other state — empty slot (our
  * Computing was removed by `invalidate`/`invalidateAll`/`close` or by a
  * successor that has since terminated), foreign `Computing` (a successor owns
  * the slot), or any `Ready` (from `put` or a newer compute) — blocks publication.
  * This is a strict freshness barrier: pre-invalidate loads cannot repopulate the
  * cache past an explicit refresh boundary, even transiently. Cost: if `invalidate`
  * fires mid-compute with no successor, the producer's successful load is NOT
  * cached and the next caller recomputes.
  *
  * '''The producer always returns its own computed `v`''' and signals parked waiters
  * with `Some(v)`, regardless of the publication outcome. This preserves Guava's
  * loader-result semantics: callers that invoked `compute` receive the value `compute`
  * produced, even if the cache state diverges. Critical for side-effecting loaders
  * (resource allocation, external records) that couple the loader's result to
  * lifecycle management — swapping in a racing put's value would orphan the producer's
  * computed result.
  *
  * Publishing after racing invalidation is a deliberate Guava-alignment choice: a
  * successful load must not be silently dropped, or the next caller would recompute
  * and duplicate side effects.
  *
  * ==Waiter wake-and-retry==
  *
  * When a waiter wakes (producer signaled), it reads the promise payload — NOT the
  * cache. Bypassing the cache means the waiter's handoff is immune to bucket mutations
  * that can race with the wake: short/zero TTLs filtering Ready via `cleanBucket`,
  * the eager-eviction fiber sweeping expired entries, or a racing invalidate/put
  * clearing or replacing the bucket post-publish.
  *
  *   - `Some(v)` → return v directly (no bucket read, no wrapper unwrap).
  *   - `None` (producer failed, was interrupted, or defected) → retry via `computeImpl`.
  *     Across N waiters waking with `None`, the retry's bucket CAS dedups them:
  *     exactly ONE installs a new `Computing` and runs its compute; the others park.
  *     Total compute invocations across the N waiters = `1`, not `N`.
  *
  * Note: under weak/soft cache ref types the promise still delivers `Some(v)` on
  * success — dedup is never broken by GC pressure, because the promise holds V
  * strongly for its (short) lifetime. The cache-level wrapper in `Ready.stored` is
  * what enables GC-reclaim for V after waiters release their references.
  *
  * ==Waiter freshness barriers==
  *
  *   - Cache-wide `closedFlag` (AtomicBoolean): flipped by [[close]] BEFORE
  *     the structure swap. Checked by every admission path in `computeImpl`
  *     and by waiters on wake. Monotonic — once `true`, stays `true`.
  *   - Structure swap (atomic `structureRef` replacement by [[invalidateAll]]
  *     / [[close]]): each swap installs a fresh Vector of fresh per-bucket
  *     `Ref2`s. Waiters capture their bucket's `Ref2` identity at park time
  *     and compare on wake via `eq` — a mismatch means the structure was
  *     replaced, forcing a retry.
  *
  * Per-key writes (`put(k)` / `invalidate(k)`) displace in-flight `Computing`
  * markers by signaling their promise `None` from inside the same modify,
  * so parked waiters wake and retry. They do NOT impose a post-wake retry
  * on already-succeeded waiters: if the producer reaches `succeed(Some(v))`
  * before the `put`/`invalidate`, the waiter returns `v` (Guava loader-result
  * dedup). `put`'s newer value is observed by subsequent `computeIfAbsent`
  * / `get` calls, not by already-parked waiters.
  *
  * Benign events (TTL expiry, weak/soft GC cleanup, successor compute-publish)
  * do NOT swap the structure. Already-deduped waiters whose producer succeeded
  * receive its `Some(v)` — Guava's loader-result dedup is preserved.
  *
  * ==Atomic structure swap for [[invalidateAll]] and [[close]]==
  *
  * Both operations are O(1)-visible: a single `structureRef.getAndSet`
  * replaces the entire bucket vector. After the swap:
  *   - New `currentBuckets` reads see the fresh (empty) vector.
  *   - In-flight ops that captured the OLD vector pre-swap continue on
  *     orphaned `Ref2`s; their effects are invisible to post-swap readers.
  *     Matches Java `ConcurrentHashMap.clear` semantics for races between
  *     `clear()` and concurrent reads/writes.
  *   - Parked waiters on old-vector `Computing` promises are woken with
  *     `None` via the post-swap drain step; they retry in `computeImpl`
  *     against the fresh vector (or fail fast if `closedFlag` is set).
  *
  * For a globally-atomic flush that also serializes against in-flight ops,
  * construct a new cache.
  */
private[bio] final class ConcurrentHashMapCache[F[+_, +_], K, R[_], V](
  initialBuckets: Vector[Ref2[F, BucketState[K, R[V]]]],
  config: CacheConfig,
  evictionFiberRef: Ref2[F, Option[Fiber2[F, Nothing, Unit]]],
)(implicit
  F: IO2[F],
  P: Primitives2[F],
  R: CacheRefType[R],
) extends BIOCache[F, K, V] {

  // Linearization point for concurrent `close` callers. The first caller to
  // CAS a non-null promise here becomes the unique teardown winner — it also
  // flips `closedFlag` and runs the interrupt / swap / drain steps, then
  // signals the promise on completion. Subsequent callers observe the
  // installed promise and `await` it, so once ANY `close` returns, teardown
  // is observably complete.
  //
  // Payload is the RAW `Exit.Uninterrupted[Nothing, Unit]` of the winner's
  // sandboxed teardown. Winner completes with `Exit.Success(())` on success
  // or the original `Exit.Termination(compoundException, allExceptions,
  // trace)` on defect. Both winner and losers replay the SAME exit via
  // `F.fromSandboxExit(exit)`. The invariant is CONSISTENCY, not max
  // fidelity: whatever `F.fromSandboxExit` collapses (the default IO2 impl
  // passes only `compoundException` to `terminate`, dropping
  // `allExceptions` and `trace`), it collapses identically for every
  // caller. Winner and losers therefore re-raise identical observable
  // throwables.
  //
  // The alternative of signaling losers via `Promise2.terminate(throwable)`
  // would break consistency: losers would re-raise a DIFFERENT cause than
  // the winner's own `F.fromSandboxExit(exit)`, because losers would go
  // through `await`'s `terminate`-path while the winner goes through
  // `fromSandboxExit`'s own `terminate(compoundException)` path. By
  // publishing the whole Exit and forcing both paths through
  // `F.fromSandboxExit`, we get a single canonical collapse.
  //
  // Created LAZILY (inside `close` via `P.mkPromise`, not at construction)
  // so (a) caches that are never closed don't allocate a promise, and
  // (b) the ordering of Primitives2.mkPromise calls observed by
  // test instrumentation is not perturbed by an unconditional init-time
  // promise allocation.
  private[this] val closedPromiseRef: java.util.concurrent.atomic.AtomicReference[Promise2[F, Nothing, Exit.Uninterrupted[Nothing, Unit]]] =
    new java.util.concurrent.atomic.AtomicReference(null)

  // ==Atomic swap semantics==
  //
  // The bucket structure is NOT a fixed `val Vector[Ref2]`. It lives in an
  // `AtomicReference` that `invalidateAll` / `close` atomically replace with a
  // fresh Vector of fresh per-bucket `Ref2`s. After the swap CAS:
  //   - Every new `currentBuckets` read returns the fresh vector.
  //   - In-flight operations that captured the OLD vector (via `currentBuckets`
  //     or `bucketFor(key)`) continue on the orphaned old `Ref2`s. Their
  //     effects are invisible to post-swap readers, which matches Guava /
  //     ConcurrentHashMap semantics for races between clear() and concurrent
  //     reads/writes.
  //   - Parked waiters detect the swap by comparing their captured bucket
  //     `Ref2` identity to the current `bucketFor(key)` at wake time (see
  //     `WaitCtx.parkedBucketRef` and `awaitAndRetry`). A mismatch forces a
  //     retry against the fresh structure.
  //
  // This replaces the previous per-bucket traversal design: `invalidateAll`
  // and `close` are now O(1) visible (one CAS), and subsequent bucket reads
  // see the fresh structure immediately.
  private[this] val structureRef: java.util.concurrent.atomic.AtomicReference[Vector[Ref2[F, BucketState[K, R[V]]]]] =
    new java.util.concurrent.atomic.AtomicReference(initialBuckets)

  // Cache-wide close fence. Flipped `true` atomically at the START of `close`,
  // BEFORE the structureRef swap. Read inside every admission-path bucket
  // modify closure in `computeImpl` so that no new `Computing` is installed
  // after close begins — even on buckets the old vector still exposes to an
  // in-flight caller that captured it pre-swap.
  //
  // Monotonic: once flipped true, stays true. `invalidateAll` does NOT touch
  // this flag (invalidateAll does not terminate the cache).
  private[this] val closedFlag: java.util.concurrent.atomic.AtomicBoolean =
    new java.util.concurrent.atomic.AtomicBoolean(false)

  // Freshness-fence design:
  //
  //   - Structure swap (via `structureRef.getAndSet`): global barrier for
  //     `invalidateAll` / `close`. Waiters detect the swap by bucket-`Ref2`
  //     identity mismatch at wake time.
  //
  //   - `Computing.originToken` / `Ready.origin`: per-producer identity used
  //     ONLY by `doCompute`'s own cleanup (match our own Computing/Ready on
  //     failure rollback). NOT part of the waiter freshness check.
  //
  // Per-key writes (`put` / `invalidate`) collect any displaced `Computing`
  // promise inside their bucket modify and signal it `None` post-commit;
  // parked waiters wake and retry via `computeImpl`. Once a waiter's
  // producer has already signaled `Some(v)`, the waiter keeps `v` —
  // consistent with Guava's loader-result dedup contract. `invalidateAll`
  // / `close` drain the OLD vector's promises after the swap CAS; waiters
  // retry and observe the swap via `Ref2` identity mismatch.
  //
  // Benign cleanup (TTL expiry, weak/soft GC, successor compute-publish)
  // does NOT swap, so already-deduped waiters are never hijacked.

  private[this] def currentBuckets: Vector[Ref2[F, BucketState[K, R[V]]]] =
    structureRef.get()

  private[this] def bucketFor(key: K): Ref2[F, BucketState[K, R[V]]] = {
    val vec = currentBuckets
    val h = key.hashCode()
    val spread = h ^ (h >>> 16) // spread high bits (from ConcurrentHashMap)
    vec(Math.floorMod(spread, vec.size))
  }

  private[this] val initialCapacity: Int = Math.max(1, config.initialCapacity)

  private[this] def isExpired(expiresAtNano: Long, nowNano: Long): Boolean = {
    expiresAtNano != Long.MaxValue && (nowNano - expiresAtNano) >= 0
  }

  private[this] def isValid(entry: BucketEntry[K, R[V]], nowNano: Long): Boolean = entry match {
    case Ready(_, stored, expiresAtNano, _) =>
      !isExpired(expiresAtNano, nowNano) && R.get(stored).isDefined
    case _: Computing[_, _] => true
  }

  private[this] def cleanBucket(entries: List[BucketEntry[K, R[V]]], nowNano: Long): List[BucketEntry[K, R[V]]] = {
    entries.filter(isValid(_, nowNano))
  }

  private[this] def computeExpiry(nowNano: Long, ttlOverride: Option[FiniteDuration]): Long = {
    ttlOverride.orElse(config.defaultTTL) match {
      case Some(ttl) => nowNano + ttl.toNanos
      case None => Long.MaxValue
    }
  }

  override def get(key: K): F[Nothing, Option[V]] = {
    bucketFor(key).modify { state =>
      // Read clock INSIDE the modify closure so CAS-retries see a fresh
      // timestamp for TTL filtering. Capturing `nowNano` outside would let
      // a Ready that expired during the retry still appear live.
      val nowNano = System.nanoTime()
      val cleaned = cleanBucket(state.entries, nowNano)
      cleaned.find(_.key == key) match {
        case Some(Ready(_, stored, _, _)) =>
          R.get(stored) match {
            case s @ Some(_) => (s, BucketState(cleaned))
            case None =>
              (None, BucketState(cleaned.filterNot(_.key == key)))
          }
        case _ => (None, BucketState(cleaned))
      }
    }
  }

  override def put(key: K, value: V): F[Nothing, Option[V]] = putImpl(key, value, None)

  override def putWithTTL(key: K, value: V, ttl: FiniteDuration): F[Nothing, Option[V]] = putImpl(key, value, Some(ttl))

  /** Collect `Computing` promises from displaced entries. Signaled post-commit
    * with `None` via `Promise2.succeed` (itself CAS-atomic) so parked waiters
    * wake and retry via `computeImpl`. `Ready` entries do NOT carry a promise
    * reference — `put`/`invalidate` don't need to signal Ready entries, only
    * displace in-flight `Computing`s. Global barriers (`invalidateAll`/`close`)
    * are detected via the bucket `Ref2` identity mismatch from the structure
    * swap; no per-key counter is needed.
    */
  private[this] def collectDisplacedSignals(
    entries: List[BucketEntry[K, R[V]]],
    keyFilter: K => Boolean,
  ): List[Promise2[F, Nothing, Option[V]]] = {
    entries.flatMap {
      case Computing(k, p, _) if keyFilter(k) =>
        List(p.asInstanceOf[Promise2[F, Nothing, Option[V]]])
      case _ => Nil
    }
  }

  private[this] def putImpl(key: K, value: V, ttl: Option[FiniteDuration]): F[Nothing, Option[V]] = {
    // Full-close: post-close puts are silent no-ops (return None).
    //
    // Otherwise: install our Ready, capture the previous live value (if any),
    // collect any displaced Computing's promise — all in ONE atomic modify.
    // Signal collected promises `None` post-commit so parked waiters wake and
    // retry. Fully uninterruptible.
    F.uninterruptible(
      F.flatMap(bucketFor(key).modify { state =>
        if (closedFlag.get()) {
          ((None: Option[V], Nil: List[Promise2[F, Nothing, Option[V]]]), state)
        } else {
          // Read clock INSIDE modify so CAS retries see a fresh timestamp.
          val nowNano = System.nanoTime()
          val cleaned = cleanBucket(state.entries, nowNano)
          // Previous live value at commit time: only a live, non-expired
          // `Ready` counts. Expired entries have been removed by `cleanBucket`
          // above; an in-flight `Computing` yields no previous value; a
          // weak/soft `Ready` whose wrapper has been GC-reclaimed yields None.
          val previous: Option[V] = cleaned.collectFirst {
            case Ready(k, storedV, _, _) if k == key => R.get(storedV.asInstanceOf[R[V]])
          }.flatten
          val stored = R.wrap(value)
          val expiry = computeExpiry(nowNano, ttl)
          val displaced = collectDisplacedSignals(state.entries, _ == key)
          val newEntries = Ready[K, R[V]](key, stored, expiry, null) :: cleaned.filterNot(_.key == key)
          ((previous, displaced), BucketState(newEntries))
        }
      }) { case (previous, promises) =>
        F.map(F.void(F.traverse(promises)(_.succeed(None: Option[V]))))(_ => previous)
      }
    )
  }

  override def computeIfAbsent[E](key: K, compute: F[E, V]): F[E, V] =
    computeImpl(key, None, compute)

  override def computeIfAbsentWithTTL[E](key: K, ttl: FiniteDuration, compute: F[E, V]): F[E, V] =
    computeImpl(key, Some(ttl), compute)

  /** Terminate an in-flight caller with the closed defect. Used in every branch
    * of `computeImpl` / `awaitAndRetry` where we detect `closedFlag == true`.
    */
  private[this] def failClosed[E]: F[E, V] =
    F.terminate(new IllegalStateException("BIOCache: computeIfAbsent called after close"))

  // Action tags for computeImpl dispatch (avoids GADT variance issues with sealed trait)
  private[this] val ActionHit = 0
  private[this] val ActionWait = 1
  private[this] val ActionCompute = 2
  private[this] val ActionClosed = 3 // cache is closed; fail fast
  private[this] val ActionSwapped = 4 // bucketRef is orphaned by invalidateAll/close; retry


  /** Wait-path context captured at park time inside `computeImpl`'s modify.
    *
    * On wake with `Some(v)`:
    *   - if `closedFlag` is set → fail fast;
    *   - else if `bucketFor(key) ne parkedBucketRef` → `invalidateAll` /
    *     `close` swapped the whole structure → retry against the fresh vector;
    *   - otherwise → accept `v` (Guava-style loader-result dedup:
    *     the producer computed `v`; waiters receive the same `v` unless an
    *     explicit global barrier linearized in between). A racing
    *     `put(k)` / `invalidate(k)` does NOT force a waiter retry; its
    *     effect is observed by NEW callers on the post-displacement bucket. */
  private[this] final class WaitCtx(
    val promise: AnyRef,
    val parkedBucketRef: Ref2[F, BucketState[K, R[V]]],
  )

  private[this] def computeImpl[E](key: K, ttl: Option[FiniteDuration], compute: F[E, V]): F[E, V] = {
    val bucketRef = bucketFor(key)
    F.uninterruptibleExcept { restore =>
      F.flatMap(P.mkPromise[Nothing, Option[V]]) { promise =>
        // Generate an origin token up-front. It lives on BOTH the `Computing`
        // (so waiters can capture it) AND the published `Ready` (so waiters
        // can verify the slot on wake). This ties producer's Computing to its
        // own Ready by identity, independent of hashing / bucket sharing.
        val myOriginToken: AnyRef = new Object
        F.flatMap(bucketRef.modify { state =>
          // Read clock INSIDE modify so CAS retries see a fresh timestamp.
          // Without this, a Ready that expires mid-retry could still be
          // treated as live (ActionHit on an expired entry).
          val nowNano = System.nanoTime()
          val cleaned = cleanBucket(state.entries, nowNano)
          // Orphan check: if `structureRef` was swapped between our
          // `bucketFor(key)` capture and this modify commit, `bucketRef` is
          // now orphan — installing Computing here would never be observable
          // via the live structure, and any waiter that also captured the
          // orphan would park on a promise no public op can ever signal. Bail
          // out with ActionSwapped and let the caller re-enter computeImpl
          // against the fresh vector. Since modify's CAS is atomic against
          // concurrent same-bucket ops, this check combined with
          // drainPromises' CAS on old buckets eliminates the hang window.
          if (bucketRef ne bucketFor(key)) {
            // Orphan bucket — don't touch state (it's unreachable via the live
            // structure; cleaning it is pointless). CAS on state→state is a
            // no-op that still serializes with concurrent drainPromises,
            // ensuring any Computing installed before we reached this check
            // is visible to drain on its CAS retry.
            ((ActionSwapped, null), state)
          } else cleaned.find(_.key == key) match {
              case Some(Ready(_, stored, _, _)) =>
                R.get(stored) match {
                  case Some(v) =>
                    ((ActionHit, v.asInstanceOf[AnyRef]), BucketState(cleaned))
                  case None =>
                    if (closedFlag.get()) ((ActionClosed, null), BucketState(cleaned))
                    else {
                      // Replace the dead Ready with our Computing (benign-GC
                      // reload).
                      val newEntries = Computing[K, R[V]](key, promise, myOriginToken) :: cleaned.filterNot(_.key == key)
                      ((ActionCompute, null), BucketState(newEntries))
                    }
                }
              case Some(c: Computing[K, R[V]] @unchecked) =>
                ((ActionWait, new WaitCtx(c.promise, bucketRef)), BucketState(cleaned))
              case _ =>
                if (closedFlag.get()) ((ActionClosed, null), BucketState(cleaned))
                else {
                  // Empty slot → install our Computing.
                  val newEntries = Computing[K, R[V]](key, promise, myOriginToken) :: cleaned.filterNot(_.key == key)
                  ((ActionCompute, null), BucketState(newEntries))
                }
            }
          }) { case (tag, payload) =>
            // Post-CAS fence re-check.
            //
            // Inside-closure reads of `closedFlag` / `bucketFor(key)` race the
            // bucket CAS commit: a caller whose closure evaluated both guards
            // as false can still have `close` flip `closedFlag` AND swap
            // `structureRef` in the window between closure return and CAS
            // commit. Without a post-CAS re-check, such a caller would admit
            // a `Computing` on a now-orphan bucket and start the loader —
            // violating the `close` contract that admissions are rejected
            // before the loader runs. Re-reading both atomics AFTER the CAS
            // linearizes the admission decision with close: if either atomic
            // has moved, we roll back (ActionCompute) or return a fresh/failure
            // view (ActionHit/ActionWait) accordingly.
            if (tag == ActionHit) {
              // Stale-hit guard: a Ready we read from the bucket could belong
              // to the orphan (swap) or predate close. Live bucket post-swap
              // is empty, so only an orphan read can produce a Ready here.
              if (closedFlag.get() || (bucketRef ne bucketFor(key))) {
                if (closedFlag.get()) failClosed
                else computeImpl(key, ttl, compute)
              } else F.pure(payload.asInstanceOf[V])
            } else if (tag == ActionWait) {
              val ctx = payload.asInstanceOf[WaitCtx]
              // Waiter-side freshness is handled in `awaitAndRetry` (it
              // re-reads closedFlag and orphan identity on wake),
              // so no rollback is required here — we did not install state.
              restore(awaitAndRetry(key, ttl, compute, ctx.promise, ctx.parkedBucketRef))
            } else if (tag == ActionClosed) {
              failClosed
            } else if (tag == ActionSwapped) {
              // Structure was swapped; re-enter from the top so we capture a
              // fresh bucketRef from the live `currentBuckets`.
              computeImpl(key, ttl, compute)
            } else {
              // ActionCompute: our `Computing` is in the bucket. Close out
              // the admission/close race window by re-checking both fences
              // after the CAS commit. If close has been observed (flag or
              // swap) since the closure returned, we must:
              //   1. Remove our `Computing` from the (now orphan) bucket.
              //   2. Signal our promise with `None` so any waiter that parked
              //      on us between CAS and this re-check wakes and retries
              //      (they will see closedFlag and fail-fast).
              //   3. Either fail-fast (if closed) or retry through computeImpl
              //      on the fresh structure.
              val promiseRef: AnyRef = promise.asInstanceOf[AnyRef]
              if (closedFlag.get() || (bucketRef ne bucketFor(key))) {
                F.flatMap(bucketRef.update_ { state =>
                  BucketState(
                    state.entries.filterNot {
                      case Computing(k, p, _) => k == key && (p eq promiseRef)
                      case _ => false
                    },
                  )
                }) { _ =>
                  F.flatMap(F.void(promise.succeed(None: Option[V]))) { _ =>
                    if (closedFlag.get()) failClosed
                    else computeImpl(key, ttl, compute)
                  }
                }
              } else {
                doCompute(key, bucketRef, promise, myOriginToken, ttl, compute, restore)
              }
            }
          }
        }
      }
    }

  /** Waiter-side: await the producer's signal and read the value directly from the
    * promise payload.
    *
    * The promise carries `Option[V]` (the raw, unwrapped value — `Some(v)` on success,
    * `None` on producer failure/interrupt). Waiters do NOT touch the bucket — this is
    * crucial because:
    *   - `cache.get` would filter expired `Ready` via `cleanBucket`, causing a
    *     zero-TTL waiter to observe `None` even after a successful publish.
    *   - The eager-eviction fiber can sweep the bucket between publish and waiter wake;
    *     a bucket read would race that cleaner. Going through the promise is immune.
    *
    * Design note — GC-eligibility for weak/soft caches. The promise holds `Some(v)`
    * strongly, but the promise itself is NOT referenced from the cache: `Ready.origin`
    * carries a lightweight per-call `Object` token (see [[doCompute.originToken]]),
    * NOT the promise. The promise therefore remains GC-eligible once the producer
    * fiber and all parked waiters have released their local references — typically
    * microseconds after the producer returns. For `WeakCacheRef`/`SoftCacheRef`,
    * V is pinned only during that short window; the cache's `Ready.stored` wraps V
    * in the weak/soft reference as usual, so the cache-level GC contract is preserved.
    *
    * Waiter decision table:
    *   - `Some(v)` → return v directly (Guava-style loader-result handoff).
    *   - `None` (producer failed/interrupted/defected) → retry via `computeImpl`; the
    *     bucket CAS dedups N racing retries to exactly one `compute` invocation.
    *
    * Interruptible at the top level (called under `restore`) so caller timeouts propagate.
    */
  private[this] def awaitAndRetry[E](
    key: K,
    ttl: Option[FiniteDuration],
    compute: F[E, V],
    existingPromiseAnyRef: AnyRef,
    parkedBucketRef: Ref2[F, BucketState[K, R[V]]],
  ): F[E, V] = {
    val existingPromise = existingPromiseAnyRef.asInstanceOf[Promise2[F, Nothing, Option[V]]]
    F.flatMap(existingPromise.await) {
      case Some(v) =>
        // Post-wake freshness validation. Two atomic reads:
        //
        //   1. `bucketFor(key)` via `structureRef.get()` — detects a swap
        //      by `invalidateAll` / `close` that replaced the whole vector
        //      (fresh `Ref2` identity `ne` our `parkedBucketRef`).
        //   2. `closedFlag.get()` — post-check fence.
        //
        // Decisions:
        //   - `closedFlag` set → fail fast.
        //   - `bucketFor(key) ne parkedBucketRef` → structure swap linearized
        //     before this check; our parked snapshot is stale → retry.
        //   - Otherwise → accept v (Guava loader-result dedup). A racing
        //     `put(k)` / `invalidate(k)` on the same bucket does NOT force
        //     a waiter retry: the producer computed v, we get v. `put`'s
        //     newer value is observed by NEW callers, not by already-parked
        //     waiters.
        if (closedFlag.get()) failClosed
        else if (bucketFor(key) ne parkedBucketRef) computeImpl(key, ttl, compute)
        else F.pure(v)
      case None =>
        if (closedFlag.get()) failClosed
        else computeImpl(key, ttl, compute)
    }
  }

  /** Check whether a bucket entry is a [[Computing]] marker owned by us (same promise identity). */
  private[this] def isOwnedComputing(entry: BucketEntry[K, R[V]], promiseRef: AnyRef): Boolean = entry match {
    case Computing(_, p, _) => p eq promiseRef
    case _ => false
  }

  private[this] def doCompute[E](
    key: K,
    bucketRef: Ref2[F, BucketState[K, R[V]]],
    myPromise: Promise2[F, Nothing, Option[V]],
    originToken: AnyRef,
    ttl: Option[FiniteDuration],
    compute: F[E, V],
    restore: RestoreInterruption2[F],
  ): F[E, V] = {
    // Promise reference for matching OUR Computing entries by identity.
    val promiseRef: AnyRef = myPromise.asInstanceOf[AnyRef]
    // `originToken` passed in from `computeImpl` — shared with our `Computing` and,
    // on success, with our published `Ready`. Parked waiters captured this token
    // from the Computing so they can verify post-wake that the Ready in the slot
    // is still ours.

    // Predicate for removing OUR bucket entries during cleanup: either the Computing
    // marker we installed (identified by `promiseRef`) or a Ready we successfully
    // published (identified by `originToken`).
    def isOurEntry(e: BucketEntry[K, R[V]]): Boolean = e match {
      case Computing(k, p, _) => k == key && (p eq promiseRef)
      case Ready(k, _, _, origin) => k == key && (origin eq originToken)
    }

    // The outer `computeImpl`'s `uninterruptibleExcept` already holds us uninterruptible;
    // we just need `guaranteeOnFailure` to clean up the Computing entry and signal the
    // promise if an interrupt/defect propagates through `restore(compute)` or through the
    // retry's `restore(computeImpl(...))`.
    F.guaranteeOnFailure(
      F.flatMap(F.sandboxExit(restore(compute))) { exit =>
        // -- uninterruptible from here (outer mask in effect, except `restore`-wrapped regions) --
        exit match {
          case Exit.Success(v) =>
            // Guava-style producer-result semantics: the caller invoked `compute`
            // and produced `v`; both the caller and parked waiters receive `v`,
            // regardless of whether a racing put/invalidate displaced the bucket.
            // Cache state may diverge — that only affects NEW callers, not this
            // in-flight load's waiters.
            //
            // Publication rule: publish `Ready(v)` ONLY IF
            //   (a) `bucketRef` is still the live bucket for this key
            //       (`structureRef` was not swapped since admission), AND
            //   (b) `closedFlag` is false, AND
            //   (c) our own `Computing` marker is still in the slot.
            //
            // Any other state blocks publication:
            //   - Structure swapped → our bucketRef is orphan; publishing would
            //     leak to an unreachable structure. Skip.
            //   - Closed → cache is terminally closed.
            //   - Empty slot → our Computing was removed by some control-plane op.
            //   - Foreign Computing → a successor is refreshing; it is authoritative.
            //   - Any foreign Ready → put/newer compute is authoritative.
            //
            // Clock, `storedV`, and `expiry` are computed INSIDE the modify
            // closure so each CAS retry uses a fresh clock reading — otherwise
            // contention could shorten the TTL arbitrarily by reusing a stale
            // `nowNano` across retries, potentially publishing an entry that
            // is already expired at commit time.
            val storedV = R.wrap(v)
            F.flatMap(bucketRef.modify { state =>
              val nowNano = System.nanoTime()
              val cleaned = cleanBucket(state.entries, nowNano)
              if (bucketRef ne bucketFor(key)) {
                // Structure was swapped out from under us; bucketRef is orphan.
                // Do NOT publish — our Ready would land on an unreachable Ref2.
                (false, BucketState(cleaned))
              } else if (closedFlag.get()) {
                (false, BucketState(cleaned))
              } else {
                val ownsComputing = cleaned.exists(e => e.key == key && isOwnedComputing(e, promiseRef))
                if (ownsComputing) {
                  val expiry = computeExpiry(nowNano, ttl)
                  (true, BucketState(Ready[K, R[V]](key, storedV, expiry, originToken) :: cleaned.filterNot(_.key == key)))
                } else {
                  (false, BucketState(cleaned))
                }
              }
            }) { published =>
              val payload: Option[V] = if (published) Some(v) else None
              F.map(F.void(myPromise.succeed(payload)))(_ => v)
            }
          case _: Exit.FailureUninterrupted[?] =>
            // Computation failed — remove Computing entry (if we still own it), signal
            // waiters with `None` (no value available → they retry via computeImpl).
            F.flatMap(bucketRef.update_ { state =>
              BucketState(state.entries.filterNot(e => e.key == key && isOwnedComputing(e, promiseRef)))
            }) { _ =>
              F.flatMap(F.void(myPromise.succeed(None: Option[V]))) { _ =>
                F.fromSandboxExit(exit)
              }
            }
        }
      },
      // On interruption or defect: remove any entry we installed and signal waiters
      // with `None`. Matches both Computing(ourPromise) (not yet published) and
      // Ready(ourOriginToken) (already published but defected before return), so no
      // stale entry persists.
      { (_: Exit.Failure[E]) =>
        F.flatMap(bucketRef.update_ { state =>
          BucketState(state.entries.filterNot(isOurEntry))
        }) { _ =>
          F.void(myPromise.succeed(None: Option[V]))
        }
      },
    )
  }

  override def invalidate(key: K): F[Nothing, Unit] = {
    // Remove entries for `key` and collect any displaced in-flight
    // Computing's promise. Signal collected promises `None` post-commit
    // so parked waiters wake and retry.
    F.uninterruptible(
      F.flatMap(bucketFor(key).modify { state =>
        if (closedFlag.get()) {
          (Nil: List[Promise2[F, Nothing, Option[V]]], state)
        } else {
          val nowNano = System.nanoTime()
          val displaced = collectDisplacedSignals(state.entries, _ == key)
          val cleaned = cleanBucket(state.entries, nowNano)
          (displaced, BucketState(cleaned.filterNot(_.key == key)))
        }
      }) { promises =>
        F.void(F.traverse(promises)(_.succeed(None: Option[V])))
      }
    )
  }

  /** Build a fresh Vector of `initialCapacity` brand-new `Ref2[BucketState]`s.
    * Used by `invalidateAll` and `close` to install a clean structure via
    * `structureRef.getAndSet`. Each Ref2 is a new object identity — parked
    * waiters with a captured old bucket Ref2 will fail the `eq` check on
    * wake and retry against the fresh structure. */
  private[this] def freshBuckets: F[Nothing, Vector[Ref2[F, BucketState[K, R[V]]]]] = {
    F.map(F.traverse((0 until initialCapacity).toList)(_ => P.mkRef(BucketState[K, R[V]](Nil))))(_.toVector)
  }

  override def invalidateAll: F[Nothing, Unit] = {
    // Atomic swap semantics: a single CAS replaces the entire bucket vector.
    //   1. Build a fresh vector of fresh `Ref2`s.
    //   2. `structureRef.getAndSet(newVec)` atomically swaps — the linearization
    //      point of `invalidateAll`. After this instant every `currentBuckets`
    //      read returns the fresh vector.
    //   3. Drain old-vector promises: signal every in-flight `Computing`'s
    //      promise with `None` so parked waiters wake. On wake they compare
    //      `bucketFor(key) ne parkedBucketRef` (different identity → structure
    //      swapped) and retry through `computeImpl` on the fresh vector.
    //
    // In-flight operations that captured the OLD vector before step 2 keep
    // running against the orphaned `Ref2`s: their effects are invisible to
    // post-swap readers. Matches `java.util.ConcurrentHashMap.clear` —
    // concurrent pre-call operations may complete with their own local view.
    //
    // Post-close no-op: if the cache is closed, `close` already swapped and
    // drained; `invalidateAll` is a silent no-op to avoid racing with close's
    // teardown.
    F.uninterruptible(
      if (closedFlag.get()) F.unit
      else F.flatMap(freshBuckets) { newVec =>
        val oldVec = structureRef.getAndSet(newVec)
        drainPromises(oldVec)
      }
    )
  }

  /** Drain all in-flight `Computing` promises from the given (usually
    * orphaned) bucket vector. Each bucket is visited in its own modify,
    * collecting promises and returning the state unchanged. Collected
    * promises are signaled `None` post-traversal so parked waiters wake.
    *
    * Used by `invalidateAll` and `close` AFTER the atomic structure swap.
    * The drain is NOT part of the swap's linearization — it is purely
    * liveness cleanup on the orphaned structure. Missing a promise here
    * only means the waiter eventually wakes when the producer signals;
    * it does not affect correctness. */
  private[this] def drainPromises(vec: Vector[Ref2[F, BucketState[K, R[V]]]]): F[Nothing, Unit] = {
    F.flatMap(F.traverse(vec.toList) { ref =>
      ref.modify { state =>
        val displaced = collectDisplacedSignals(state.entries, _ => true)
        (displaced, state)
      }
    }) { perBucketPromises =>
      F.void(F.traverse(perBucketPromises.flatten)(_.succeed(None: Option[V])))
    }
  }

  override def size: F[Nothing, Int] = {
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      F.map(F.traverse(currentBuckets.toList) { ref =>
        F.map(ref.get) { state =>
          state.entries.count {
            case r: Ready[_, _] => !isExpired(r.expiresAtNano, nowNano) && R.get(r.stored.asInstanceOf[R[V]]).isDefined
            case _ => false
          }
        }
      })(_.sum)
    }
  }

  override def keys: F[Nothing, Set[K]] = {
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      F.map(F.traverse(currentBuckets.toList) { ref =>
        F.map(ref.get) { state =>
          state.entries.collect {
            case r @ Ready(k, _, expiresAtNano, _) if !isExpired(expiresAtNano, nowNano) && R.get(r.stored.asInstanceOf[R[V]]).isDefined => k
          }
        }
      })(_.flatten.toSet)
    }
  }

  override def toMap: F[Nothing, Map[K, V]] = {
    // Per-bucket atomic snapshot; cross-bucket concurrent mutations linearize
    // independently. Matches `java.util.concurrent.ConcurrentHashMap.entrySet`
    // iterator semantics (weakly consistent).
    //
    // Expired entries and GC-reclaimed weak/soft values are filtered. In-flight
    // `Computing` entries carry no value and are skipped.
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      F.map(F.traverse(currentBuckets.toList) { ref =>
        F.map(ref.get) { state =>
          state.entries.collect {
            case Ready(k, storedV, expiresAtNano, _) if !isExpired(expiresAtNano, nowNano) =>
              R.get(storedV.asInstanceOf[R[V]]).map(v => (k, v))
          }.flatten
        }
      })(_.flatten.toMap)
    }
  }

  override def close: F[Nothing, Unit] = {
    // Terminal close via atomic swap:
    //   1. Flip `closedFlag` to `true` BEFORE the swap. All subsequent
    //      admissions in `computeImpl` read it inside their modify closure
    //      and fail fast — even on buckets of the OLD vector that an
    //      in-flight caller may still be operating on pre-swap.
    //   2. Interrupt the background eviction fiber (if any).
    //   3. `structureRef.getAndSet(freshEmptyVector)` atomically replaces the
    //      whole bucket structure. Post-swap `currentBuckets` reads return an
    //      empty vector — no state remains.
    //   4. Drain old-vector promises: wake every parked waiter with `None`.
    //      On wake they observe `closedFlag.get() == true` and fail fast with
    //      IllegalStateException (no retry, no new compute).
    //   5. Signal `closedPromise` so any concurrent `close` caller that lost
    //      the `closedFlag.getAndSet` race wakes and returns.
    //
    // Producer fibers themselves are NOT interrupted from here; the caller
    // is expected to cancel them via their own supervision (timeouts, fiber
    // scope). Pre-close producers complete their compute, publish to the
    // orphaned bucket (invisible to post-close readers), and return `v` to
    // their direct caller per Guava loader-result semantics.
    //
    // Idempotent AND teardown-linearizable: two concurrent `close` callers
    // both observe "close completed" only AFTER the winning caller has
    // finished teardown. The loser awaits `closedPromise`; the winner
    // signals it after drain. Without this, a loser that got `true` from
    // `getAndSet` could return F.unit while teardown is still in flight —
    // violating the contract that once any `close` returns, the cache is
    // fully quiesced.
    F.uninterruptible(
      F.flatMap(P.mkPromise[Nothing, Exit.Uninterrupted[Nothing, Unit]]) { candidate =>
        F.flatMap(F.sync {
          // CAS-install the candidate as the shared close promise. Exactly
          // one concurrent caller wins this CAS; only that caller flips
          // closedFlag, interrupts the eviction fiber, swaps structureRef,
          // and drains. Losers observe the winner's promise and await it.
          val installed = closedPromiseRef.compareAndSet(null, candidate)
          val promise = if (installed) candidate else closedPromiseRef.get()
          if (installed) {
            // Flip admission fence. No getAndSet needed — CAS-install already
            // gave us exclusive teardown rights.
            closedFlag.set(true)
          }
          (installed, promise)
        }) { case (shouldTearDown, winnerPromise) =>
          // Unified replay: EVERY caller — winner and losers alike —
          // re-raises its `close` outcome by awaiting `winnerPromise`
          // and passing the resulting Exit through `F.fromSandboxExit`.
          // The winner's extra work is solely the teardown + publish
          // step; its replay is the same code path as any loser's.
          //
          // Routing winner through `await` (rather than re-raising
          // from a local exit variable) makes the published Exit the
          // SOLE source of every caller's terminal effect. A hypothetical
          // regression where the winner synthesized an observably
          // equivalent exit via a different code path (e.g.,
          // `F.terminate(sameThrowable)`) is mechanically eliminated
          // by construction — there is no other source to synthesize
          // from. Await on an already-completed promise is O(1).
          val teardownAndPublish: F[Nothing, Unit] = if (shouldTearDown) {
            val teardown: F[Nothing, Unit] =
              F.flatMap(evictionFiberRef.get) { fiberOpt =>
                F.flatMap(fiberOpt match {
                  case Some(fiber) => fiber.interrupt
                  case None => F.unit
                }) { _ =>
                  F.flatMap(freshBuckets) { emptyVec =>
                    val oldVec = structureRef.getAndSet(emptyVec)
                    drainPromises(oldVec)
                  }
                }
              }
            F.flatMap(F.sandboxExit(teardown))(exit => F.void(winnerPromise.succeed(exit)))
          } else F.unit
          F.flatMap(teardownAndPublish)(_ => F.flatMap(winnerPromise.await)(F.fromSandboxExit(_)))
        }
      }
    )
  }

  /** TEST-ONLY: count bucket entries for a key (Ready + Computing combined). Used to
    * assert internal dedup invariants that are not observable through the public API.
    * E.g., "exactly 1 Computing for key K across N concurrent waiters" proves all waiters
    * took the `ActionWait` path (no new-caller-path regression). */
  private[bio] def countBucketEntriesForTesting(key: K): F[Nothing, Int] = {
    F.map(bucketFor(key).get)(_.entries.count(_.key == key))
  }

  /** TEST-ONLY: snapshot the bucket entries for a key. Used to inspect internal state
    * (e.g., `Ready.origin` identity) that is not observable through the public API. */
  private[bio] def bucketEntriesForTesting(key: K): F[Nothing, List[BucketEntry[K, R[V]]]] = {
    F.map(bucketFor(key).get)(_.entries.filter(_.key == key))
  }

  private[cache] def evictExpired: F[Nothing, Unit] = {
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      F.traverse_(currentBuckets.toList) { ref =>
        ref.update_ { state =>
          BucketState(cleanBucket(state.entries, nowNano))
        }
      }
    }
  }
}

// Bucket entry types. Widened to `private[bio]` so test code in
// `izumi.functional.bio.test` can pattern-match on `Ready`/`Computing` when using
// the TEST-ONLY bucket inspection hooks. Not part of the public API.
private[bio] sealed trait BucketEntry[K, +S] {
  def key: K
}

/** A successfully computed / put value.
  *
  * @param origin an opaque identity token for the publishing computeIfAbsent call —
  *               used ONLY by [[ConcurrentHashMapCache.doCompute]]'s `guaranteeOnFailure`
  *               cleanup to match and remove a Ready that THIS very call published
  *               (defect rollback). `null` for entries installed by `put`. Control ops
  *               do not inspect origin.
  */
private[bio] final case class Ready[K, S](key: K, stored: S, expiresAtNano: Long, origin: AnyRef) extends BucketEntry[K, S]

/** Per-bucket state wrapper. The bucket is a single-field value — `entries`
  * is the current `List[BucketEntry]` in this bucket.
  *
  * Barriers live OUTSIDE the per-bucket state:
  *   - [[ConcurrentHashMapCache.closedFlag]] (cache-wide AtomicBoolean) fences
  *     admissions after `close`.
  *   - Atomic structure swap via [[ConcurrentHashMapCache.structureRef]] on
  *     `invalidateAll` / `close`. Waiters detect this by capturing the bucket
  *     `Ref2` identity at park time (see `WaitCtx.parkedBucketRef`) and
  *     comparing it to `bucketFor(key)` on wake — a mismatch means the whole
  *     structure was replaced, forcing a retry against the fresh vector.
  *
  * `put(k)` / `invalidate(k)` displace any in-flight `Computing(k, ...)` by
  * signaling its promise `None` from inside the same modify, so parked waiters
  * wake and retry. They do NOT carry an additional per-key barrier counter:
  * waiters follow Guava loader-result semantics and return the producer's `v`
  * on wake (after verifying no structure swap / close has linearized).
  */
private[bio] final case class BucketState[K, S](
  entries: List[BucketEntry[K, S]],
)

/** A placeholder for an in-flight computeIfAbsent call.
  *
  * @param promise the per-key promise carrying `Option[V]` (raw V, not the `R[V]`
  *                wrapper). Stored as `AnyRef` to keep `BucketEntry` free of the
  *                `F` type parameter.
  * @param originToken identity token linking this in-flight call to the `Ready`
  *                that the producer will publish on success (same token carried
  *                in `Ready.origin`). Used ONLY by `doCompute`'s
  *                `guaranteeOnFailure` cleanup to match and remove a Ready
  *                that THIS very call published (defect rollback). NOT part
  *                of the waiter freshness check — that uses
  *                [[ConcurrentHashMapCache.structureRef]] identity
  *                (see [[ConcurrentHashMapCache.awaitAndRetry]]).
  */
private[bio] final case class Computing[K, S](key: K, promise: AnyRef, originToken: AnyRef) extends BucketEntry[K, Nothing]

private[bio] object ConcurrentHashMapCache {

  def create[F[+_, +_], K, R[_], V](
    config: CacheConfig
  )(implicit F: IO2[F],
    P: Primitives2[F],
    R: CacheRefType[R],
  ): F[Nothing, BIOCache[F, K, V]] = {
    val n = Math.max(1, config.initialCapacity)
    F.flatMap(F.traverse((0 until n).toList)(_ => P.mkRef(BucketState[K, R[V]](Nil)))) { bucketList =>
      F.map(P.mkRef(Option.empty[Fiber2[F, Nothing, Unit]])) { fiberRef =>
        new ConcurrentHashMapCache[F, K, R, V](bucketList.toVector, config, fiberRef): BIOCache[F, K, V]
      }
    }
  }

  def createWithEviction[F[+_, +_], K, R[_], V](
    config: CacheConfig
  )(implicit F: IO2[F],
    P: Primitives2[F],
    R: CacheRefType[R],
    T: Temporal2[F],
    FK: Fork2[F],
  ): F[Nothing, BIOCache[F, K, V]] = {
    val n = Math.max(1, config.initialCapacity)
    F.flatMap(F.traverse((0 until n).toList)(_ => P.mkRef(BucketState[K, R[V]](Nil)))) { bucketList =>
      val buckets = bucketList.toVector
      F.flatMap(P.mkRef(Option.empty[Fiber2[F, Nothing, Unit]])) { fiberRef =>
        val cache = new ConcurrentHashMapCache[F, K, R, V](buckets, config, fiberRef)
        config.eagerEvictionInterval match {
          case Some(interval) =>
            val evictionLoop: F[Nothing, Unit] =
              F.tailRecM[Nothing, Unit, Nothing](()) { _ =>
                F.map(F.*>(T.sleep(interval), cache.evictExpired))(_ => Left(()))
              }
            // Leak-safe construction:
            //   - `uninterruptible` around `fork + fiberRef.set` makes registration
            //     atomic: `close` can always find the fiber once the block completes.
            //   - `guaranteeOnFailure` is a belt-and-braces catch for the small window
            //     where a caller-level interrupt is delivered as the `uninterruptible`
            //     region exits (e.g., a masked interrupt latches until unmask, then
            //     propagates before the caller's outer `flatMap` can install its own
            //     bracket). If that happens, cleanup reads the now-populated fiberRef
            //     and interrupts the eviction fiber, preventing a leak.
            //
            // Caller contract (still required): wrap construction in a bracket/Resource
            // that calls `close` on scope exit. The internal safety net only covers
            // interruption delivered WHILE `createWithEviction` itself is running.
            F.guaranteeOnFailure[Nothing, BIOCache[F, K, V]](
              F.uninterruptible(
                F.flatMap(FK.fork(evictionLoop)) { fiber =>
                  F.map(fiberRef.set(Some(fiber)))(_ => cache: BIOCache[F, K, V])
                }
              ),
              (_: Exit.Failure[Nothing]) =>
                F.flatMap(fiberRef.get) {
                  case Some(fiber) => F.void(fiber.interrupt)
                  case None => F.unit
                },
            )
          case None =>
            F.pure(cache: BIOCache[F, K, V])
        }
      }
    }
  }
}
