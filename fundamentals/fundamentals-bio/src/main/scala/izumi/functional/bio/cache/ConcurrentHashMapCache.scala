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
  * hung producer — any control-plane operation (including `shutdown`) unblocks it.
  *
  * ==Producer publication (Guava semantics)==
  *
  * When a producer finishes `compute`, it publishes `Ready(v)` only if its OWN
  * `Computing` marker is still in the slot. Any other state — empty slot (our
  * Computing was removed by `invalidate`/`invalidateAll`/`shutdown` or by a
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
  * All freshness-barrier state lives INSIDE each bucket's [[BucketState]]:
  * `closed` (shutdown marker), `invGen` (invalidateAll/shutdown generation),
  * and `keyEpochs` (per-key put/invalidate counter). A waiter's post-wake
  * decision is therefore made from a SINGLE atomic `bucketRef.get`
  * snapshot — there is no inter-atomic race between separate counters.
  *
  *   - `state.closed` → fail fast with IllegalStateException.
  *   - `state.invGen > parked` → [[invalidateAll]] / [[shutdown]] visited
  *     this bucket → retry.
  *   - `state.keyEpochs(k) > parked` → [[put]] / [[invalidate]] on THIS
  *     key fired → retry. No hash-collision false positives.
  *
  * Benign events (TTL expiry, weak/soft GC cleanup, successor compute-publish)
  * do NOT bump either counter. Already-deduped waiters whose producer succeeded
  * receive its `Some(v)` — Guava's loader-result dedup is preserved.
  *
  * ==Non-atomic flush / close==
  *
  * [[invalidateAll]] and [[shutdown]] are BUCKET-by-bucket operations, not
  * globally atomic. Each bucket's state transition is linearizable with
  * any concurrent same-bucket op, but concurrent callers on buckets the
  * traversal hasn't reached yet may still observe pre-flush / pre-close
  * state. This matches `java.util.ConcurrentHashMap.clear` semantics. For
  * an atomic flush, construct a new cache.
  */
private[bio] final class ConcurrentHashMapCache[F[+_, +_], K, R[_], V](
  buckets: Vector[Ref2[F, BucketState[K, R[V]]]],
  config: CacheConfig,
  evictionFiberRef: Ref2[F, Option[Fiber2[F, Nothing, Unit]]],
)(implicit
  F: IO2[F],
  P: Primitives2[F],
  R: CacheRefType[R],
) extends BIOCache[F, K, V] {

  // Cache-wide shutdown fence. Flipped to `true` at the START of
  // `shutdown` — BEFORE bucket traversal — to fence new admissions on
  // buckets the traversal has not yet visited. Per-bucket `state.closed`
  // remains the linearization-point fact that a waiter reads from its
  // single-atomic bucket snapshot; this cache-wide flag is an ADDITIONAL
  // belt-and-suspenders check at admission time in `computeImpl` so that
  // no new `Computing` is installed after shutdown starts, even in a
  // bucket whose state.closed has not yet been written.
  //
  // Monotonic: once flipped true, stays true. `invalidateAll` does NOT
  // touch this flag — it is per-bucket non-atomic by design, matching
  // `ConcurrentHashMap.clear` semantics.
  private[this] val closedFlag: java.util.concurrent.atomic.AtomicBoolean =
    new java.util.concurrent.atomic.AtomicBoolean(false)

  // Freshness-fence design:
  //
  //   - `globalEpoch` (AtomicLong, bumped ATOMICALLY before traversal by
  //     `invalidateAll` / `shutdown`): global barrier. Any waiter whose
  //     parked globalEpoch is less than the current globalEpoch retries on
  //     wake, regardless of which bucket they are in or the traversal order.
  //
  //   - per-key `BucketState.keyEpochs(k)` (bumped atomically inside
  //     `put(k)` / `invalidate(k)` modify closures): per-key barrier.
  //     Only waiters parked on `k` see the advance. A write on a
  //     hash-colliding different key bumps a separate map entry, so
  //     unrelated waiters are never disturbed — no duplicate-compute
  //     hazard under short-TTL / weak-ref caches where a false-positive
  //     retry could land on a cleaned-out bucket and spawn a second
  //     compute.
  //
  //   - `Computing.originToken` / `Ready.origin`: per-producer identity used
  //     ONLY by `doCompute`'s own cleanup (match our own Computing/Ready on
  //     failure rollback). NOT part of the waiter freshness check.
  //
  // Benign cleanup (TTL expiry, weak/soft GC, successor compute-publish)
  // does NOT bump either counter, so already-deduped waiters are never
  // hijacked. This is the Guava loader-result dedup contract.
  //
  // Memory: one AtomicLong cache-wide + one Long per bucket. Independent of
  // key cardinality; invulnerable to adversarial unique-key workloads.
  //
  // No in-flight promise tracker is needed — `put` / `invalidate` /
  // `invalidateAll` / `shutdown` collect any displaced `Computing` promise
  // inside their bucket modify and signal it `None` post-commit.

  private[this] val numBuckets = buckets.size

  private[this] def bucketFor(key: K): Ref2[F, BucketState[K, R[V]]] = {
    val h = key.hashCode()
    val spread = h ^ (h >>> 16) // spread high bits (from ConcurrentHashMap)
    buckets(Math.floorMod(spread, numBuckets))
  }

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
            case s @ Some(_) => (s, BucketState(state.invGen, state.closed, state.keyEpochs, cleaned))
            case None =>
              (None, BucketState(state.invGen, state.closed, state.keyEpochs, cleaned.filterNot(_.key == key)))
          }
        case _ => (None, BucketState(state.invGen, state.closed, state.keyEpochs, cleaned))
      }
    }
  }

  override def put(key: K, value: V): F[Nothing, Unit] = putImpl(key, value, None)

  override def putWithTTL(key: K, value: V, ttl: FiniteDuration): F[Nothing, Unit] = putImpl(key, value, Some(ttl))

  /** Collect `Computing` promises from displaced entries. Signaled post-commit
    * with `None` via `Promise2.succeed` (itself CAS-atomic). `Ready` entries do
    * NOT carry a promise reference under the generation-based freshness design:
    * waiters validate freshness by comparing `BucketState.gen` captured at park
    * time against the current gen on wake, so displacers don't need to veto the
    * producer's `Some(v)` through the promise — a gen-advance already forces
    * the waiter onto the retry path.
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

  private[this] def putImpl(key: K, value: V, ttl: Option[FiniteDuration]): F[Nothing, Unit] = {
    // Full-close: post-shutdown puts are silent no-ops.
    //
    // Otherwise: install our Ready, bump the bucket's `gen` (per-key barrier),
    // collect any displaced Computing's promise — all in ONE atomic modify.
    // Signal collected promises `None` post-commit. Fully uninterruptible.
    F.uninterruptible(
      F.flatMap(bucketFor(key).modify { state =>
        if (state.closed || closedFlag.get()) {
          (Nil: List[Promise2[F, Nothing, Option[V]]], state)
        } else {
          // Read clock INSIDE modify so CAS retries see a fresh timestamp.
          val nowNano = System.nanoTime()
          val stored = R.wrap(value)
          val expiry = computeExpiry(nowNano, ttl)
          val displaced = collectDisplacedSignals(state.entries, _ == key)
          val cleaned = cleanBucket(state.entries, nowNano)
          val newEntries = Ready[K, R[V]](key, stored, expiry, null) :: cleaned.filterNot(_.key == key)
          (displaced, BucketState(state.invGen, state.closed, bumpKeyEpoch(state.keyEpochs, key), newEntries))
        }
      }) { promises =>
        F.void(F.traverse(promises)(_.succeed(None: Option[V])))
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
    F.terminate(new IllegalStateException("BIOCache: computeIfAbsent called after shutdown"))

  // Action tags for computeImpl dispatch (avoids GADT variance issues with sealed trait)
  private[this] val ActionHit = 0
  private[this] val ActionWait = 1
  private[this] val ActionCompute = 2
  private[this] val ActionClosed = 3 // cache is shut down; fail fast


  /** Wait-path context: the in-flight producer's promise, plus the two
    * monotonic barrier counters captured at park time.
    *
    * On wake with `Some(v)`:
    *   - if `globalEpoch` has advanced → invalidateAll/shutdown fired → retry;
    *   - else if the bucket's `gen` has advanced → a `put`/`invalidate`
    *     within this bucket fired → retry (includes rare false-positive
    *     retries on hash-colliding unrelated keys, which are cheap —
    *     ActionHit re-reads, no compute re-run);
    *   - otherwise → accept v (matching Ready, benign TTL/GC cleanup,
    *     successor compute — none of which bump either counter, so
    *     already-deduped waiters are never hijacked). */
  private[this] final class WaitCtx(val promise: AnyRef, val parkedInvGen: Long, val parkedKeyEpoch: Long)

  /** Bump the per-key barrier epoch. Called inside bucket modify closures
    * for `put` / `invalidate` so the epoch advance is atomic with the
    * bucket mutation. Waiters that captured the old epoch see the advance
    * on wake and take the retry path. */
  private[this] def bumpKeyEpoch(keyEpochs: Map[K, Long], key: K): Map[K, Long] = {
    keyEpochs.updated(key, keyEpochs.getOrElse(key, 0L) + 1)
  }

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
            cleaned.find(_.key == key) match {
              case Some(Ready(_, stored, _, _)) =>
                R.get(stored) match {
                  case Some(v) =>
                    ((ActionHit, v.asInstanceOf[AnyRef]), BucketState(state.invGen, state.closed, state.keyEpochs, cleaned))
                  case None =>
                    if (state.closed || closedFlag.get()) ((ActionClosed, null), BucketState(state.invGen, state.closed, state.keyEpochs, cleaned))
                    else {
                      // Replace the dead Ready with our Computing. This is NOT
                      // a freshness barrier (it's a benign-GC-triggered
                      // reload), so do NOT bump gen.
                      val newEntries = Computing[K, R[V]](key, promise, myOriginToken) :: cleaned.filterNot(_.key == key)
                      ((ActionCompute, null), BucketState(state.invGen, state.closed, state.keyEpochs, newEntries))
                    }
                }
              case Some(c: Computing[K, R[V]] @unchecked) =>
                val parkedInvGen = state.invGen
                val parkedKeyEpoch = state.keyEpochs.getOrElse(key, 0L)
                ((ActionWait, new WaitCtx(c.promise, parkedInvGen, parkedKeyEpoch)), BucketState(state.invGen, state.closed, state.keyEpochs, cleaned))
              case _ =>
                if (state.closed || closedFlag.get()) ((ActionClosed, null), BucketState(state.invGen, state.closed, state.keyEpochs, cleaned))
                else {
                  // Empty slot → install our Computing. Not a barrier, gen untouched.
                  val newEntries = Computing[K, R[V]](key, promise, myOriginToken) :: cleaned.filterNot(_.key == key)
                  ((ActionCompute, null), BucketState(state.invGen, state.closed, state.keyEpochs, newEntries))
                }
            }
          }) { case (tag, payload) =>
            if (tag == ActionHit) {
              F.pure(payload.asInstanceOf[V])
            } else if (tag == ActionWait) {
              val ctx = payload.asInstanceOf[WaitCtx]
              restore(awaitAndRetry(key, ttl, compute, ctx.promise, ctx.parkedInvGen, ctx.parkedKeyEpoch))
            } else if (tag == ActionClosed) {
              failClosed
            } else {
              doCompute(key, bucketRef, promise, myOriginToken, ttl, compute, restore)
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
    parkedInvGen: Long,
    parkedKeyEpoch: Long,
  ): F[E, V] = {
    val existingPromise = existingPromiseAnyRef.asInstanceOf[Promise2[F, Nothing, Option[V]]]
    F.flatMap(existingPromise.await) {
      case Some(v) =>
        // Post-wake freshness validation from a SINGLE atomic bucket read:
        // `state.closed`, `state.invGen`, and `state.keyEpochs(key)` all
        // come from one `bucketRef.get` snapshot. No inter-atomic race.
        //
        //   - state.closed → fail fast (shutdown reached this bucket).
        //   - state.invGen > parked → invalidateAll/shutdown reached this
        //     bucket → retry.
        //   - state.keyEpochs(key) > parked → put/invalidate on THIS key
        //     fired → retry.
        //   - Otherwise → accept v. Guava's loader-result dedup is
        //     preserved.
        F.flatMap(bucketFor(key).get) { state =>
          if (state.closed || closedFlag.get()) failClosed
          else if (state.invGen > parkedInvGen) computeImpl(key, ttl, compute)
          else if (state.keyEpochs.getOrElse(key, 0L) > parkedKeyEpoch) computeImpl(key, ttl, compute)
          else F.pure(v)
        }
      case None =>
        F.flatMap(bucketFor(key).get) { state =>
          if (state.closed || closedFlag.get()) failClosed
          else computeImpl(key, ttl, compute)
        }
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
            // `nowNano` is read INSIDE the `bucketRef.update_` closure below so that a
            // CAS retry (triggered by contention on the bucket) uses a fresh clock
            // reading for `cleanBucket`'s TTL filter. Capturing `nowNano` once before
            // the CAS would let an already-expired put-Ready appear live on the retry
            // path, causing the producer to skip publication behind a stale entry.
            // `computeExpiry` is evaluated once at publish time — fine, since the
            // expiry is relative to the wall clock at insertion.
            F.flatMap(F.sync(System.nanoTime())) { insertNano =>
              val storedV = R.wrap(v)
              val expiry = computeExpiry(insertNano, ttl)
              // Guava-style producer-result semantics: the caller invoked `compute` and
              // produced `v`; both the caller and parked waiters receive `v`, regardless
              // of whether a racing put/invalidate displaced the bucket. The cache state
              // may diverge (only when a racing put has already installed its own Ready)
              // — that only affects NEW callers, not this in-flight load's waiters.
              //
              // Publication rule: publish `Ready(v)` ONLY IF our own `Computing` is
              // still in the slot. Anything else (empty slot, foreign `Computing`,
              // any `Ready`) blocks publication.
              //
              //   - Own `Computing` present → publish. Normal happy path.
              //   - Empty slot → DO NOT publish. Our Computing was removed by some
              //     control-plane op (`invalidate`/`invalidateAll`/`shutdown`) or by
              //     the failure-path of a successor that transiently held the slot.
              //     Either way, "our Computing is gone" is strong evidence that a
              //     freshness barrier (or termination) fired since we started; a load
              //     that began before that barrier must not repopulate the cache, or
              //     it would resurrect pre-invalidate data past an explicit refresh
              //     boundary even when the replacement load failed.
              //   - Foreign `Computing` → DO NOT publish. A successor is refreshing;
              //     it is authoritative.
              //   - Any foreign `Ready` → DO NOT publish. `put` is authoritative;
              //     a newer compute-produced `Ready` is fresher than ours.
              //
              // Cost: if `invalidate` fires mid-compute and no successor runs, our
              // successful load is NOT cached — the next caller recomputes. This is
              // an intentional tradeoff for strict freshness. Loader-result semantics
              // are preserved for the producer's direct caller (it still receives
              // `v`); only cache state and waiter signals diverge.
              //
              // Under weak/soft caches, the promise pins V strongly while alive, but
              // the promise is NOT referenced from the cache (Ready.origin is the
              // separate `originToken`). Once the producer fiber and all parked
              // waiters finish, the promise is GC-eligible and V is free to be
              // reclaimed through the cache's weak/soft wrapper as usual.
              // Publish Ready iff own Computing still present. The waiter-side
              // freshness check is the generation counter — displacers that arrive
              // after we publish will bump gen in their own modify, forcing any
              // parked waiter that wakes with Some(v) onto the retry path. No
              // promise ref needs to live inside Ready.
              F.flatMap(bucketRef.modify { state =>
                val retryNano = System.nanoTime()
                val cleaned = cleanBucket(state.entries, retryNano)
                if (state.closed || closedFlag.get()) {
                  (false, BucketState(state.invGen, state.closed, state.keyEpochs, cleaned))
                } else {
                  val ownsComputing = cleaned.exists(e => e.key == key && isOwnedComputing(e, promiseRef))
                  if (ownsComputing) {
                    // Publish our Ready. This is NOT a freshness barrier
                    // (it's the resolution of our own in-flight compute), so
                    // do NOT bump gen.
                    (true, BucketState(state.invGen, state.closed, state.keyEpochs, Ready[K, R[V]](key, storedV, expiry, originToken) :: cleaned.filterNot(_.key == key)))
                  } else {
                    (false, BucketState(state.invGen, state.closed, state.keyEpochs, cleaned))
                  }
                }
              }) { published =>
                val payload: Option[V] = if (published) Some(v) else None
                F.map(F.void(myPromise.succeed(payload)))(_ => v)
              }
            }
          case _: Exit.FailureUninterrupted[?] =>
            // Computation failed — remove Computing entry (if we still own it), signal
            // waiters with `None` (no value available → they retry via computeImpl).
            F.flatMap(bucketRef.update_ { state =>
              BucketState(state.invGen, state.closed, state.keyEpochs, state.entries.filterNot(e => e.key == key && isOwnedComputing(e, promiseRef)))
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
          BucketState(state.invGen, state.closed, state.keyEpochs, state.entries.filterNot(isOurEntry))
        }) { _ =>
          F.void(myPromise.succeed(None: Option[V]))
        }
      },
    )
  }

  override def invalidate(key: K): F[Nothing, Unit] = {
    // Per-key barrier: remove entries for `key` and bump the bucket's `gen`.
    // Waiters parked in this bucket detect the gen advance on wake and retry.
    F.uninterruptible(
      F.flatMap(bucketFor(key).modify { state =>
        if (state.closed || closedFlag.get()) {
          (Nil: List[Promise2[F, Nothing, Option[V]]], state)
        } else {
          val nowNano = System.nanoTime()
          val displaced = collectDisplacedSignals(state.entries, _ == key)
          val cleaned = cleanBucket(state.entries, nowNano)
          (displaced, BucketState(state.invGen, state.closed, bumpKeyEpoch(state.keyEpochs, key), cleaned.filterNot(_.key == key)))
        }
      }) { promises =>
        F.void(F.traverse(promises)(_.succeed(None: Option[V])))
      }
    )
  }

  override def invalidateAll: F[Nothing, Unit] = {
    // Per-bucket global barrier: traverse every bucket and update its
    // state atomically in one CAS — bump `invGen`, drop `keyEpochs`, clear
    // entries. Each bucket's update is linearizable with any concurrent
    // per-bucket operation (put/invalidate/computeImpl modify). Like
    // `java.util.ConcurrentHashMap.clear`, invalidation is NOT atomic
    // across the whole cache: concurrent callers on buckets the traversal
    // hasn't reached yet may still observe pre-flush state. This is the
    // fundamental tradeoff of bucket-partitioned caches.
    F.uninterruptible(
      F.flatMap(F.traverse(buckets.toList) { ref =>
        ref.modify { state =>
          if (state.closed || closedFlag.get()) {
            (Nil: List[Promise2[F, Nothing, Option[V]]], state)
          } else {
            val displaced = collectDisplacedSignals(state.entries, _ => true)
            (displaced, BucketState(state.invGen + 1, state.closed, Map.empty[K, Long], Nil: List[BucketEntry[K, R[V]]]))
          }
        }
      }) { perBucketPromises =>
        F.void(F.traverse(perBucketPromises.flatten)(_.succeed(None: Option[V])))
      }
    )
  }

  override def size: F[Nothing, Int] = {
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      F.map(F.traverse(buckets.toList) { ref =>
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
      F.map(F.traverse(buckets.toList) { ref =>
        F.map(ref.get) { state =>
          state.entries.collect {
            case r @ Ready(k, _, expiresAtNano, _) if !isExpired(expiresAtNano, nowNano) && R.get(r.stored.asInstanceOf[R[V]]).isDefined => k
          }
        }
      })(_.flatten.toSet)
    }
  }

  override def shutdown: F[Nothing, Unit] = {
    // End-to-end uninterruptible: once shutdown starts, it MUST reach the bucket
    // sweep + promise signaling step. An interrupt delivered after reading
    // `evictionFiberRef` but before the sweep would leave Computing markers in
    // place and parked waiters wedged — defeating the liveness guarantee.
    //
    // Steps (atomic w.r.t. caller interruption):
    //   1. Set `closedRef := true`. This fences future `computeIfAbsent` calls and
    //      released waiter-retries: they see the closed flag in `checkOpen` and fail
    //      fast with IllegalStateException rather than starting a fresh loader after
    //      teardown. Without this flag, signaling `None` to parked waiters would let
    //      them retry via `computeImpl` and spawn a new compute after shutdown — a
    //      duplicate-load hazard for side-effecting loaders.
    //   2. Stop the background eviction fiber, if any.
    //   3. Sweep every bucket: remove `Computing` entries AND signal their promises
    //      with `None`. Combined with step 1, released waiters observe "closed" and
    //      fail fast (no retry, no new compute). `put`/`get`/`invalidate*` remain
    //      functional post-shutdown because they do not trigger loaders.
    //
    // Producer fibers themselves are NOT interrupted from here; the caller is
    // expected to cancel them via their own supervision (timeouts, fiber scope).
    // This step only releases waiters.
    F.uninterruptible(
      // Step 1: flip the cache-wide `closedFlag` BEFORE any bucket
      // traversal. All subsequent `computeImpl` admissions check this
      // flag inside their modify closure and fail fast, even on buckets
      // whose `state.closed` has not yet been written by step 3. This is
      // the fence that Codex's "shutdown does not fence new loads"
      // finding requires.
      F.flatMap(F.sync(closedFlag.set(true))) { _ =>
        F.flatMap(evictionFiberRef.get) { fiberOpt =>
        F.flatMap(fiberOpt match {
          case Some(fiber) => fiber.interrupt
          case None => F.unit
        }) { _ =>
          // Step 3: per-bucket terminal close. Each bucket's modify sets
          // `closed=true`, bumps `invGen`, drops `keyEpochs`, and clears
          // `entries`. Computing promises are signaled `None` so parked
          // waiters wake; their post-wake snapshot has `state.closed=true`
          // → they fail fast. New `computeIfAbsent` calls on already-closed
          // buckets take the `ActionClosed` path; calls on buckets the
          // traversal hasn't reached yet may still succeed (per-bucket
          // non-atomic semantics, documented).
          F.flatMap(F.traverse(buckets.toList) { ref =>
            ref.modify { state =>
              val displaced = collectDisplacedSignals(state.entries, _ => true)
              (displaced, BucketState(state.invGen + 1, closed = true, Map.empty[K, Long], Nil: List[BucketEntry[K, R[V]]]))
            }
          }) { perBucketPromises =>
            F.void(F.traverse(perBucketPromises.flatten)(_.succeed(None: Option[V])))
          }
        }
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
      F.traverse_(buckets.toList) { ref =>
        ref.update_ { state =>
          BucketState(state.invGen, state.closed, state.keyEpochs, cleanBucket(state.entries, nowNano))
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
  *
  *               Freshness races (producer publishes, control-plane op arrives
  *               between publish commit and producer's `succeed(Some(v))`) are handled
  *               by the per-key epoch counter in [[BucketState#keyEpochs]], not by
  *               any field on `Ready` — see [[BucketState]] scaladoc and
  *               [[ConcurrentHashMapCache.awaitAndRetry]].
  */
private[bio] final case class Ready[K, S](key: K, stored: S, expiresAtNano: Long, origin: AnyRef) extends BucketEntry[K, S]

/** Per-bucket state wrapper. All freshness-barrier flags live INSIDE
  * this structure so that waiters and ops can make decisions from a
  * SINGLE atomic snapshot read.
  *
  *   - `invGen`: monotonic counter bumped by [[ConcurrentHashMapCache.invalidateAll]]
  *     and [[ConcurrentHashMapCache.shutdown]] inside this bucket's
  *     atomic modify. Per-bucket global-barrier marker.
  *   - `closed`: flipped to `true` inside [[ConcurrentHashMapCache.shutdown]]'s
  *     per-bucket modify. Per-bucket shutdown marker.
  *   - `keyEpochs`: per-key monotonic counter bumped ATOMICALLY inside
  *     `put(k)` / `invalidate(k)` bucket-modify closures. Per-key scoping
  *     eliminates hash-collision false-positive retries: a write on a
  *     DIFFERENT key does not touch this key's epoch.
  *
  * Because all three live in the same `BucketState` and every modifier
  * uses the same `Ref2` CAS, a single `bucketRef.get` yields a mutually
  * consistent snapshot — there is no inter-atomic race window for
  * waiter decisions. The tradeoff: `invalidateAll` / `shutdown` operate
  * per-bucket (matching `java.util.ConcurrentHashMap.clear` semantics)
  * rather than atomically across the whole cache. In-flight operations
  * whose bucket has not yet been visited can still complete normally.
  *
  * Waiters capture `(invGen, keyEpochs.getOrElse(key, 0L))` at park time.
  * On wake with `Some(v)` they compare against the current snapshot: an
  * advance in either counter forces a retry. `closed` forces a fail-fast.
  *
  * Benign bucket operations (computeIfAbsent, get, cleanBucket, TTL/GC
  * cleanup, Ready publication by doCompute) do NOT bump any counter.
  *
  * Memory: `keyEpochs.size` is bounded per bucket by the number of
  * distinct keys `put`/`invalidate`d since the last `invalidateAll` /
  * `shutdown`. For adversarial workloads that hammer `invalidate` with
  * ever-new keys without ever calling `invalidateAll`, operators should
  * invoke `invalidateAll` periodically to reclaim barrier metadata.
  */
private[bio] final case class BucketState[K, S](
  invGen: Long,
  closed: Boolean,
  keyEpochs: Map[K, Long],
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
  *                of the waiter freshness check — that uses `globalEpoch`
  *                and per-bucket `gen` counters (see
  *                [[ConcurrentHashMapCache.awaitAndRetry]]).
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
    F.flatMap(F.traverse((0 until n).toList)(_ => P.mkRef(BucketState[K, R[V]](0L, false, Map.empty[K, Long], Nil)))) { bucketList =>
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
    F.flatMap(F.traverse((0 until n).toList)(_ => P.mkRef(BucketState[K, R[V]](0L, false, Map.empty[K, Long], Nil)))) { bucketList =>
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
            //     atomic: `shutdown` can always find the fiber once the block completes.
            //   - `guaranteeOnFailure` is a belt-and-braces catch for the small window
            //     where a caller-level interrupt is delivered as the `uninterruptible`
            //     region exits (e.g., a masked interrupt latches until unmask, then
            //     propagates before the caller's outer `flatMap` can install its own
            //     bracket). If that happens, cleanup reads the now-populated fiberRef
            //     and interrupts the eviction fiber, preventing a leak.
            //
            // Caller contract (still required): wrap construction in a bracket/Resource
            // that calls `shutdown` on scope exit. The internal safety net only covers
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
