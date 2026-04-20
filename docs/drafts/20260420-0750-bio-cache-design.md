# BIOCache — Design Document

**Branch:** `wip/bio-cache`
**Primary file:** `fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/cache/ConcurrentHashMapCache.scala`
**Trait:** `izumi.functional.bio.BIOCache[F[+_,+_], K, V]`
**Companion document:** `20260420-0750-bio-cache-codex-review-ledger.md` (audit trail of adversarial review findings and mitigations)

## 1. Goals

A concurrent in-memory cache for BIO (bifunctor IO) code that is:

- **Sound under concurrency.** No hangs, no silent loss, no stale data across explicit refresh barriers, no double-execution of side-effecting loaders without explicit caller consent.
- **Guava-compatible for `computeIfAbsent`.** A caller that supplies a loader `compute` receives the value THAT loader produced, even if a concurrent `put` races and installs a different value. The cache can diverge from the caller's return value; the dedup contract belongs to the CALLER.
- **Cross-platform (JVM + Scala.js).** No JVM-only primitives where avoidable; when used, must be Scala.js-safe (`AtomicBoolean`, `AtomicLong`, immutable data).
- **Cross-Scala (2.12 / 2.13 / 3.7).** No syntax or stdlib-API features that require Scala 3 only.
- **Abstract over the effect type.** Works for any `F[+_,+_]` that has `IO2`, `Primitives2`, `Temporal2`, `Fork2`. No hard dependency on ZIO / cats-effect / monix.

## 2. Priorities (explicit, in order)

The following priority order was set explicitly by the user and drove every design tradeoff:

1. **Soundness.** Hangs are unacceptable. Strict freshness across explicit barriers. No missed invalidations. No silently lost writes.
2. **Performance.** Per-bucket concurrency, lock-free CAS modify, no global serialization. But `uninterruptible` / `uninterruptibleExcept` are used liberally where they simplify the reasoning.
3. **Interruption propagation: LOW.** Producer fibers are NOT interrupted by control-plane ops. The caller who invoked `compute` keeps running to completion; control-plane ops only release parked WAITERS (via promise signaling). Callers needing producer cancellation use their own supervision (timeouts, fiber scope).

Memory is treated as between (2) and (3): per-bucket memory must stay bounded by live state under typical workloads; adversarial patterns can require operator action (e.g. periodic `invalidateAll` or cache recreation) — but not unbounded growth under realistic use.

## 3. Non-goals

- **Cache-wide atomicity** for `invalidateAll` and `shutdown`. Both are per-bucket (matching `java.util.ConcurrentHashMap.clear` semantics). An atomic flush requires constructing a new cache.
- **Loader cancellation.** When shutdown happens while a loader is running, the loader runs to completion; shutdown releases only waiters, not producers. This is derived from priority (3).
- **Exact linearizability across all public ops.** Operations are per-bucket linearizable; cache-wide operations are serialized only with respect to their own bucket's traversal step.

## 4. Public API

```scala
trait BIOCache[F[+_,+_], K, V] {
  def get(key: K):                                       F[Nothing, Option[V]]
  def put(key: K, value: V):                             F[Nothing, Unit]
  def putWithTTL(key: K, value: V, ttl: FiniteDuration): F[Nothing, Unit]
  def computeIfAbsent[E](key: K, compute: F[E, V]):      F[E, V]
  def computeIfAbsentWithTTL[E](k: K, ttl: FiniteDuration, compute: F[E, V]): F[E, V]
  def invalidate(key: K):    F[Nothing, Unit]
  def invalidateAll:         F[Nothing, Unit]
  def size:                  F[Nothing, Int]
  def keys:                  F[Nothing, Set[K]]
  def shutdown:              F[Nothing, Unit]  // TERMINAL close
}
```

Construction:

```scala
BIOCache.make[F, K, V](config):                           F[Nothing, BIOCache[F, K, V]]  // lazy
BIOCache.makeEager[F, K, V](config):                      F[Nothing, BIOCache[F, K, V]]  // eager eviction fiber
BIOCache.makeEagerResource[F, K, V](config):              Lifecycle[F[Nothing, *], BIOCache[F, K, V]]
```

Cache ref types:
- `StrongRef[V]` — holds V strongly (default).
- `WeakCacheRef[V]` — holds V via `java.lang.ref.WeakReference`.
- `SoftCacheRef[V]` — holds V via `java.lang.ref.SoftReference`.

## 5. Core architecture

### 5.1 Bucket partitioning

The cache is an immutable `Vector[Ref2[F, BucketState[K, R[V]]]]` of `config.initialCapacity` buckets (minimum 1). A key hashes to a bucket via the standard ConcurrentHashMap spread (`hash ^ (hash >>> 16)`, floor-mod by bucket count). Each bucket is an independent atomic — concurrent ops on different buckets have zero contention.

### 5.2 Bucket state (the source of truth)

```scala
final case class BucketState[K, S](
  invGen:    Long,          // incremented by invalidateAll / shutdown on THIS bucket's visit
  closed:    Boolean,       // flipped by shutdown on THIS bucket's visit
  keyEpochs: Map[K, Long],  // per-key counter bumped by put(k) / invalidate(k)
  entries:   List[BucketEntry[K, S]],
)
```

All freshness-barrier state lives INSIDE `BucketState`. A single `bucketRef.get` yields a mutually consistent snapshot of `closed`, `invGen`, `keyEpochs`, and `entries` — no inter-atomic race between separate counters.

Supplemented by one cache-wide atomic:

```scala
private val closedFlag: AtomicBoolean = new AtomicBoolean(false)
```

flipped at the START of `shutdown` (before bucket traversal). Read at admission points in `computeImpl` to fence new loads on buckets the traversal has not yet visited. Monotonic — once `true`, stays `true`.

### 5.3 Bucket entries

```scala
sealed trait BucketEntry[K, +S] { def key: K }

final case class Ready[K, S](
  key:           K,
  stored:        S,                  // R[V] — may be Strong/Weak/SoftCacheRef
  expiresAtNano: Long,               // Long.MaxValue = no TTL
  origin:        AnyRef,             // producer identity (null for put-installed Ready)
) extends BucketEntry[K, S]

final case class Computing[K, S](
  key:         K,
  promise:     AnyRef,  // Promise2[F, Nothing, Option[V]] as AnyRef
  originToken: AnyRef,  // per-call identity token
) extends BucketEntry[K, Nothing]
```

No `Tombstone` variant — earlier design iterations tried tombstones; they were replaced by the `keyEpochs` map because tombstones accumulated unboundedly, required wall-clock aging (correctness-hazardous), and could be erased by same-key follow-up mutations. See the review ledger for full history.

### 5.4 Producer / waiter dedup

Concurrent `computeIfAbsent` calls on the same key share a single `Promise2[F, Nothing, Option[V]]`:
- The first caller installs `Computing(k, promise, originToken)` in the bucket and becomes the PRODUCER. It runs `compute` under `restore(...)` inside an `uninterruptibleExcept` region.
- Subsequent callers find the `Computing`, capture `(promise, parkedInvGen, parkedKeyEpoch)`, and await the promise. They are WAITERS.
- The promise carries `Option[V]`:
  - `Some(v)` on producer success — waiter returns `v` directly (unless freshness check forces retry).
  - `None` on producer failure / interruption / displaced-by-control-plane — waiter retries via `computeImpl` (which dedups again).

Waiters read the value directly from the promise payload, NOT from the bucket. This makes the handoff immune to bucket mutations that race with the producer's signal (TTL sweeps, eager-eviction fiber, concurrent put/invalidate).

### 5.5 Freshness barriers

A waiter woken with `Some(v)` reads ONE bucket snapshot and checks:

```
if state.closed                                  → failClosed
if state.invGen > parkedInvGen                   → retry
if state.keyEpochs.getOrElse(k, 0L) > parkedKeyEpoch → retry
otherwise                                        → return v
```

Counters bumped by:
- `put(k)`, `invalidate(k)`: bump `keyEpochs(k)` atomically with entry mutation.
- `invalidateAll`: per-bucket modify bumps `invGen`, resets `keyEpochs`, clears `entries`.
- `shutdown`: flips cache-wide `closedFlag`; then per-bucket modify sets `state.closed = true`, bumps `invGen`, resets `keyEpochs`, clears `entries`.

Counters NOT bumped by:
- `computeIfAbsent` (Computing install, Ready publish).
- `get`, `size`, `keys`.
- `cleanBucket` (TTL expiry, weak/soft GC cleanup).
- The eager-eviction fiber.

The distinction is semantic: only **user-intent barriers** bump counters. Benign internal plumbing never does, so already-deduped waiters are never hijacked by successor computes / TTL sweeps / GC reclamation.

### 5.6 Producer publication (Guava-style)

On `compute` success, the producer's `doCompute` runs:

```
bucketRef.modify { state =>
  if (state.closed || closedFlag.get())               → don't publish, signal None
  if cleaned contains Computing(k, p=ourPromise, _)   → publish Ready(k, v, expiry, originToken)
                                                         and signal Some(v)
  otherwise                                           → don't publish, signal None
}
```

"Own Computing still present" is the publication predicate. Anything else blocks publication:
- Empty slot → our Computing was removed by a control-plane op or a successor's failure.
- Foreign Computing → a successor is refreshing.
- Foreign Ready → put or newer compute is authoritative.

**The producer's CALLER always receives its own `compute`'s `v`**, even when publication is blocked. Waiters receive the same `v` via the promise payload (modulo freshness check). Cost: a successful load that races with `invalidate` may not be cached — the next caller recomputes. This is an intentional tradeoff for strict freshness.

### 5.7 TTL and cache ref types

```scala
case class CacheConfig(
  initialCapacity:       Int                = 16,
  defaultTTL:            Option[FiniteDuration] = None,
  eagerEvictionInterval: Option[FiniteDuration] = None,
)
```

- **TTL:** `Ready.expiresAtNano` is the wall-clock deadline (`Long.MaxValue` = never). `cleanBucket` filters expired entries inside every bucket-modifying op. `System.nanoTime()` is read INSIDE the modify closure on every CAS retry (avoids stale-clock hazard: a `Ready` that expires during a CAS retry could otherwise be treated as live on the successful retry).
- **Weak/soft refs:** `R.get(stored).isDefined` check in `cleanBucket` drops Ready entries whose wrapped value has been GC-reclaimed. The Promise's payload pins `v` strongly for its (short) lifetime — dedup stays correct under GC pressure, but the Ready's long-lived reference is the weak/soft wrapper.
- **Eager eviction:** `createWithEviction` forks a Temporal fiber that periodically runs `evictExpired`. Construction is leak-safe: `uninterruptible` around fork+register, plus `guaranteeOnFailure` to interrupt a leaked fiber if construction is cancelled.

## 6. Key design choices

### 6.1 Monotonic counters instead of tombstones

**Choice:** Track barriers as monotonic `Long` counters (`invGen`, `keyEpochs(k)`), not as `Tombstone` bucket entries.

**Why:** Earlier iterations used `Tombstone[K, S](key, invalidatedToken, createdAtNano)` entries in the bucket. This design accumulated three structural problems:
- **Unbounded retention without aging.** Tombstones stay until actively overwritten; an invalidate-then-never-touch-again key retains its tombstone indefinitely.
- **Wall-clock aging hazards.** Adding a TTL (tried 60s, 1h) makes correctness dependent on STW pauses / OS suspend. Any waiter parked longer than the cap silently loses its barrier.
- **Erasure by same-key follow-ups.** A follow-up `put`/`invalidate` that only displaces `Ready`/`Computing` (not a prior tombstone) could drop older tombstones that were still needed by parked waiters of earlier producers.

Monotonic counters sidestep all three: they never expire, never erase, and "larger than parked" is a well-defined total order that doesn't depend on reference identity or wall-clock.

### 6.2 Per-key epochs, not bucket-wide

**Choice:** `keyEpochs: Map[K, Long]` per bucket, not a single `bucketGen: Long`.

**Why:** A bucket-wide counter bumped on every same-bucket `put`/`invalidate` causes false-positive retries on hash-colliding unrelated keys. Under short-TTL or weak-ref caches, a false-positive retry can land on a cleaned-out bucket and spawn a second `compute` — a duplicate-load hazard for side-effecting loaders. Per-key epochs eliminate that: a `put` on key k2 bumps `keyEpochs(k2)` only, leaving `keyEpochs(k1)` untouched. Waiters parked on k1 never retry spuriously.

### 6.3 Single atomic snapshot for waiter freshness check

**Choice:** Waiters read `bucketFor(key).get` ONCE. `state.closed`, `state.invGen`, `state.keyEpochs(k)` all come from that single snapshot.

**Why:** Earlier designs used separate atomics (`AtomicBoolean closedFlag` + `AtomicLong globalEpoch` + bucket state). Reading them across multiple operations creates inter-atomic race windows: a write that lands between our first read and the decision branch is either missed entirely or produces inconsistent views. Embedding all barrier state in `BucketState` eliminates this class of bug — one atomic read = one linearizable snapshot. The cache-wide `closedFlag` is the exception, used only for admission fencing (where its purpose is to REJECT new loads, and any false-negative reject is just a pessimistic retry, not a correctness violation).

### 6.4 Promise payload handoff (not bucket read-back)

**Choice:** Waiters read `Option[V]` from the promise directly. They do NOT re-read the bucket to get `v`.

**Why:** Zero-TTL and eager-eviction caches can sweep a just-published `Ready` out of the bucket within microseconds of publication. If waiters had to read through the bucket to get `v`, they would see "not there" and spuriously retry — or worse, read a DIFFERENT successor's `Ready` and return its value, violating Guava dedup semantics. Promise payload bypass makes the handoff immune to bucket mutations.

The promise holds `Some(v)` strongly for its lifetime — dedup is preserved even under weak/soft caching. The promise is not referenced from any long-lived cache state (`Ready.origin` is a separate per-call `Object` token, not the promise), so the promise becomes GC-eligible as soon as producer + waiters release their references (typically microseconds).

### 6.5 Clock inside modify closures

**Choice:** `System.nanoTime()` is read INSIDE every `bucketRef.modify` / `update_` closure, not before.

**Why:** `Ref2.modify` retries on CAS conflict. If we snapshot `nowNano` once before entering `modify`, a CAS retry reuses that stale timestamp. Under contention, a `Ready` that expires between snapshot and retry appears live in `cleanBucket` → the retry path takes `ActionHit` on an expired entry. Re-reading the clock each retry eliminates that hazard.

### 6.6 Per-bucket `invalidateAll` and `shutdown`

**Choice:** Both operate bucket-by-bucket, NOT globally atomic.

**Why:** A truly atomic cache-wide op requires either a single `Ref[CacheState]` (destroying per-bucket concurrency) or a global lock (latency hit). Both are incompatible with priority (2). We chose to match `java.util.ConcurrentHashMap.clear` semantics: each bucket's transition is atomic; cross-bucket ordering is not guaranteed. A caller who needs atomic flush constructs a new cache.

For `shutdown`, the cache-wide `closedFlag` is set BEFORE bucket traversal to fence NEW load admissions even on not-yet-visited buckets. In-flight producers that started before the flag was flipped run to completion (consistent with priority (3)).

### 6.7 Terminal `shutdown` semantics

**Choice:** `shutdown` is a TERMINAL close, not an eviction-fiber stop.

**Why:** Per priority (1), a safety-critical cache cannot silently permit post-teardown loader execution. Side-effecting loaders could run after the cache is closed — a real hazard ("human lives depend on it"). Making `shutdown` terminal:
- Flips `closedFlag`; all subsequent admissions defect with `IllegalStateException`.
- Interrupts the eager-eviction fiber.
- Signals `None` to all in-flight producer promises; parked waiters wake and fail fast.
- Clears every bucket entry.

`put`/`invalidate`/`invalidateAll` become silent no-ops post-shutdown — these are `F[Nothing, Unit]` and have no channel to signal "closed" short of defect, which would be surprising for non-loader ops.

This is a deliberate API break from the pre-refactor trait where `shutdown` was eviction-fiber-only. Captured explicitly in the trait scaladoc.

### 6.8 `uninterruptible` / `uninterruptibleExcept` liberally

**Choice:** Every bucket-modifying control-plane op (`put`, `invalidate`, `invalidateAll`, `shutdown`) runs under `F.uninterruptible`. `computeImpl` runs under `F.uninterruptibleExcept { restore => ... }`, with `restore(compute)` for the loader and `restore(awaitAndRetry(...))` for the wait path.

**Why:** Per priority (3), interruption propagation is low. We accept uninterruptible regions where they simplify proving the "admission + bucket mutation + promise signal" step is atomic w.r.t. caller interruption. `computeImpl` escapes this via `restore` only for the loader body (so caller timeouts propagate into `compute`) and the wait path (so caller timeouts propagate into `await`).

`guaranteeOnFailure` cleans up a `Computing` entry and signals `None` on any interrupt / defect propagated through `restore`-wrapped regions — this is how we guarantee no orphan `Computing` is left in the bucket even if a caller gets cancelled mid-compute.

### 6.9 Producer returns its own `v`, unaffected by racing mutations

**Choice:** `doCompute` always returns the `v` that `compute` produced, regardless of whether publication succeeded.

**Why:** Guava loader-result semantics. A caller invoked `compute`, got back the fiber that runs `compute`, waits for it, and expects the result. A racing `put` or `invalidate` might divert the CACHE away from that `v`, but the CALLER is entitled to the loader's output. This is critical for side-effecting loaders: if loader allocated a resource, opened a connection, wrote a row — the caller must receive that handle, not a racing put's value.

Cost: cache state and the caller's return value can diverge momentarily. Documented in the `computeIfAbsent` scaladoc.

### 6.10 `invalidate` / `invalidateAll` / `put` do NOT preempt in-flight producers

**Choice:** These operations mutate bucket state and signal the displaced `Computing`'s promise with `None`, but they do NOT interrupt the producer fiber.

**Why:** Priority (3). Also, preempting would require tracking all producer fibers (significant complexity and memory overhead), and it would break Guava loader-result semantics (the producer's caller could receive an unexpected cancellation). Instead:
- Displaced `Computing`'s promise is signaled `None` → parked waiters wake and retry via `computeImpl`. Liveness preserved.
- Producer runs to completion on its own fiber. When it tries to publish, it sees `cleaned.exists(ownedComputing) == false` and silently doesn't publish. Signals its own promise with `None` (idempotent with the control-plane's earlier signal).
- Producer's caller receives its own computed `v`.

The user who wants to cancel a hung producer uses their own fiber supervision (`Temp.timeout`, structured concurrency, fiber scope). This is the documented recommended liveness pattern.

## 7. Memory profile

Per bucket:
- `invGen`, `closed`: 9 bytes.
- `keyEpochs`: one `(K, Long)` entry per distinct key ever `put`/`invalidate`d since the last `invalidateAll` / `shutdown`. Cleared on `invalidateAll` / `shutdown`.
- `entries`: a `List[BucketEntry]` of live Ready / Computing entries for this bucket's keys. Bounded by the cache's working set.

Cache-wide:
- `closedFlag`: 1 byte (AtomicBoolean).
- `evictionFiberRef`: a single `Ref2[F, Option[Fiber2]]`.

`keyEpochs` is the only structure that can exceed the cache's live-entry count. For typical workloads (bounded key universe, periodic `invalidateAll`), it tracks at most the distinct keys in the cache. For adversarial unique-key churn without periodic `invalidateAll`, it grows per touched key — operators should call `invalidateAll` on a schedule or recreate the cache. This is an explicit tradeoff (see review ledger §Accepted tradeoffs).

## 8. Concurrency semantics summary

| Operation             | Atomicity                   | Linearization                          | Interrupts producer? |
| --------------------- | --------------------------- | -------------------------------------- | -------------------- |
| `get`                 | per-bucket CAS              | at the read                            | n/a                  |
| `put` / `putWithTTL`  | per-bucket CAS              | at the CAS commit                      | No                   |
| `invalidate`          | per-bucket CAS              | at the CAS commit                      | No                   |
| `computeIfAbsent` admission | per-bucket CAS        | at the CAS commit (+ `closedFlag` double-check) | n/a |
| `computeIfAbsent` wait | awaits promise + per-bucket snapshot | at the snapshot read          | n/a                  |
| Producer publish      | per-bucket CAS              | at the CAS commit                      | n/a                  |
| `invalidateAll`       | per-bucket CAS per bucket   | per-bucket (NOT globally atomic)       | No                   |
| `shutdown`            | `closedFlag` flip + per-bucket CAS | `closedFlag` cache-wide; per-bucket otherwise | Eviction fiber: yes. Producers: no. |

## 9. What can still go wrong (honest limitations)

These are accepted per-design; they are not bugs but deliberate tradeoffs:

- **In-flight producer after shutdown.** If a producer's admission committed before `shutdown` flipped `closedFlag`, that producer runs to completion. Its side effects happen. Its caller receives `v`. The cache does not retain it (publication is blocked by `state.closed`). Callers needing hard termination must cancel their own fibers via fiber scope.

- **Pre-flush producer result after `invalidateAll`.** If a producer's bucket has not yet been visited by `invalidateAll`'s traversal, the producer can publish, signal, and wake waiters BEFORE the bucket's `invGen` is bumped. Those waiters observe the pre-flush snapshot and accept `v`. Use a new cache for atomic flush.

- **`keyEpochs` growth under adversarial unique-key churn.** Without periodic `invalidateAll`, the per-bucket `keyEpochs` map grows monotonically with distinct touched keys. Bounded by lifetime key cardinality, not live-entry count. See §7.

- **Sub-nanosecond read-then-decide window.** Between a waiter's atomic bucket read and its `if-else` branch, a put/invalidate can commit. The waiter decides with the captured (stale) snapshot. This is inherent to any read-then-decide on `AtomicReference`. Linearizability places our decision at the read; the put's linearizes strictly after.

- **Per-bucket `BucketState` CAS contention on hot keys.** All ops on keys hashing to the same bucket serialize through one `Ref2`. A hot key cannot scale beyond one core's CAS throughput. Increase `initialCapacity` for high-contention workloads.

## 10. Forward work

- **API split?** Consider `shutdown` (non-destructive, stop eviction fiber only) + `close` (terminal). Currently in-place. Reversible decision.
- **`keyEpochs.size` metric.** Expose for observability so operators can detect adversarial growth before it becomes a problem.
- **Atomic-flush variant.** If callers routinely need globally-atomic flush, consider a separate `AtomicBIOCache` backed by a single `Ref[CacheState]` — slower but atomic — alongside the current per-bucket design.
- **Producer tracking for cancel-on-shutdown.** Deliberately deferred per priority (3). Would enable hard cancellation at the cost of per-producer bookkeeping.
