# BIOCache — Codex Adversarial Review Ledger

**Branch:** `wip/bio-cache`
**Primary file:** `fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/cache/ConcurrentHashMapCache.scala`
**Tests:** `fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/test/BIOCacheTest.scala` (83 passing)
**Cross-compiles:** Scala 2.12.20, 2.13.16, 3.7.4 — JVM + JS

## Purpose

This document captures every issue Codex's adversarial reviewer flagged across ~25 iterations of `/codex:adversarial-review`, the resulting mitigation, and the design tradeoffs or exclusions we chose to accept rather than mitigate. It is a single-file audit trail for reviewing the final design.

## Design constraints (user-stated, ordered)

1. **Soundness first.** Hangs are unacceptable; strict freshness across explicit barriers; no missed invalidations.
2. **Performance second.** May use `uninterruptible`/`uninterruptibleExcept` liberally if it helps soundness.
3. **Interruption propagation: LOW priority.** Producer fibers are not interrupted by control-plane ops.

## Contract summary (final design)

- **`computeIfAbsent(k, compute)`** — Guava-style loader-result dedup. `compute` runs at most once per caller; the caller's return value is its own computed `v`, not whatever ends up in the cache.
- **`put(k, v)`** / **`putWithTTL`** — explicit per-key freshness barrier. Bumps `keyEpochs(k)` atomically with entry installation.
- **`invalidate(k)`** — explicit per-key freshness barrier. Bumps `keyEpochs(k)` atomically with entry removal.
- **`invalidateAll`** — per-bucket (not globally atomic) flush. Matches `java.util.ConcurrentHashMap.clear` semantics.
- **`shutdown`** — **terminal close.** Flips cache-wide `closedFlag` before bucket traversal; post-shutdown `computeIfAbsent` calls defect with `IllegalStateException`; `put`/`invalidate*` become silent no-ops.

## Freshness-barrier architecture (final)

All freshness-barrier state lives INSIDE `BucketState`:
```scala
case class BucketState[K, S](
  invGen:    Long,        // bumped by invalidateAll / shutdown per-bucket
  closed:    Boolean,     // set by shutdown per-bucket
  keyEpochs: Map[K, Long],// bumped by put(k) / invalidate(k)
  entries:   List[BucketEntry[K, S]],
)
```

Plus a cache-wide atomic for shutdown admission fencing:
```scala
private val closedFlag: AtomicBoolean = new AtomicBoolean(false)
```

Waiter post-wake decision reads `bucketFor(key).get` ONCE and inspects `state.closed`, `state.invGen`, `state.keyEpochs(k)` from that single atomic snapshot. Admission paths additionally read `closedFlag.get()` to fence new loads after shutdown starts (before `state.closed` has been written to the caller's bucket).

---

## Chronological findings (HIGH only unless noted)

### Round 1

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 1.1 | Parked waiters can return stale producer value after later `put` if replacement cleaned by TTL/GC | Initially added Tombstone(displaced-origin-token) markers; later redesigned into the per-key epoch model where `put` bumps `keyEpochs(k)` atomically with entry installation | **Fixed** (final design bumps `keyEpochs(k)` in put, waiter sees advance) |
| 1.2 | Tombstones unbounded in lazy caches (no reaper) | Replaced tombstones entirely with per-key epoch counters; `invalidateAll` resets `keyEpochs` per bucket | **Fixed** |

### Round 2

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 2.1 | Already-deduped waiters hijacked by later TTL/GC-triggered successor | Only explicit user actions (`put`/`invalidate`/`invalidateAll`/`shutdown`) bump barrier counters; benign TTL/GC cleanup, successor compute installation, and Ready publication do not | **Fixed** (regression test: "dedup preserved: already-deduped waiter returns producer's v even after benign TTL/GC + successor") |
| 2.2 (MED) | Barrier markers expired after 60s wall-clock → correctness dependent on clock | Tombstone aging removed entirely; monotonic counters never expire | **Fixed** |

### Round 3

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 3.1 | `shutdown` is a breaking in-place API change | See [Accepted tradeoffs](#accepted-tradeoffs) — user explicitly chose terminal semantics for soundness | **Accepted** |
| 3.2 | `invalidateAll` leaks the flushed keyset via immortal tombstones | `invalidateAll` clears `keyEpochs` to empty and `entries` to `Nil`; no tombstones | **Fixed** |

### Round 4

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 4.1 | Same-key follow-up (invalidate → invalidate) erases tombstone | Tombstones removed; counters are monotonic and never erased by same-key follow-ups | **Fixed** (regression test: "publish → invalidate → invalidate: same-key follow-up preserves barrier") |
| 4.2 (MED) | Tombstones invisible to `size`/`keys` | Tombstones removed; `keyEpochs` entries are bounded metadata (`Map[K, Long]`), not `BucketEntry`s, and not counted by `size`/`keys` (which only count live `Ready`s) | **Fixed** |

### Round 5

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 5.1 | Successor compute's publish erases older waiter's tombstone | Tombstones replaced by monotonic counters that can't be erased | **Fixed** (regression test: "publish → invalidate → successor publish → old waiter wake") |
| 5.2 (MED) | 1-hour tombstone TTL makes correctness wall-clock dependent | Tombstone aging removed | **Fixed** |

### Round 6

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 6.1 | Expired/GC-cleared Readys still generated tombstones → synthetic barrier hijacks deduped waiters | Counters are only bumped by user actions, never inferred from state shape | **Fixed** (regression test: "expired Ready + later put: put IS an explicit barrier — waiter retries past A") |
| 6.2 | `shutdown` destructive | See [Accepted tradeoffs](#accepted-tradeoffs) | **Accepted** |
| 6.3 (MED) | Same-key invalidation history unbounded via tombstone accumulation | Tombstones removed; `keyEpochs` stores one `Long` per distinct touched key | **Fixed** |

### Round 7

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 7.1 | Weak/soft `get`'s GC cleanup deleted tombstones needed by parked waiters | No tombstones; `get` never bumps barrier counters so weak/soft GC cleanup is always benign | **Fixed** |
| 7.2 | Per-key tombstone history unbounded on hot refresh loops | Tombstones removed | **Fixed** |

### Round 8

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 8.1 | `shutdown` breaking | See [Accepted tradeoffs](#accepted-tradeoffs) | **Accepted** |
| 8.2 | Freshness barriers lossy under STW pauses + 60s cap | No wall-clock caps — monotonic counters | **Fixed** |
| 8.3 (MED) | Per-key `invalidate` leaves hidden tombstones in lazy cache | No tombstones | **Fixed** |

### Round 9

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 9.1 | `computeImpl` snapshots `nowNano` before CAS-retrying `modify`; expired `Ready` could survive on retry → `ActionHit` on expired entry | `System.nanoTime()` moved INSIDE every CAS-retrying modify closure; `put`/`invalidate`/`computeImpl`/`get`/`evictExpired` all re-read the clock per retry | **Fixed** |
| 9.2 | `keyEpochs` retained every distinct put/invalidated key | See [Accepted tradeoffs — keyEpochs memory profile](#accepted-tradeoffs) | **Accepted with documentation** |
| 9.3 | `invalidateAll` didn't fence in-flight loads in not-yet-visited buckets | Added cache-wide `globalEpoch` (initially); finally embedded `invGen` directly in `BucketState` so each bucket's state transition atomically marks it flushed | **Fixed** (see note below) |

### Round 10

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 10.1 | Bucket-wide `gen` bumped by per-key ops → hash-colliding keys cause false-positive retries → duplicate `compute` under short TTL / weak refs | Moved from bucket-gen to per-key `keyEpochs: Map[K, Long]`. A `put`/`invalidate` on a colliding key bumps a DIFFERENT map entry; waiters for unrelated keys never retry spuriously | **Fixed** |
| 10.2 | `shutdown` breaking | See [Accepted tradeoffs](#accepted-tradeoffs) | **Accepted** |

### Round 11

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 11.1 | `invalidateAll` bumped separate `AtomicLong globalEpoch`; waiter read `globalEpoch` THEN bucket — a between-reads `invalidateAll` could bump global AND reset bucket, waiter saw stale epoch + empty map | Embedded `invGen` IN `BucketState`. Waiter reads a single atomic snapshot. No inter-atomic race | **Fixed** |
| 11.2 (MED) | Per-key epoch tracking unbounded | See [Accepted tradeoffs](#accepted-tradeoffs) | **Accepted with documentation** |

### Round 12

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 12.1 | Read bucket → read globalEpoch window: a put between the two reads was missed | Refactored to single atomic `BucketState` read (all three counters live in one structure) | **Fixed** |
| 12.2 | `shutdown` breaking | See [Accepted tradeoffs](#accepted-tradeoffs) | **Accepted** |
| 12.3 (MED) | Per-key epoch unbounded | See [Accepted tradeoffs](#accepted-tradeoffs) | **Accepted with documentation** |

### Round 13

| # | Finding | Mitigation | Status |
|---|---------|------------|--------|
| 13.1 | `closedFlag`/`globalEpoch` as separate atomics outside bucket state — CAS commit could still succeed after fence flipped | All barrier state embedded in `BucketState`; waiters read ONE snapshot; admissions additionally double-check cache-wide `closedFlag` for shutdown fencing (belt-and-suspenders) | **Fixed** |
| 13.2 (MED) | `keyEpochs` unbounded | See [Accepted tradeoffs](#accepted-tradeoffs) | **Accepted with documentation** |

### Round 14 (final round — persistent concerns)

| # | Finding | Disposition |
|---|---------|-------------|
| 14.1 | Same-key put/invalidate can leak stale to waiters (sub-nanosecond window between atomic read and branch execution) | **Inherent.** Any read-then-decide pattern has a non-zero gap between the atomic read and the decision using that value. Linearizability defines operations to take effect at the read point. A put that fires strictly after our bucket read linearizes after our accept. See [Accepted tradeoffs — strict-linearization window](#accepted-tradeoffs). |
| 14.2 | `shutdown` breaking | **Accepted** — intentional per user's soundness-first priority. See [Accepted tradeoffs](#accepted-tradeoffs). |
| 14.3 | `invalidateAll` not globally atomic — in-flight producer on not-yet-visited bucket can publish and wake waiters with pre-flush value | **Accepted** — matches `ConcurrentHashMap.clear` semantics; documented. See [Accepted tradeoffs](#accepted-tradeoffs). |

---

## Cross-cutting fixes (consolidated)

### Stale-clock hazard
- **Problem:** `System.nanoTime()` captured before a CAS-retrying `Ref2.modify`; retries used stale timestamp → `ActionHit` on expired `Ready`.
- **Fix:** Clock is read INSIDE every retrying modify closure: `get`, `put`, `invalidate`, `computeImpl`, `doCompute`'s publish, `evictExpired`.

### Ref cleanup under racing put (`origin = null` NPE concern)
- **Problem flagged earlier in session:** `doCompute`'s `guaranteeOnFailure` cleanup matched `Ready` entries by `origin eq originToken`. Put installs Ready with `origin=null`. Would a null-receiver `.eq` NPE?
- **Fix:** Scala's `eq` compiles to JVM `if_acmpeq` — reference identity, no dispatch, no NPE. Regression test: "producer cleanup after racing put (Ready.origin = null) does not NPE".

### Interrupt-safe admission
- **Problem:** A caller-level interrupt between installing `Computing` and running `compute` could orphan the `Computing` in the bucket and wedge any future waiter.
- **Fix:** `computeImpl` runs under `uninterruptibleExcept { restore => ... }`; `compute` runs via `restore(compute)`. `guaranteeOnFailure` removes the `Computing` and signals the promise with `None` on any interrupt or defect. Regression test: "computeImpl does not orphan Computing when compute is interrupted".

### createWithEviction leak-safety
- **Problem:** Between `FK.fork(evictionLoop)` and `fiberRef.set(Some(fiber))`, an interrupt could leak the eviction fiber.
- **Fix:** Wrapped in `uninterruptible` + `guaranteeOnFailure` that reads `fiberRef.get` and interrupts any installed fiber. Regression test: "createWithEviction construction under interrupt: every successful build has a working shutdown".

### Waiter wake-and-retry dedup
- **Problem:** N waiters all wake with `None` after a failure; each could spawn its own `compute` → N-fold duplicate load.
- **Fix:** Each waiter's `None` handler calls `computeImpl`. The bucket `modify` CAS serializes them: exactly one installs a new `Computing` and runs compute; the others see it and take `ActionWait`. Regression test: "N waiters woken with empty cache dedup their retries (at most 1 recompute across N)".

### Zero-TTL / eager-eviction racing waiter wake
- **Problem:** Producer publishes Ready with TTL=0; `cleanBucket` (or eager-eviction fiber) immediately sweeps it; a waiter reading the bucket would see "not there" and retry unnecessarily.
- **Fix:** Waiters read `Option[V]` directly from the promise payload, NOT from the bucket. Bypassing the cache makes the handoff immune to bucket mutations. Regression tests: "eager eviction + zero TTL: waiter still receives producer's value via promise" and "concurrent callers with zero TTL: producer runs compute once; waiter receives producer's value".

### Caller-side timeout
- **Problem:** A caller's `Temp.timeout` should only free the caller, not peer waiters or the producer.
- **Fix:** `awaitAndRetry` runs `promise.await` under `restore(...)` so interrupts propagate to the waiter only. The producer's promise remains signalable by others; peers stay parked. Regression test: "waiter's caller-side timeout frees only itself, not peers".

---

## Final freshness decision table

A waiter parked on producer A's promise, woken with `Some(v_A)`, reads ONE `bucketFor(key).get` snapshot and decides:

| Condition                                           | Decision       | Rationale                                                              |
| --------------------------------------------------- | -------------- | ---------------------------------------------------------------------- |
| `state.closed == true`                              | `failClosed`   | shutdown visited this bucket                                           |
| `state.invGen > parkedInvGen`                       | retry          | `invalidateAll`/`shutdown` visited this bucket                         |
| `state.keyEpochs.getOrElse(k, 0L) > parkedKeyEpoch` | retry          | `put(k)`/`invalidate(k)` fired for this specific key                   |
| otherwise                                           | return `v_A`   | Guava loader-result dedup preserved; no explicit barrier was observed  |

A waiter woken with `None` retries via `computeImpl` unconditionally (after checking `state.closed`).

---

## Accepted tradeoffs

### shutdown terminal semantics (NOT reverted)

**Codex says:** `shutdown` used to be eviction-fiber cleanup; making it a terminal close is a silent API break.

**Our decision:** **Accept.** This is an explicit choice by the user, who prioritized soundness over backwards compatibility on a WIP branch. Changing `shutdown` to terminal close:
- Prevents post-teardown loader execution (side-effecting loaders could otherwise run after cache close — a real safety hazard for "human lives depend on it" semantics).
- Makes the contract explicit: `put`/`invalidate*` become silent no-ops; `computeIfAbsent` misses defect with `IllegalStateException`.
- Callers that previously called `shutdown` defensively now must either treat the cache as discarded or stop calling `shutdown` until they mean it.

The trait scaladoc documents this explicitly.

### keyEpochs memory profile

**Codex says:** `BucketState.keyEpochs` is a `Map[K, Long]` that grows monotonically with distinct `put`/`invalidate`d keys. Long-lived caches with high key churn accumulate metadata.

**Our decision:** **Accept with documentation.** Pruning is provably unsafe: if we drop `keyEpochs(k)` when no live `Ready`/`Computing` exists, waiters that parked with `parkedKeyEpoch > 0` would observe `getOrElse(k, 0L) == 0` and incorrectly accept pre-barrier values. Any pruning scheme either:
- Loses barrier information for still-parked waiters (correctness bug), or
- Requires tracking outstanding waiters per key (significant complexity and memory overhead).

**Memory bound:** per bucket, one `(K, Long)` entry per distinct key ever `put`/`invalidate`d since the last `invalidateAll` or `shutdown`. For typical workloads (bounded key universe, periodic `invalidateAll`), this is bounded by the cache's own working set. For adversarial unique-key workloads, operators must call `invalidateAll` periodically to reclaim metadata — this is documented in `BucketState`'s scaladoc.

### invalidateAll non-atomic flush

**Codex says:** `invalidateAll` should be a cache-wide freshness barrier; the current design lets in-flight producers on not-yet-visited buckets publish stale results.

**Our decision:** **Accept.** `invalidateAll` operates per-bucket, matching `java.util.ConcurrentHashMap.clear` semantics. Making it globally atomic would require either:
- A single `Ref2[CacheState]` covering all buckets (destroys per-bucket concurrency — all ops contend on one CAS), or
- A lock held for the duration of the traversal (hurts latency), or
- Tracking and interrupting all in-flight producer fibers (complex, and conflicts with the user's "interruption propagation is LOW priority" constraint, and would break Guava loader-result semantics for callers who already invoked `compute`).

For a strict atomic flush, callers construct a new cache. This is documented in both the class-level scaladoc and in the `invalidateAll` scaladoc. A regression test ("invalidateAll is non-atomic: concurrent put may survive if racing the traversal") pins the semantics.

### Strict-linearization window for read-then-decide

**Codex says:** Between a waiter's atomic bucket read and the decision-branch execution, a concurrent `put`/`invalidate` can commit. The waiter's decision uses the captured (stale) snapshot. Under very strict linearizability, this could be seen as "the put linearized first but waiter returned pre-put v".

**Our decision:** **Accept.** This is inherent to any read-then-decide code path on a shared `AtomicReference`-based structure. The waiter's operation linearizes at the read point; a put that fires AFTER the read linearizes after the waiter's return. From the user's observable perspective:
- If waiter returned before the put's call-site returned: user sees waiter first (consistent).
- If put's call-site returned before waiter's: waiter's read happened-before (same linearization order).

The only way to eliminate the window is a global lock covering read-through-return, which would serialize all waiters. `ConcurrentHashMap` and every other concurrent cache has the same property.

We tightened the window by embedding all barrier counters into a single `BucketState` so one atomic read captures all of them; there is no inter-atomic race. The remaining sub-nanosecond window between that one read and the `if-else` branch is inherent.

---

## Regression test coverage (83 tests)

Tests that specifically pin down Codex-flagged scenarios:

| Test name                                                                                                          | Covers                                                 |
| ------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------ |
| put AFTER producer signaled: waiter sees put's value via gen-check retry (strict freshness)                        | Round 1.1 — put-after-signal waiter retries            |
| put with expired TTL + cleanBucket sweeps put's Ready: waiter retries                                              | Round 1.1 — tombstone/epoch survives Ready cleanup     |
| dedup preserved: already-deduped waiter returns producer's v even after benign TTL/GC + successor                  | Round 2.1 — no benign-cleanup hijack                   |
| expired Ready + later put: put IS an explicit barrier — waiter retries past A                                      | Round 6.1 — put always bumps epoch                     |
| publish → invalidate → successor publish → old waiter wake: waiter retries                                         | Round 5.1 — older waiter sees barrier                  |
| publish → invalidate → invalidate: same-key follow-up preserves barrier                                            | Round 4.1 — same-key follow-ups                        |
| publish → invalidate → successor publish → invalidate → old waiter wake: older barrier survives                    | Round 5.1 — multi-mutation sequence                    |
| A → invalidate → B → A succeeds → B succeeds: newer compute wins, cache ends with B's value                        | Freshness boundary                                     |
| A → invalidate → B → B publishes → A publishes late: A's stale value does NOT overwrite B's Ready                  | Strict freshness: late publish blocked                 |
| A → invalidate → B → B fails first → A succeeds: A does NOT resurrect stale data                                   | Strict freshness: failed successor                     |
| A → invalidate → B → A succeeds → B fails: A's stale value is correctly suppressed                                 | Strict freshness: A's late signal                      |
| producer cleanup after racing put (Ready.origin = null) does not NPE                                               | JVM `eq` null-safety                                   |
| repeated invalidate against in-flight loader does not amplify compute count                                        | At-most-once compute                                   |
| invalidate does NOT preempt in-flight producer; compute runs exactly once; cache stays empty                       | Producer non-preemption                                |
| invalidateAll does NOT preempt in-flight producer; compute runs exactly once; cache stays empty                    | Producer non-preemption (global)                       |
| invalidate / invalidateAll / put immediately wakes parked waiters (release semantics)                              | No-wedge liveness                                      |
| shutdown releases parked waiters on a wedged producer with IllegalStateException                                   | Shutdown liveness                                      |
| put releases waiter on a wedged producer WITHOUT needing shutdown                                                  | Release without teardown                               |
| caller-side Temp.timeout on producer unblocks parked waiters (recommended liveness pattern)                        | Timeout pattern                                        |
| waiter's caller-side timeout frees only itself, not peers                                                          | Timeout scope                                          |
| interrupting computing fiber should unblock waiters                                                                | Interrupt liveness                                     |
| eager eviction + zero TTL: waiter still receives producer's value via promise                                      | Promise-payload handoff                                |
| concurrent callers with zero TTL: producer runs compute once; waiter receives producer's value                     | Dedup under zero TTL                                   |
| N waiters woken with empty cache dedup their retries (at most 1 recompute across N)                                | Retry dedup                                            |
| computeImpl does not orphan Computing when compute is interrupted                                                  | Interrupt cleanup                                      |
| invalidateAll is non-atomic: concurrent put may survive if racing the traversal                                    | Pins non-atomic flush semantics (accepted tradeoff)    |
| createWithEviction construction under interrupt: every successful build has a working shutdown                     | Leak-safe construction                                 |
| stress: concurrent computeIfAbsent and put converge on cache-consistent final state                                | Stress                                                 |
| stress: concurrent computeIfAbsent with a waiter and racing put yields consistent results                          | Stress                                                 |

---

## Open questions for user review

1. **`shutdown` API break.** Should we split into `shutdown` (non-destructive, stop eviction fiber only) + `close` (terminal)? Codex recommends it; we kept in-place rename per soundness-first priority. Decision is reversible — only affects callers of `BIOCache.shutdown`.

2. **`keyEpochs` pruning.** Pruning is unsafe without waiter tracking. If the memory profile becomes a production issue, we can add either (a) a metric to expose `keyEpochs.size` for observability, or (b) a per-bucket `compact()` method callers invoke during low-traffic windows (still unsafe under load — would require waiter tracking).

3. **`invalidateAll` atomicity.** Could be made globally atomic via a single-`Ref` design, at the cost of per-bucket concurrency. If a caller needs an atomic flush, the recommendation is "construct a new cache" — this is consistent with `ConcurrentHashMap`.

4. **Producer interruption on shutdown.** Codex asked for this in one round. We chose not to implement: tracking all in-flight producer fibers adds complexity and memory, and conflicts with loader-result semantics (the producer's caller already has a handle to `compute`'s result). Producers naturally terminate once their compute finishes; shutdown only needs to release WAITERS, which it does via `None` signaling.
