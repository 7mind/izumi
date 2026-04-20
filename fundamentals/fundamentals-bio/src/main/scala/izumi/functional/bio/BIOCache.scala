package izumi.functional.bio

import izumi.functional.bio.cache.*

import scala.concurrent.duration.FiniteDuration

/** A concurrent, effect-aware cache with TTL support and configurable reference types.
  *
  * Guava-style semantics: similar to Google Guava's `LoadingCache`, adapted for bifunctor
  * IO effects.
  *
  * Key properties:
  *   - Concurrent access via lock-free hash map (array of buckets with atomic refs).
  *   - '''At-most-once''' `compute` invocation PER caller: each `computeIfAbsent`
  *     call invokes its own `compute` at most once. Concurrent callers dedup via a
  *     shared `Computing` marker.
  *   - '''Strict freshness barrier''' for `put`/`invalidate`/`invalidateAll`: an
  *     in-flight producer whose `Computing` is removed cannot publish its result.
  *   - '''No waiter hangs.''' Control-plane operations IMMEDIATELY signal any
  *     displaced `Computing`'s promise so parked waiters always wake and retry
  *     against the freshest cache state. A wedged producer cannot wedge its waiters.
  *   - Per-entry and global TTL with lazy or eager eviction strategies.
  *   - Configurable reference types (strong, weak, soft) via [[CacheRefType]] typeclass.
  *
  * ==Concurrency contract==
  *
  * '''Release semantics for control-plane operations.''' `put`/`invalidate`/
  * `invalidateAll` each atomically (a) mutate the bucket and (b) signal every
  * displaced `Computing`'s promise with `None`. Parked waiters wake, retry through
  * `computeImpl`, and observe the freshest cache state (hit on put's `Ready`, empty
  * slot after invalidate, etc.). The displaced producer itself still runs to
  * completion on its own fiber — its direct caller receives its computed `v`
  * (loader-result contract) — but its publish is rejected by the "own `Computing`
  * still in slot" check, and its promise signal is a no-op (the displacer already
  * signaled). Producer fibers are not interrupted by control-plane ops.
  *
  * '''At-most-once `compute` per caller.''' Each `computeIfAbsent` call invokes its
  * `compute` at most once even under racing control-plane ops. Across multiple
  * independent `computeIfAbsent` calls on the same key racing with `invalidate`,
  * each call's `compute` still runs at most once but the total compute invocations
  * across all calls can grow with the number of callers that miss after the
  * invalidate — this is intentional: `invalidate` explicitly communicates that the
  * prior value is stale, so a pre-invalidate load must not repopulate the cache.
  *
  * '''Waiter wake-up.''' Callers parked on another fiber's in-flight load wake when:
  *   - The producer fiber terminates (success / typed failure / interrupt).
  *   - Any of `put`/`invalidate`/`invalidateAll`/`shutdown` runs on the same key
  *     and displaces the producer's `Computing` from its bucket — they IMMEDIATELY
  *     signal the displaced promise `None`.
  *
  * Promise payload on wake:
  *   - `Some(v)` iff the producer's publication succeeded (slot empty or own
  *     `Computing` at publish time). Waiters consume `v` directly.
  *   - `None` otherwise (publication rejected, producer failed/interrupted, OR
  *     control-plane op displaced the producer). Waiters retry through
  *     `computeImpl` and observe the freshest cache state (a hit on put's `Ready`,
  *     a successor's `Computing`, an empty slot where they install their own
  *     `Computing`, or `IllegalStateException` if closed).
  *
  * Producer's direct caller always receives its own computed `v` (loader-result
  * contract); only waiters are retry-signaled when the slot changes hands.
  *
  * '''No waiter wedge.''' A hung producer cannot wedge its waiters: any
  * control-plane operation (including `shutdown`) releases them. The caller only
  * needs to worry about the producer's OWN liveness.
  *
  * Recommended pattern — apply `Temp.timeout` on every call:
  *
  * {{{
  *   Temp.timeout(5.seconds)(cache.computeIfAbsent(key, slowLoader))
  * }}}
  *
  * How it works:
  *   - If the '''producer's''' timeout fires (the first caller for the key — whose
  *     fiber actually runs `compute`), its fiber is interrupted. `doCompute`'s
  *     `guaranteeOnFailure` cleanup removes the Computing entry and signals the promise
  *     with `None`. Parked waiters wake, see `None`, and retry via a fresh
  *     `computeImpl` cycle (one of them runs `compute` anew; the rest dedup by bucket
  *     CAS). A single producer timeout therefore unblocks every waiter.
  *   - If a '''waiter's''' own timeout fires, the waiter's `promise.await` is
  *     interrupted and that waiter's fiber exits with interruption. No cleanup runs
  *     (the waiter never entered `doCompute`), so the Computing entry stays and peer
  *     waiters remain parked. Waiter timeouts provide per-call liveness but do not
  *     free peers.
  *   - `shutdown` also unblocks every parked waiter by signaling all in-flight
  *     promises `None` (released waiters fail with `IllegalStateException`, see
  *     [[BIOCache#shutdown]]).
  *
  * Consequence: apply `Temp.timeout` uniformly to every `computeIfAbsent` call.
  * Whichever call ends up being the producer will then provide the shared bound;
  * waiter timeouts still bound their own callers.
  *
  * '''Non-atomic invalidation.''' `invalidate`/`invalidateAll` clear cache state at call
  * time but are NOT global barriers — concurrent `put`/`computeIfAbsent` may repopulate
  * before the call returns. This matches Java's `ConcurrentHashMap.clear`. For an atomic
  * flush, construct a new cache.
  *
  * @tparam F bifunctor effect type
  * @tparam K key type
  * @tparam V value type (raw, unwrapped)
  */
trait BIOCache[F[+_, +_], K, V] {

  /** Get a value if present, not expired, and not garbage-collected.
    *
    * Returns the current cache state; concurrent mutations after return are a standard
    * concurrent-cache hazard and not specific to this method.
    */
  def get(key: K): F[Nothing, Option[V]]

  /** Put a value using the global default TTL (if configured).
    *
    * Replaces any existing entry for the key (Ready or in-flight Computing) with a
    * new Ready AND immediately signals the displaced Computing's promise (if any)
    * with `None` — releasing parked waiters. The modify + signal step is fully
    * uninterruptible, so the cache state and waiter-release are always consistent.
    *
    * '''Interaction with parked waiters.''' Waiters released by put retry through
    * `computeImpl` and hit put's `Ready`, returning put's value. The displaced
    * producer keeps running on its own fiber; its direct caller receives its
    * computed `v` (loader-result contract), but its publish is rejected and its
    * promise signal is a no-op.
    *
    * If you need deterministic ordering against a specific in-flight load, coordinate
    * at the caller layer (e.g., `invalidate` + fresh-call sequencing).
    */
  def put(key: K, value: V): F[Nothing, Unit]

  /** Put a value with an explicit TTL. See [[put]] for semantics (including the
    * timing-dependent parked-waiter interaction). */
  def putWithTTL(key: K, value: V, ttl: FiniteDuration): F[Nothing, Unit]

  /** Get the existing value or compute it. Concurrent `computeIfAbsent` calls for the
    * same key are deduplicated: one fiber is the producer (runs `compute`); others park
    * on the producer's promise and receive the result via the payload when the producer
    * completes — see the '''Waiter result handoff''' section below for the exact
    * payload semantics (success vs. failure vs. GC-reclaimed wrapper).
    *
    * '''At-most-once per call.''' `compute` is invoked at most ONCE per `computeIfAbsent`
    * call, even under racing `put`/`invalidate`/`invalidateAll`. The producer always
    * '''returns its own computed value''' to its direct caller (loader-result contract
    * for the fiber that invoked compute). Waiters see either `Some(ownV)` or `None`
    * depending on whether publication succeeded — see the '''Waiter result handoff'''
    * section. Safe for side-effecting loaders that tie V's lifecycle to the loader's
    * return value.
    *
    * '''Publication on success:''' publish `Ready(v)` only if our own `Computing` is
    * still in the slot. Anything else (empty slot, foreign `Computing`, or any
    * `Ready`) blocks publication — the freshness barrier is strict. Cost: if
    * `invalidate` fires mid-compute, our successful load is NOT cached and the next
    * caller recomputes, even if no successor ran. This is an intentional tradeoff:
    * `invalidate` explicitly communicates "the prior value is wrong", so a load
    * that started before the barrier is never allowed to repopulate. Displaced
    * in-flight producers still signal their own waiters via their own promises.
    *
    * '''Waiter liveness.''' Waiters parked on an in-flight producer wake when the
    * producer fiber completes (success/failure/interrupt) OR when any control-plane
    * operation (`put`/`invalidate`/`invalidateAll`/`shutdown`) on the same key
    * displaces the producer's `Computing` — those operations signal the displaced
    * promise `None` in the same uninterruptible step as the bucket mutation.
    * Callers therefore do NOT need `Temp.timeout` to rescue waiters from a hung
    * producer; any control-plane op is sufficient.
    *
    * '''Waiter result handoff.''' The producer signals its promise with:
    *   - `Some(v)` iff publication succeeded (slot was empty or held our own
    *     `Computing` at publish time). Parked waiters consume `v` directly and
    *     return it — no bucket read, no wrapper unwrap.
    *   - `None` iff publication was REJECTED (a foreign `Ready` or `Computing`
    *     now owns the slot — via racing `put`, post-invalidate successor, newer
    *     compute, or shutdown fence). Parked waiters retry through `computeImpl`:
    *     they then observe the freshest available state (racing put's `Ready`,
    *     successor's `Computing`, or an empty slot / closed cache).
    *
    * This is the key freshness invariant: waiters NEVER return a pre-invalidate
    * producer's value through a freshness boundary. Across N concurrent waiters
    * that all see `None` and retry on an empty bucket, the retry's bucket CAS
    * dedups them so compute runs at most once. Producer's direct caller still
    * receives its own `v` unconditionally (loader-result contract for the fiber
    * that invoked compute).
    *
    * Interruption: the compute itself and the waiter await run interruptibly, so caller
    * timeouts propagate cleanly.
    */
  def computeIfAbsent[E](key: K, compute: F[E, V]): F[E, V]

  /** Like [[computeIfAbsent]] but with an explicit TTL for the computed value. */
  def computeIfAbsentWithTTL[E](key: K, ttl: FiniteDuration, compute: F[E, V]): F[E, V]

  /** Remove a single entry present at call time (strict freshness barrier).
    *
    * Atomically (uninterruptibly) removes any `Ready` or `Computing` for the key AND
    * signals the displaced `Computing`'s promise (if any) with `None` — releasing
    * parked waiters immediately. NOT atomic w.r.t. concurrent writes: a racing `put`
    * or `computeIfAbsent` may add a new entry for this key before or after this call
    * returns.
    *
    * '''Interaction with in-flight loads.''' Parked waiters wake, retry through
    * `computeImpl`, and observe the freshest cache state. The displaced producer
    * keeps running: its direct caller receives its computed `v` (loader-result
    * contract); its publish is rejected (no own `Computing` in slot); its promise
    * signal is a no-op. Cost: a pre-invalidate producer's successful load is NOT
    * cached (the next caller recomputes) — intentional, since `invalidate`
    * explicitly communicates that the prior value is stale.
    */
  def invalidate(key: K): F[Nothing, Unit]

  /** Remove all entries present at call time (strict freshness barrier).
    *
    * Clears every bucket AND signals every displaced `Computing`'s promise `None`
    * in one uninterruptible step. Same strict-freshness + release semantics as
    * [[invalidate]]. NOT atomic w.r.t. concurrent writes: the traversal processes
    * buckets one at a time, so a `put`/`computeIfAbsent` on an already-cleared
    * bucket may repopulate before this call returns. For an atomic flush, construct
    * a new cache.
    */
  def invalidateAll: F[Nothing, Unit]

  /** Current number of non-expired, alive entries (approximate). */
  def size: F[Nothing, Int]

  /** All current keys (approximate snapshot). */
  def keys: F[Nothing, Set[K]]

  /** Full-close: terminal lifecycle operation that tears down the cache.
    *
    *   - Sets the cache's closed flag (fences new loader admission).
    *   - Stops the background eviction fiber (if any).
    *   - Per-bucket uninterruptible sweep: clears EVERY entry (both `Ready` and
    *     `Computing`) and signals every `Computing`'s promise `None`.
    *
    * Does '''NOT''' interrupt producer fibers themselves — producer lifecycle is
    * the caller's responsibility (supervision / timeouts / explicit cancellation).
    *
    * Post-shutdown behavior (all operations are safe to call; shutdown clears
    * every bucket, so the cache holds no state):
    *   - `computeIfAbsent` / `computeIfAbsentWithTTL` → every key miss fails
    *     with `IllegalStateException` (defect via `F.terminate`). Since shutdown
    *     sweeps every bucket to empty, all post-shutdown calls take the miss
    *     branch. (The implementation's hit branch is unfenced by design —
    *     hits never start a loader — but no hit is observable after a shutdown
    *     sweep unless a concurrent in-flight producer had completed publication
    *     before the sweep committed, which is a narrow harmless window: the
    *     sweep then removes that Ready on its next modify.)
    *   - `put` / `putWithTTL` / `invalidate` / `invalidateAll` → silent no-op.
    *     These operations cannot propagate a typed error, and a dropped
    *     post-shutdown mutation is safer than a fiber-killing defect for
    *     lifecycle-racing code.
    *   - `get` → returns `None` (cache is empty).
    *   - `size` → returns `0`. `keys` → returns empty set.
    *
    * Idempotent: calling shutdown twice is safe.
    *
    * '''Breaking change vs. traditional "eviction-fiber-only" shutdown''': this
    * cache treats shutdown as a terminal liveness fence, not a benign finalizer.
    * The defect on post-shutdown `computeIfAbsent` is intentional — it prevents
    * duplicate loader execution after teardown, which is a correctness hazard
    * under side-effecting loaders. Wrap cache construction in a bracket so
    * `shutdown` is always the last operation:
    *
    * {{{
    *   F.bracket(BIOCache.makeEager(config))(_.shutdown)(cache => use(cache))
    * }}}
    */
  def shutdown: F[Nothing, Unit]
}

object BIOCache {

  /** Create a cache with strong references and lazy TTL eviction.
    *
    * Expired entries are removed on read. No background fiber is started.
    */
  def make[F[+_, +_]: IO2: Primitives2, K, V](config: CacheConfig): F[Nothing, BIOCache[F, K, V]] = {
    ConcurrentHashMapCache.create[F, K, StrongRef, V](config)
  }

  /** Create a cache with a custom reference type and lazy TTL eviction.
    *
    * Use with [[cache.WeakCacheRef]] or [[cache.SoftCacheRef]] (JVM only) for GC-aware caching.
    *
    * '''Weak/soft dedup contract.''' The producer's promise carries `Option[V]` (raw
    * V, not the `R[V]` wrapper). Parked waiters receive `Some(v)` directly from the
    * promise — bypassing the cache's weak/soft wrapper entirely — so dedup is never
    * broken by GC pressure: across N concurrent waiters on a single in-flight load,
    * `compute` runs exactly ONCE regardless of whether the wrapper is cleared between
    * publish and wake.
    *
    * '''GC-eligibility.''' The promise holds `v` strongly for its lifetime, but the
    * promise is NOT referenced from any long-lived cache state: the `Ready` entry
    * stores a separate lightweight origin token (an `Object`), NOT the promise. Once
    * the producer fiber and all parked waiters release their local references to the
    * promise (typically microseconds after the producer returns), the promise becomes
    * GC-eligible and V is free to be reclaimed through the cache's weak/soft wrapper.
    * The weak/soft contract is preserved at the cache level; it is only suspended for
    * the short handoff window.
    */
  def makeWithRef[F[+_, +_]: IO2: Primitives2, K, R[_]: CacheRefType, V](
    config: CacheConfig
  ): F[Nothing, BIOCache[F, K, V]] = {
    ConcurrentHashMapCache.create[F, K, R, V](config)
  }

  /** Create a cache with strong references and eager TTL eviction.
    *
    * Starts a background fiber that periodically scans and evicts expired entries.
    *
    * '''Caller responsibility.''' Wrap the returned effect in a bracket (or equivalent
    * resource-scoped pattern) so that `shutdown` is always called on scope exit:
    *
    * {{{
    *   F.bracket(
    *     acquire = BIOCache.makeEager(config)
    *   )(release = _.shutdown)(
    *     use = cache => ...
    *   )
    * }}}
    *
    * Without a bracket, an interrupt delivered between `makeEager` returning and
    * the caller installing their own shutdown path will leak the background
    * eviction fiber. The internal construction masks interrupts around fork +
    * registration, but it cannot protect the handoff after the function returns —
    * that is the caller's responsibility.
    *
    * @param config must have `eagerEvictionInterval` set, otherwise behaves like [[make]]
    */
  def makeEager[F[+_, +_]: IO2: Primitives2: Temporal2: Fork2, K, V](
    config: CacheConfig
  ): F[Nothing, BIOCache[F, K, V]] = {
    makeEagerWithRef[F, K, StrongRef, V](config)
  }

  /** Create a cache with a custom reference type and eager TTL eviction. */
  def makeEagerWithRef[F[+_, +_]: IO2: Primitives2: Temporal2: Fork2, K, R[_]: CacheRefType, V](
    config: CacheConfig
  ): F[Nothing, BIOCache[F, K, V]] = {
    ConcurrentHashMapCache.createWithEviction[F, K, R, V](config)
  }
}
