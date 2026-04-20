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

1. **Soundness.** Hangs are unacceptable. No missed invalidations. No silently lost writes. Freshness across explicit barriers (`put` / `invalidate` / `invalidateAll` / `close`) holds for NEW callers — a fresh `get` / `computeIfAbsent` issued after the barrier returns sees the post-barrier state — AND for the producer's publish path (an in-flight producer whose `Computing` was displaced does NOT repopulate the cache past the barrier). For already-signaled waiters, the rule depends on the barrier kind: `put(k)` / `invalidate(k)` leave the bucket `Ref2` intact, so a waiter that already received `Some(v_A)` returns `v_A` (Guava `LoadingCache` loader-result dedup); `invalidateAll` / `close` swap the whole bucket structure, so the waiter's `parkedBucketRef` becomes orphan and the waiter is forced to retry (`invalidateAll`) or fail fast with `IllegalStateException` (`close`). The waiter-side strict-freshness property for `put`/`invalidate` was deliberately dropped in favor of simpler data structures — see §6.2 and the review ledger Round 26.
2. **Performance.** Per-bucket concurrency, lock-free CAS modify, no global serialization. But `uninterruptible` / `uninterruptibleExcept` are used liberally where they simplify the reasoning.
3. **Interruption propagation: LOW.** Producer fibers are NOT interrupted by control-plane ops. The caller who invoked `compute` keeps running to completion; control-plane ops only release parked WAITERS (via promise signaling). Callers needing producer cancellation use their own supervision (timeouts, fiber scope).

Memory is treated as between (2) and (3): per-bucket memory must stay bounded by live state under typical workloads; adversarial patterns can require operator action (e.g. periodic `invalidateAll` or cache recreation) — but not unbounded growth under realistic use.

## 3. Non-goals

- **Loader cancellation.** When close happens while a loader is running, the loader runs to completion; close releases only waiters, not producers. This is derived from priority (3).
- **Blocking concurrent readers on close / invalidateAll.** In-flight operations on the pre-swap structure complete against the orphaned vector. Their effects are invisible to post-swap readers, but they are NOT interrupted or failed.

## 4. Public API

```scala
trait BIOCache[F[+_,+_], K, V] {
  def get(key: K):                                       F[Nothing, Option[V]]
  def put(key: K, value: V):                             F[Nothing, Option[V]]  // returns previous live v
  def putWithTTL(key: K, value: V, ttl: FiniteDuration): F[Nothing, Option[V]]  // returns previous live v
  def computeIfAbsent[E](key: K, compute: F[E, V]):      F[E, V]
  def computeIfAbsentWithTTL[E](k: K, ttl: FiniteDuration, compute: F[E, V]): F[E, V]
  def invalidate(key: K):    F[Nothing, Unit]
  def invalidateAll:         F[Nothing, Unit]
  def size:                  F[Nothing, Int]
  def keys:                  F[Nothing, Set[K]]
  def toMap:                 F[Nothing, Map[K, V]]  // weakly-consistent snapshot
  def close:                 F[Nothing, Unit]  // TERMINAL close
}
```

`put` / `putWithTTL` return the live value that was in the slot at commit time (or `None` if the slot was empty / held `Computing` / held an expired Ready / had a GC-reclaimed weak wrapper / the cache is closed). `toMap` iterates the current bucket vector and collects live entries per-bucket-atomically — same weak-consistency guarantees as `size` / `keys` (matches Java `ConcurrentHashMap.entrySet` iterator semantics).

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

The cache owns an `AtomicReference[Vector[Ref2[F, BucketState[K, R[V]]]]]`:

```scala
private val structureRef: AtomicReference[Vector[Ref2[F, BucketState[K, R[V]]]]] = ...
```

Each `Ref2` is an independent atomic cell; concurrent ops on different buckets have zero contention. A key hashes to a bucket via the standard ConcurrentHashMap spread (`hash ^ (hash >>> 16)`, floor-mod by bucket count). **`bucketFor(key)` reads `structureRef` fresh each call** — this is the central move of the design: it makes `invalidateAll` / `close` O(1)-visible via a single `getAndSet` swap.

### 5.2 Bucket state

```scala
final case class BucketState[K, S](entries: List[BucketEntry[K, S]])
```

Per-bucket state is the entry list and nothing else — no `closed` flag, no `invGen` counter, no per-key `keyEpochs` map. Global barriers are detected via `Ref2` identity (the structure swap replaces the whole vector), and the cache-wide `closedFlag` lives outside `BucketState`.

Supplemented by one cache-wide atomic:

```scala
private val closedFlag: AtomicBoolean = new AtomicBoolean(false)
```

flipped at the START of `close` (before the structure swap). Read inside admission and publish modify closures to fence new loads / publications on buckets that an in-flight caller captured pre-swap. Monotonic — once `true`, stays `true`.

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

No `Tombstone` variant — tombstones (in early iterations) and their replacement, the per-key `keyEpochs: Map[K, Long]` barrier, were both deleted (see review ledger, Rounds 1 and 26). The cache now follows **Guava's loader-result dedup** for waiters: once a waiter is signaled `Some(v_A)` by its producer, it returns `v_A` regardless of any `put`/`invalidate` that linearizes after the signal. No per-key barrier counter is needed.

### 5.4 Producer / waiter dedup

Concurrent `computeIfAbsent` calls on the same key share a single `Promise2[F, Nothing, Option[V]]`:
- The first caller installs `Computing(k, promise, originToken)` in the bucket and becomes the PRODUCER. It runs `compute` under `restore(...)` inside an `uninterruptibleExcept` region.
- Subsequent callers find the `Computing`, capture `(promise, parkedBucketRef)` into a `WaitCtx`, and await the promise. They are WAITERS. `parkedBucketRef` is the `Ref2` they found the `Computing` in; on wake they compare it (`eq`) against `bucketFor(key)` to detect a structure swap by `invalidateAll` / `close`.
- The promise carries `Option[V]`:
  - `Some(v)` on producer success — waiter returns `v` directly (unless freshness check forces retry).
  - `None` on producer failure / interruption / displaced-by-control-plane — waiter retries via `computeImpl` (which dedups again).

Waiters read the value directly from the promise payload, NOT from the bucket. This makes the handoff immune to bucket mutations that race with the producer's signal (TTL sweeps, eager-eviction fiber, concurrent put/invalidate).

### 5.5 Freshness barriers

Waiters capture `parkedBucketRef` at park time. On wake with `Some(v)`:

```
if closedFlag.get()                              → failClosed
if bucketFor(k) ne parkedBucketRef               → retry   // structure was swapped
otherwise                                        → return v // Guava loader-result dedup
```

Two barrier mechanisms:
- **Cache-wide `closedFlag`** (AtomicBoolean): flipped by `close` BEFORE the structure swap. Checked in every admission point and on waiter wake.
- **Structure swap** (AtomicReference `getAndSet`): done by `invalidateAll` / `close`. Installs a fresh Vector of fresh `Ref2`s; the `eq` check on the bucket ref detects it.

`put(k)` / `invalidate(k)` are **not** waiter-side barriers. They displace in-flight `Computing(k, …)` by signaling the promise `None` — so waiters that hadn't yet been signaled wake and retry (and observe the post-displacement bucket state). Waiters that already received `Some(v_A)` from their producer keep `v_A` per Guava loader-result dedup. Callers that need to observe `put`'s newer value issue a fresh `get` / `computeIfAbsent`; they do not inherit a prior caller's in-flight parked decision.

### 5.5.1 Orphan-bucket admission guard

In-flight operations can hold a `bucketRef` captured pre-swap. After the swap, that `Ref2` is orphaned — not reachable via `currentBuckets`. Allowing admissions (`computeImpl`'s Computing install, `doCompute`'s Ready publish) on orphan buckets would:
- Leave entries nobody reads (harmless by itself).
- **Risk a hang:** if a later caller also holds the same orphan `Ref2`, it could park on a Computing's promise that no public `invalidateAll` / `close` will ever drain again (those operate on `currentBuckets`, not on prior orphans).

The admission guard operates in **two stages**:

**Stage 1 — inside the modify closure** (pre-CAS):

```scala
bucketRef.modify { state =>
  if (bucketRef ne bucketFor(key)) {
    // Structure was swapped since we captured bucketRef. Abort.
    (ActionSwapped, state)
  } else if (closedFlag.get()) {
    (ActionClosed, state)
  } else {
    // proceed with Computing install / Ready publish
  }
}
```

**Stage 2 — post-CAS rollback re-check** (after the modify commits):

```scala
// ActionCompute: Computing was just installed. Re-read the fences AFTER
// the CAS commit. Close's `closedFlag.getAndSet(true)` and
// `structureRef.getAndSet(emptyVec)` may have interleaved between the
// closure's reads and the CAS commit; Stage 1 cannot see that window.
if (closedFlag.get() || (bucketRef ne bucketFor(key))) {
  // Remove our Computing from the (now orphan) bucket and signal the
  // promise `None` so any waiter that parked on us wakes and retries.
  rollbackAndSignal(...)
  if (closedFlag.get()) failClosed else computeImpl(key, ttl, compute) // retry
} else {
  doCompute(...) // start the loader
}
```

Stage 1 is the fast path (rejects admission BEFORE the CAS, cheapest possible exit). Stage 2 closes the admission/close race window that Stage 1 cannot see: the closure's reads of `closedFlag` and `bucketFor(key)` are not part of the bucket CAS, so `close` can linearize between them and the CAS commit. Without Stage 2 such a caller admits `Computing` on an orphan bucket AND starts the loader — violating the terminal `close` contract that admissions are rejected before the loader runs.

Combined with `drainPromises`' CAS traversal on the orphaned vector, the two-stage guard eliminates both the orphan-hang class (waiters parked forever) and the post-close-loader-execution class (side-effecting loaders running after `close` returned).

### 5.6 Producer publication (Guava-style)

On `compute` success, the producer's `doCompute` runs:

```
bucketRef.modify { state =>
  if (bucketRef ne bucketFor(key))                    → don't publish, signal None   // structure was swapped; our bucket is orphan
  if (closedFlag.get())                               → don't publish, signal None   // cache is closed
  if cleaned contains Computing(k, p=ourPromise, _)   → publish Ready(k, v, expiry, originToken)
                                                         and signal Some(v)
  otherwise                                           → don't publish, signal None   // our Computing was displaced by a control-plane op
}
```

Publication requires ALL of: (a) `bucketRef` is still the live bucket for the key (`structureRef` was not swapped since admission), (b) `closedFlag` is false, AND (c) our own `Computing` marker (matched by promise identity) is still in the slot. Anything else blocks publication:
- Structure swapped → `bucketRef` is an orphan; publishing would land on an unreachable `Ref2`.
- Cache closed → terminal; no new state may be published.
- Empty slot → our Computing was removed by a control-plane op or a successor's failure.
- Foreign Computing → a successor is refreshing.
- Foreign Ready → put or newer compute is authoritative.

**The producer's CALLER always receives its own `compute`'s `v`**, even when publication is blocked. Waiters receive the same `v` via the promise payload (modulo the close / structure-swap checks on wake). Cost: a successful load that races with `invalidate` may not be cached — the next caller recomputes. This is an intentional tradeoff to keep the publish path strictly post-barrier-clean: pre-invalidate loads cannot repopulate the cache past the barrier for NEW callers.

### 5.7 TTL and cache ref types

```scala
case class CacheConfig(
  initialCapacity:       Int                = 16,
  defaultTTL:            Option[FiniteDuration] = None,
  eagerEvictionInterval: Option[FiniteDuration] = None,
)
```

- **TTL:** `Ready.expiresAtNano` is the wall-clock deadline (`Long.MaxValue` = never). `cleanBucket` filters expired entries inside every bucket-modifying op. `System.nanoTime()` AND `computeExpiry(...)` (for publishing) are read / computed INSIDE the modify closure, so every CAS retry uses a fresh clock — this avoids two related hazards:
  - **Stale-clock hazard:** a `Ready` that expires during a CAS retry could otherwise be treated as live on the successful retry.
  - **Shortened-TTL hazard:** if `expiry` is computed before the modify and the modify retries under contention, the published `expiresAtNano` is calculated against an old clock, shortening the advertised TTL arbitrarily (and possibly publishing an already-expired entry).
- **Weak/soft refs:** `R.get(stored).isDefined` check in `cleanBucket` drops Ready entries whose wrapped value has been GC-reclaimed. The Promise's payload pins `v` strongly for its (short) lifetime — dedup stays correct under GC pressure, but the Ready's long-lived reference is the weak/soft wrapper.
- **Eager eviction:** `createWithEviction` forks a Temporal fiber that periodically runs `evictExpired`. Construction is leak-safe: `uninterruptible` around fork+register, plus `guaranteeOnFailure` to interrupt a leaked fiber if construction is cancelled.

## 6. Key design choices

### 6.1 Atomic structure swap for `invalidateAll` and `close`

**Choice:** `invalidateAll` and `close` replace the entire bucket vector atomically via a single `AtomicReference.getAndSet`, producing a fresh Vector of fresh per-bucket `Ref2`s. The old vector is orphaned; its effects are invisible to any post-swap reader.

**Why:** Earlier iterations traversed buckets one at a time, setting a per-bucket `invGen` / `closed` marker. That required waiters to compare per-bucket counters, which in turn needed the counters to be embedded in each bucket state for atomicity — a noisy design. The swap collapses "reset every bucket" to one atomic operation and shifts detection from counter comparison to `Ref2` identity (`eq`). As a bonus, any per-bucket metadata is reclaimed automatically because the whole vector becomes unreachable.

### 6.2 Monotonic per-key counters instead of tombstones

**Choice:** Track per-key barriers (for `put(k)` / `invalidate(k)`) as a `Map[K, Long]` in each `BucketState`, not as `Tombstone` bucket entries.

**Why:** Earlier iterations used `Tombstone[K, S](key, invalidatedToken, createdAtNano)` entries, then replaced them with a per-key `keyEpochs: Map[K, Long]` barrier. Both were enforcing **strict freshness for waiters** — a waiter whose producer signaled `Some(v_A)` would retry past a later `put`/`invalidate`. Tombstones had unbounded retention and wall-clock aging hazards; `keyEpochs` had a per-bucket `Map[K, Long]` growing monotonically with distinct touched keys.

**Final decision (Round 26):** Drop the strict-freshness-for-waiters invariant entirely, following Guava's actual `LoadingCache` behavior. A waiter that received `Some(v_A)` returns `v_A` — the producer's own caller already received `v_A`, and forcing the waiter to retry just to see the post-signal `put`'s value has no caller-observable benefit. Memory dropped to zero; `BucketState` is now a single-field wrapper over `List[BucketEntry]`.

### 6.4 Bucket `Ref2` identity as the swap signal

**Choice:** Waiters capture their bucket's `Ref2` at park time (`parkedBucketRef: Ref2[F, BucketState]`). On wake they compare via `eq` to `bucketFor(key)`. A mismatch means the structure was swapped → retry.

**Why:** Every swap installs brand-new `Ref2` objects. Object identity (`if_acmpeq` on the JVM, the ECMAScript `===` on Scala.js) is a constant-time, zero-bookkeeping way to detect a global replacement. No counter comparison, no inter-atomic coordination — the swap IS the generation.

### 6.5 Two-stage admission guard (in-closure + post-CAS rollback)

**Choice:** Every admission path (`computeImpl` Computing install, `doCompute` Ready publish) checks `bucketRef ne bucketFor(key)` AND `closedFlag` INSIDE its `Ref2.modify` closure (Stage 1). `computeImpl` additionally re-checks BOTH fences AFTER the CAS commits and rolls back the installed `Computing` if either has moved (Stage 2). `doCompute`'s publish is naturally idempotent under a late swap — it simply does not publish — so it does not need a rollback step.

**Why:** The modify closure's reads of `closedFlag` and `bucketFor(key)` are not part of the bucket CAS. A caller whose Stage 1 reads evaluated both fences as "open" can still have `close` linearize between the closure's return and the CAS commit, winning the old-bucket CAS afterward. Stage 2 closes that race by linearizing the admission decision against the LAST atomic read (post-CAS):
- If either fence has moved by then, we roll back the just-installed Computing, signal its promise `None` (so any waiter that parked on us in the tiny window wakes and retries), and either fail-fast with `IllegalStateException` (closed) or re-enter `computeImpl` against the fresh structure (swapped).
- If both fences hold post-CAS, the admission's linearization point precedes any future close, so starting the loader is safe.

Combined with `drainPromises`' CAS-atomic traversal, the two-stage guard eliminates:
- The orphan-hang class (admission slips through → waiter parks on a promise nobody will drain). Stage 1 catches the common case; Stage 2 + drain's CAS together cover the race window.
- The post-close-loader-execution class (admission slips through → side-effecting loader runs after `close` returned). Stage 2's re-check and rollback guarantees that any loader that does start had its admission CAS linearized before `close` flipped `closedFlag`.

### 6.6 Two-atomic waiter freshness check

**Choice:** Waiters perform two atomic reads on wake: `closedFlag.get()` (a monotonic `AtomicBoolean`) and `bucketFor(key)` (which dereferences `structureRef` and hashes `key`). Comparison to captured `parkedBucketRef` via `eq` detects a structure swap without any bucket-state read.

**Why:** Under Guava loader-result dedup the waiter does not need to inspect per-key state: the decision is "is the cache closed?" and "was the whole structure swapped out from under me?", both of which are cache-wide fences. No `BucketState.get` is required on the waiter path — one memory read (`closedFlag`) and one pointer compare (`bucketFor(key) eq parkedBucketRef`). The closed flag is monotonic, so a false-negative (read `false` just before the flag flips) linearizes the waiter's accept ahead of the close, which is correct.

### 6.7 Promise payload handoff (not bucket read-back)

**Choice:** Waiters read `Option[V]` from the promise directly. They do NOT re-read the bucket to get `v`.

**Why:** Zero-TTL and eager-eviction caches can sweep a just-published `Ready` out of the bucket within microseconds of publication. If waiters had to read through the bucket to get `v`, they would see "not there" and spuriously retry — or worse, read a DIFFERENT successor's `Ready` and return its value, violating Guava dedup semantics. Promise payload bypass makes the handoff immune to bucket mutations.

The promise holds `Some(v)` strongly for its lifetime — dedup is preserved even under weak/soft caching. The promise is not referenced from any long-lived cache state (`Ready.origin` is a separate per-call `Object` token, not the promise), so the promise becomes GC-eligible as soon as producer + waiters release their references (typically microseconds).

### 6.8 Clock AND expiry inside modify closures

**Choice:** Both `System.nanoTime()` AND `computeExpiry(nowNano, ttl)` are computed INSIDE every retrying `bucketRef.modify` closure.

**Why:** Two related hazards, one fix.
- **Stale-clock hazard for `cleanBucket`:** if we snapshot `nowNano` once before `modify`, a CAS retry reuses the stale timestamp. A `Ready` that expires between snapshot and retry appears live in `cleanBucket` → the retry path takes `ActionHit` on an expired entry.
- **Shortened-TTL hazard for publication:** if `expiry = computeExpiry(nowNano, ttl)` is computed outside the modify, CAS retries reuse the stale `nowNano`. The published `Ready.expiresAtNano` is then calculated against an old clock, shortening the TTL arbitrarily under contention — in the worst case publishing an already-expired entry.

Re-reading the clock each retry eliminates both. `R.wrap(v)` stays outside (it's cheap and doesn't depend on time); `storedV` is captured once.

### 6.9 Terminal `close` semantics

**Choice:** `close` is terminal. Post-close:
- `computeIfAbsent` defects with `IllegalStateException`.
- `put` / `invalidate*` are silent no-ops.
- The structure is swapped to a fresh empty vector; all pre-close state is orphaned.

**Why:** Per priority (1), a safety-critical cache cannot silently permit post-teardown loader execution. Side-effecting loaders could run after the cache is closed. Making `close` terminal:
- Flips `closedFlag`; all subsequent admissions defect.
- Interrupts the eager-eviction fiber.
- Swaps to empty, orphaning pre-close state.
- Drains the orphaned vector's promises so parked waiters wake, retry, and observe `closedFlag` → fail fast.

**Concurrent `close` serialization.** A single `close` call linearly owns teardown: the first caller to CAS a non-null `Promise2` into `closedPromiseRef` becomes the teardown winner, flips `closedFlag`, and runs the interrupt / swap / drain steps. Concurrent losers observe the installed promise and `await` it — they return ONLY after the winner's teardown is observably complete. This upholds the contract that once ANY `close` call returns, the cache is fully quiesced: a `getAndSet`-based serialization (what an earlier version used) would let a loser return `F.unit` immediately while the winner was still mid-teardown, which violates that contract. The promise is created lazily via `P.mkPromise` inside `close` (not at construction) so caches that are never closed don't allocate it.

**Teardown defect propagation.** Winner wraps teardown in `F.sandboxExit` and publishes the raw `Exit.Uninterrupted[Nothing, Unit]` as the promise payload (so the promise's carrier type is `Promise2[F, Nothing, Exit.Uninterrupted[Nothing, Unit]]`, not `Promise2[F, Nothing, Unit]`). EVERY caller — winner and losers alike — then re-raises via the SAME tail expression: `F.flatMap(winnerPromise.await)(F.fromSandboxExit(_))`. Only the teardown + publish step is winner-exclusive; the replay is universal. This makes the published Exit the mechanically SOLE source of every caller's terminal effect — there is no alternate code path to synthesize an observably equivalent re-raise from. A winner-path divergence (winner re-raising from a local `exit` variable while losers read from the promise) would be caught by the unified `await` count in the regression test; at the implementation level, there is no such local variable to re-raise from because the winner's own branch discards `exit` after `succeed(exit)`.

The invariant is **consistency, not maximum fidelity**: `F.fromSandboxExit` itself is lossy — the default `IO2` implementation passes only `compoundException` to `terminate`, discarding `allExceptions` and the original `trace`. That collapsing is applied identically to every caller, so winner and losers produce the same observable throwable; they just both see the collapsed form. If a backend wanted perfect fidelity (e.g., to preserve ZIO `Cause` structure across the replay), it could override `IO2.fromSandboxExit` — this design does not require it, but it permits it.

An earlier version used `Promise2.terminate(exit.trace.toThrowable)`: that broke consistency because losers went through `await`'s terminate-path (single `Die(t)`) while the winner went through its own `F.fromSandboxExit(exit)` path — losers and winner observed DIFFERENT causes. Publishing the Exit and routing everyone through `F.fromSandboxExit` enforces one canonical collapse. No caller can return `F.unit` unless teardown genuinely succeeded (`Exit.Success`).

`put`/`invalidate`/`invalidateAll` become silent no-ops post-close — their signatures are `F[Nothing, Unit]` and have no channel to signal "closed" short of defect, which would be surprising for non-loader ops.

This is a clean-slate API for the `wip/bio-cache` branch (no deployed callers). The trait's method is named `close`, matching the terminal-teardown convention (AutoCloseable / Cats Effect Resource).

### 6.10 `uninterruptible` / `uninterruptibleExcept` liberally

**Choice:** Every bucket-modifying control-plane op (`put`, `invalidate`, `invalidateAll`, `close`) runs under `F.uninterruptible`. `computeImpl` runs under `F.uninterruptibleExcept { restore => ... }`, with `restore(compute)` for the loader and `restore(awaitAndRetry(...))` for the wait path.

**Why:** Per priority (3), interruption propagation is low. We accept uninterruptible regions where they simplify proving the "admission + bucket mutation + promise signal" step is atomic w.r.t. caller interruption. `computeImpl` escapes this via `restore` only for the loader body (so caller timeouts propagate into `compute`) and the wait path (so caller timeouts propagate into `await`).

`guaranteeOnFailure` cleans up a `Computing` entry and signals `None` on any interrupt / defect propagated through `restore`-wrapped regions — this is how we guarantee no orphan `Computing` is left in the bucket even if a caller gets cancelled mid-compute.

### 6.11 Producer returns its own `v`, unaffected by racing mutations

**Choice:** `doCompute` always returns the `v` that `compute` produced, regardless of whether publication succeeded.

**Why:** Guava loader-result semantics. A caller invoked `compute`, got back the fiber that runs `compute`, waits for it, and expects the result. A racing `put` or `invalidate` might divert the CACHE away from that `v`, but the CALLER is entitled to the loader's output. This is critical for side-effecting loaders: if loader allocated a resource, opened a connection, wrote a row — the caller must receive that handle, not a racing put's value.

Cost: cache state and the caller's return value can diverge momentarily. Documented in the `computeIfAbsent` scaladoc.

### 6.12 `invalidate` / `invalidateAll` / `put` / `close` do NOT preempt in-flight producers

**Choice:** These operations mutate bucket state and signal the displaced `Computing`'s promise with `None`, but they do NOT interrupt the producer fiber.

**Why:** Priority (3). Also, preempting would require tracking all producer fibers (significant complexity and memory overhead), and it would break Guava loader-result semantics (the producer's caller could receive an unexpected cancellation). Instead:
- Displaced `Computing`'s promise is signaled `None` → parked waiters wake and retry via `computeImpl`. Liveness preserved.
- Producer runs to completion on its own fiber. When it tries to publish, it sees `bucketRef ne bucketFor(key)` (for `invalidateAll` / `close`) or `cleaned.exists(ownedComputing) == false` (for same-bucket `put` / `invalidate`) and silently doesn't publish. Signals its own promise with `None` (idempotent with the control-plane's earlier signal).
- Producer's caller receives its own computed `v`.

The user who wants to cancel a hung producer uses their own fiber supervision (`Temp.timeout`, structured concurrency, fiber scope). This is the documented recommended liveness pattern.

## 7. Memory profile

Per bucket (`BucketState`):
- `entries`: a `List[BucketEntry]` of live Ready / Computing entries for this bucket's keys. Bounded by the cache's working set.

Cache-wide:
- `structureRef`: one `AtomicReference[Vector[Ref2]]`. Points to the currently-live vector.
- `closedFlag`: 1 byte (AtomicBoolean).
- `evictionFiberRef`: a single `Ref2[F, Option[Fiber2]]`.
- `closedPromiseRef`: one `AtomicReference[Promise2]`, allocated lazily on the first `close` call.

No per-key metadata — the `keyEpochs: Map[K, Long]` that earlier iterations carried per bucket was removed in Round 26 together with the strict-freshness-for-waiters invariant it enforced. Memory for a bucket with zero live entries is now constant (one empty-list case class).

## 8. Concurrency semantics summary

| Operation                     | Atomicity                         | Linearization                                    | Interrupts producer? |
| ----------------------------- | --------------------------------- | ------------------------------------------------ | -------------------- |
| `get`                         | `structureRef` read + per-bucket CAS | at the bucket CAS                             | n/a                  |
| `put` / `putWithTTL`          | `structureRef` read + per-bucket CAS | at the bucket CAS                             | No                   |
| `invalidate`                  | `structureRef` read + per-bucket CAS | at the bucket CAS                             | No                   |
| `computeIfAbsent` admission   | `structureRef` read + per-bucket CAS (with orphan + closed guards inside closure) | at the bucket CAS | n/a   |
| `computeIfAbsent` wait        | awaits promise + per-bucket snapshot | at the snapshot read                          | n/a                  |
| Producer publish              | per-bucket CAS (with orphan + closed guards inside closure) | at the CAS commit         | n/a                  |
| `invalidateAll`               | `structureRef.getAndSet` (1 CAS) + drain traversal | at the `getAndSet`                | No                   |
| `close`                       | `closedFlag.getAndSet` + `structureRef.getAndSet` + drain | at the `structureRef` swap  | Eviction fiber: yes. Producers: no. |

## 9. What can still go wrong (honest limitations)

These are accepted per-design; they are not bugs but deliberate tradeoffs:

- **In-flight producer after close.** If a producer's admission CAS committed AND passed the Stage 2 post-CAS re-check before `close` flipped `closedFlag`, that producer runs to completion. Its side effects happen. Its caller receives `v`. The cache does not retain it (publication is blocked by the orphan-bucket guard in `doCompute`). Callers needing hard termination must cancel their own fibers via fiber scope — the Stage 2 guard only prevents admissions from starting the loader after close is observable; it does not preempt already-running loaders.

- **Concurrent put/get racing `invalidateAll` or `close`.** A caller that captured the old `structureRef` vector pre-swap can complete its operation against the orphaned `Ref2`s. A `put` that linearizes on an orphan bucket has no post-swap visible effect. A `get` that reads an orphan bucket sees its state at read time (which may be pre-swap content). These are "in-flight ops at the moment of swap"; their effects linearize before the swap. Matches ConcurrentHashMap.clear semantics.

- **Per-bucket `BucketState` CAS contention on hot keys.** All ops on keys hashing to the same bucket serialize through one `Ref2`. A hot key cannot scale beyond one core's CAS throughput. Increase `initialCapacity` for high-contention workloads.

- **Waiters do NOT observe a racing `put`/`invalidate`.** Once a waiter has received `Some(v_A)` from its producer's promise, it returns `v_A` even if `put(k, v_B)` linearizes between the producer's signal and the waiter's wake. This is Guava loader-result dedup: the waiter's return is tied to the producer it dedup'd against, not to the latest cache state. Callers that need `v_B` issue a fresh `get` / `computeIfAbsent` (which will hit `put`'s Ready). This is the invariant we deliberately chose over strict freshness for waiters — see review ledger Round 26.

- **Sub-nanosecond read-then-decide window for `closedFlag`.** Between a waiter reading `closedFlag.get() == false` and returning `v`, `close` can flip the flag. The waiter already committed to return `v`; linearizability places its return before the close. (False-negative close check is not a correctness bug — the waiter's call was linearized before close.)

## 10. Forward work

- **Globally-atomic flush variant.** If callers routinely need to serialize in-flight ops against flush, consider a separate `AtomicBIOCache` backed by a single `Ref[CacheState]` — slower but fully atomic — alongside the current swap design.
- **Producer tracking for cancel-on-close.** Deliberately deferred per priority (3). Would enable hard cancellation at the cost of per-producer bookkeeping.
