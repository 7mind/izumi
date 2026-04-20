# BIOCache — Codex Adversarial Review Ledger

**Branch:** `wip/bio-cache`
**Primary file:** `fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/cache/ConcurrentHashMapCache.scala`
**Tests:** `fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/test/BIOCacheTest.scala` (89 passing)
**Cross-compiles:** Scala 2.12.20, 2.13.16, 3.7.4 — JVM + JS

## Purpose

This document captures every issue Codex's adversarial reviewer flagged across the design process, the resulting mitigation, and the design tradeoffs or exclusions we chose to accept rather than mitigate. It is a single-file audit trail for reviewing the final design.

## Design constraints (user-stated, ordered)

1. **Soundness first.** Hangs are unacceptable; no missed invalidations; no silently lost writes. Freshness across explicit barriers holds for NEW callers (a fresh `get` / `computeIfAbsent` after the barrier returns sees the post-barrier state) AND for the publish path (a displaced producer does NOT repopulate past the barrier). For already-signaled waiters, the rule splits by barrier kind: `put(k)` / `invalidate(k)` honor Guava loader-result dedup (waiter returns producer's `v_A` — the bucket `Ref2` is intact); `invalidateAll` / `close` swap the whole structure, so the waiter's `parkedBucketRef` is orphan and the waiter retries (`invalidateAll`) or fails fast (`close`). See Round 26 for the rationale behind the `put`/`invalidate` relaxation.
2. **Performance second.** May use `uninterruptible`/`uninterruptibleExcept` liberally if it helps soundness.
3. **Interruption propagation: LOW priority.** Producer fibers are not interrupted by control-plane ops.
4. **Backward compatibility: NOT a concern.** `BIOCache` is on `wip/bio-cache` and has not shipped.

## Contract summary (final design)

- **`computeIfAbsent(k, compute)`** — Guava-style loader-result dedup. `compute` runs at most once per caller; the caller's return value is its own computed `v`, not whatever ends up in the cache. Waiters receive the producer's `v` via the promise payload.
- **`put(k, v)`** / **`putWithTTL`** — installs a new `Ready` and displaces any in-flight `Computing(k, …)` by signaling its promise `None` so parked waiters wake and retry. `put` is authoritative for callers that read the cache AFTER it returns; it does NOT retro-cancel already-signaled waiters.
- **`invalidate(k)`** — removes entries for `k` and displaces any in-flight `Computing(k, …)` via `None`-signal. Same semantics as `put` with respect to already-signaled waiters.
- **`invalidateAll`** — atomic structure swap: a single `AtomicReference.getAndSet` replaces the entire bucket vector with a fresh one. Old vector is orphaned. Post-swap drain signals in-flight producer promises; waiters detect the swap via `Ref2` identity on wake and retry.
- **`close`** — terminal close. Flips cache-wide `closedFlag`, swaps the structure, drains. Post-close `computeIfAbsent` defects with `IllegalStateException`; `put`/`invalidate*` become silent no-ops.

## Freshness-barrier architecture (final)

```scala
case class BucketState[K, S](entries: List[BucketEntry[K, S]])

private val structureRef: AtomicReference[Vector[Ref2[F, BucketState[K, R[V]]]]]
private val closedFlag:   AtomicBoolean  // monotonic; flipped by close
```

Two barrier mechanisms:
- **Cache-wide `closedFlag`** — flipped `true` by `close` BEFORE the structure swap. Checked inside every admission and publish modify closure.
- **Structure swap** — `invalidateAll` / `close` do `structureRef.getAndSet(freshVec)`. Old vector's `Ref2`s are orphaned; new ops don't see them. Detected by waiters via `Ref2` identity (`eq`).

`put(k)` / `invalidate(k)` do NOT impose a post-wake retry on already-deduped waiters: they displace in-flight `Computing`s by `None`-signaling their promise (so parked waiters that hadn't yet been signaled wake and retry), but once a waiter has been signaled `Some(v_A)` by its producer, the waiter returns `v_A` per Guava loader-result dedup. Callers that need to observe `put`'s newer value issue a fresh `get` / `computeIfAbsent` — they do not inherit some previous caller's in-flight parked decision.

Admission and publish paths both check `bucketRef ne bucketFor(key)` inside the modify closure — if orphaned, abort. Combined with `drainPromises`' CAS traversal of the old vector, this eliminates the orphan-bucket hang window.

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

### Round 14 (persistent concerns during the per-bucket-traversal design)

| # | Finding | Disposition |
|---|---------|-------------|
| 14.1 | Same-key put/invalidate can leak stale to waiters (sub-nanosecond window between atomic read and branch execution) | **Fixed** by moving to the atomic swap design (Round 16): waiters detect swaps via `Ref2` identity from a single pointer compare, and per-key `keyEpochs` is read from the SAME `Ref2` they parked on (no inter-atomic race). |
| 14.2 | `shutdown` breaking | **Accepted.** Renamed to `close` in Round 15 per user directive (no backward compat concern). |
| 14.3 | `invalidateAll` not globally atomic — in-flight producer on not-yet-visited bucket can publish and wake waiters with pre-flush value | **Fixed** in Round 16 via atomic structure swap + orphan-bucket publish guard: producer's publish checks `bucketRef ne bucketFor(key)` and skips if orphan. |

### Round 15 (API clean-up per user directive)

User: *"on shutdown: let's rename to 'close'"*.

| # | Action | Status |
|---|--------|--------|
| 15.1 | Rename `BIOCache.shutdown` → `BIOCache.close` in trait + impl + tests | **Done.** Name now matches terminal-close convention (AutoCloseable / Cats Effect Resource). Backward compat was explicitly not a concern. |

### Round 16 (atomic swap redesign per user directive)

User: *"why can't we just create a new empty underlying data structure and swap existing one with the new atomically?"*. This triggered a fundamental redesign: `invalidateAll` and `close` replace the entire bucket vector atomically via `AtomicReference.getAndSet`; waiters detect swaps via bucket `Ref2` identity.

| # | Change | Status |
|---|--------|--------|
| 16.1 | `buckets: Vector` field → `structureRef: AtomicReference[Vector]` | **Done.** `bucketFor(key)` reads `structureRef` fresh every call. |
| 16.2 | Remove `BucketState.invGen` and `BucketState.closed` | **Done.** Barrier detection moves to `Ref2` identity (swap) + cache-wide `closedFlag` (close). |
| 16.3 | `WaitCtx.parkedInvGen: Long` → `WaitCtx.parkedBucketRef: Ref2[...]` | **Done.** Waiters compare via `eq` on wake. |
| 16.4 | `invalidateAll` rewritten as `structureRef.getAndSet(freshVec) + drainPromises(oldVec)` | **Done.** O(1) atomic. |
| 16.5 | `close` rewritten as `closedFlag := true; structureRef.getAndSet(freshVec); drainPromises(oldVec)` | **Done.** Cache-wide fence. |
| 16.6 | New regression tests: structure-swap detection by waiter; pre-swap producer publishes to orphan; post-close admission defects | **Added** (3 tests). |

### Round 17 (Codex review of the atomic-swap redesign)

| # | Finding | Fix |
|---|---------|-----|
| 17.1 (HIGH) | `invalidateAll` can still reintroduce waiter hangs. `computeImpl` captures a bucket ref once and can install a new `Computing` on the orphaned old vector after the atomic swap. A later caller that also captured the orphan parks on a promise no public op can ever signal again. | **Fixed.** Inside `computeImpl`'s admission modify closure, check `bucketRef ne bucketFor(key)`; if orphan, return `ActionSwapped` and re-enter `computeImpl` against the fresh structure. Combined with `drainPromises`' CAS on old buckets (which picks up any Computing slipped in before the check), no orphan hang is possible. |
| 17.2 (HIGH) | `close` does not fully fence in-flight admissions on the old structure. The `closedFlag` read is not part of the bucket CAS: a fiber can observe `closedFlag == false` in the modify closure, then close flips it and swaps the structure, and the old-bucket CAS can still commit. | **Fixed.** Same orphan-bucket guard (`bucketRef ne bucketFor(key)`) in admission AND publish modify closures. After the swap, drain signals the Computing's promise with `None`; waiters retry, observe `closedFlag`, fail fast. |
| 17.3 (MED) | Computed TTL publication uses a stale timestamp across CAS retries. `insertNano` and `expiry` were fixed before entering the retrying publish modify; under contention this shortens TTL arbitrarily and can publish an already-expired entry. | **Fixed.** Moved `System.nanoTime()` AND `computeExpiry(nowNano, ttl)` INSIDE the publish modify closure so every CAS retry uses a fresh clock. |

Regression tests added in Round 17:
- `invalidateAll + concurrent computeIfAbsent: no orphan-admission hangs [Codex HIGH regression]` (stress: 100 concurrent computeIfAbsent × 50 invalidateAll; timeout = hang = fail)
- `close + concurrent computeIfAbsent: no orphan-admission hangs [Codex HIGH regression]` (50 concurrent computeIfAbsent racing close; all must terminate)
- `publish modify orphan check: pre-swap producer's Ready does NOT land on live structure [Codex HIGH regression]` (deterministic: producer + invalidateAll + producer publish; assert post-swap get returns None)

### Round 18 (Codex review of Round 17's fixes)

| # | Finding | Fix |
|---|---------|-----|
| 18.1 (HIGH) | `close` still does not linearly fence loader admission. `computeImpl` reads `structureRef` and `closedFlag` INSIDE the bucket-modify closure, but neither value is part of the bucket CAS that actually installs `Computing`. A caller whose closure evaluated both guards as open just before `close` flips `closedFlag` and swaps `structureRef` can still win the old-bucket CAS afterward and start `doCompute` — violating the terminal-`close` contract that admissions are rejected BEFORE the loader runs. | **Fixed.** Added Stage 2 post-CAS rollback in `computeImpl`: after the admission CAS commits with `ActionCompute`, re-read `closedFlag` and `bucketFor(key)`. If either has moved, `update_` the (now orphan) bucket to remove our `Computing`, signal the promise `None` so any waiter that parked on us between CAS and re-check wakes and retries, then fail fast (closed) or re-enter `computeImpl` (swapped). `ActionHit`/`ActionWait` get lighter post-CAS checks (stale-hit guard / waiter-side freshness in `awaitAndRetry`). |
| 18.2 (MED) | Round 17's `close + concurrent computeIfAbsent` regression test is stress-only; `publish modify orphan check` deliberately resumes the producer only AFTER `invalidateAll` has already completed. Neither forces the critical interleaving where the guard sees the old structure AND the old-bucket CAS commits after the swap. | **Fixed.** Added `close fence: no compute invocation starts AFTER close has returned [Codex HIGH regression]`: 80 iterations × 64 concurrent admissions race a single `close`; every `compute` reads an external `AtomicBoolean` that is flipped the instant `close` returns, and any post-close `compute` entry is counted as a violation. Test is deterministic in its assertion (violation count must be zero), stress-driven in its interleaving coverage. |
| 18.3 (MED) | Resource-safe eager constructor still missing. Design draft specifies `makeEagerResource`, but the public API only exposes `makeEager` / `makeEagerWithRef` with scaladoc that explicitly warns an interrupt between `makeEager` returning and caller-installed cleanup leaks the eviction fiber. | **Fixed.** Added `BIOCache.makeEagerResource` and `BIOCache.makeEagerResourceWithRef` returning `Lifecycle[F[Nothing, *], BIOCache[F, K, V]]` via `Lifecycle.make(acquire)(release = _.close)`. Raw `makeEager` scaladoc now points at it as the preferred entry point while retaining bracket-example for callers that still want the effect form. |

Regression tests added in Round 18:
- `close fence: no compute invocation starts AFTER close has returned [Codex HIGH regression]` (Stage 2 post-CAS rollback coverage; see 18.2)

Total regression test count: 90 (89 prior + 1 new in Round 18).

### Round 19 (Codex stop-hook review of Round 18's fixes)

| # | Finding | Fix |
|---|---------|-----|
| 19.1 (HIGH) | Concurrent `close` can return before teardown is finished. Round 17/18 used `closedFlag.getAndSet(true)` as the serialization point: the caller that observes `false` (winner) runs teardown; the caller that observes `true` (loser) returned `F.unit` IMMEDIATELY while the winner was still interrupting the eviction fiber, swapping `structureRef`, and draining promises. A loser whose `close` "returned" could therefore observe mid-teardown state (e.g., the pre-swap vector still reachable via `structureRef`), violating the contract that once ANY `close` returns, the cache is fully quiesced. | **Fixed.** Replaced `closedFlag.getAndSet` as the linearization point with a lazy CAS-installed `Promise2`: `closedPromiseRef: AtomicReference[Promise2[F, Nothing, Unit]]`. First `close` caller creates a candidate promise, CAS-installs it, flips `closedFlag` and runs teardown; post-teardown it signals the promise. Losers observe the installed promise and `await` it — they return only after the winner's teardown is observably complete. Promise is created lazily inside `close` via `P.mkPromise` so caches that are never closed don't allocate it. |

Regression tests added in Round 19:
- Initial attempt: `concurrent close callers all return only after teardown is complete [Codex HIGH regression]` (asserted post-close `computeIfAbsent` defects). See Round 20.1 for why this was insufficient.

### Round 20 (Codex stop-hook review of Round 19)

| # | Finding | Fix |
|---|---------|-----|
| 20.1 (HIGH) | Round 19's `close` linearization still masks teardown failure. Winner wrapped teardown in `F.guarantee(teardown, winnerPromise.succeed(()))`. On teardown defect, `F.guarantee`'s cleanup still ran `succeed(())` — losers' `await` resolved with success, returning `F.unit` as if teardown had completed, even though the defect was (only) propagated to the winner's own call. Defect masking on losers violates the contract that all callers observe the same terminal outcome. | **Fixed.** Replaced `F.guarantee` with explicit `sandboxExit` + branch: on `Exit.Success` signal `winnerPromise.succeed(())`; on `Exit.Failure` signal `winnerPromise.terminate(f.trace.toThrowable)` so losers' `await` re-raises the same defect. Winner re-raises via `F.fromSandboxExit(exit)` so its own call also reflects the failure. No caller can return `F.unit` unless teardown genuinely succeeded. |
| 20.2 (MED) | Round 19's regression test only asserts that post-close `computeIfAbsent` defects. That check is satisfied by `closedFlag` alone, which the winner sets BEFORE teardown — losers that returned early (pre-swap) would still see `closedFlag=true` and the admission would defect. The test therefore passes even against the broken `F.guarantee` version. | **Fixed.** Rewrote the regression to pre-populate `entriesToPopulate=32` entries, then run `concurrentClosers=16` close callers. After each caller's `close` returns, it calls `cache.get` on every pre-populated key and counts how many still return `Some(_)`. With the `structureRef` swap observed, every post-close `get` must return `None`; any leak proves the caller returned before the swap was observable — directly verifying teardown-completion ordering (not just `closedFlag`). |

Regression tests updated in Round 20:
- `concurrent close callers all return only after teardown is complete [Codex HIGH regression]` — now pins structureRef-swap visibility via post-close `get` leak count (see 20.2).

Total regression test count: 91 (test was rewritten in place, not added — Round 19's count still holds).

### Round 21 (Codex stop-hook review of Round 20)

| # | Finding | Fix |
|---|---------|-----|
| 21.1 (HIGH) | Loser `close` calls still observe a different teardown defect than the winner on ZIO. Round 20 signaled losers via `winnerPromise.terminate(f.trace.toThrowable)`, which collapses a multi-defect `Exit.Termination` (or structured interrupt trace) into a single `Die(throwable)`. Losers' `await` re-raises that single-throwable `Die`; the winner's own `F.fromSandboxExit(exit)` re-raises the original structured Exit. So winner and losers observe different causes — the loser-path would hide accumulated defects or collapse an interrupt's trace. | **Fixed.** Changed the close promise payload from `Unit` to `Exit.Uninterrupted[Nothing, Unit]`. Winner signals `winnerPromise.succeed(exit)` with the raw sandboxed exit; losers `await` the exit and replay it via `F.fromSandboxExit`. Both winner and losers produce the SAME terminal effect — full trace, all accumulated defects, interrupt identity preserved. `Promise2`'s `fail`/`terminate` channels are bypassed entirely; they cannot carry `Exit`'s structured shape. |

Regression tests added in Round 21:
- `concurrent close: losers observe the SAME defect as the winner [Codex HIGH regression]` — instrumented `Primitives2.mkRef` terminates on the first `freshBuckets` allocation inside the winner's `close`; 8 concurrent close callers all sandbox-exit their `close` call; the test asserts every caller observes a failure (not just the winner). A `Success` on any loser proves either the promise payload lost fidelity or the signaling branched on success-only.

Total regression test count: 92 (91 prior + 1 new in Round 21).

### Round 22 (Codex stop-hook review of Round 21)

| # | Finding | Fix |
|---|---------|-----|
| 22.1 (MED) | Round 21's docs overclaimed "full trace, all accumulated defects, and interrupt identity are preserved verbatim". That is false: the default `IO2.fromSandboxExit` implementation in `fundamentals-bio/src/main/scala/izumi/functional/bio/IO2.scala` passes only `compoundException` to `terminate`, dropping `allExceptions` and the original `trace`. Because winner AND losers BOTH go through `F.fromSandboxExit`, they collapse identically — consistency IS preserved, but maximum fidelity is not. The claim was stronger than the code warrants. | **Fixed.** Docs (`ConcurrentHashMapCache` field comment, close-branch comments, design doc §6.9) rewritten to describe the actual invariant: every caller applies the SAME `F.fromSandboxExit(exit)` to the SAME `exit` value, so observable outcomes are identical, but whatever collapsing `fromSandboxExit` does is applied identically to all. A backend wanting perfect fidelity can override `IO2.fromSandboxExit`; this design permits that but does not depend on it. |
| 22.2 (MED) | Round 21's regression only asserted each caller's Exit was `Exit.Failure[_]` — it did not assert identity of the observable throwable. With only an existence check, a bug that gave winner a real `Exit.Termination` and losers a synthesized `Die(otherThrowable)` would still pass. | **Fixed.** Regression strengthened to collect every caller's `Exit.Termination.compoundException` and assert all of them are `eq` to the sentinel defect. Any drift between winner and losers (e.g., a regression to the `Promise2.terminate(throwable)` path) would now fail the identity check while still passing the existence check. |

Total regression test count: 92 (Round 21's test was strengthened in place, not added).

### Round 23 (Codex stop-hook review of Round 22)

| # | Finding | Fix |
|---|---------|-----|
| 23.1 (MED) | Round 22's strengthened regression still does not prove the claimed winner/loser replay invariant. The `eq`-check on `compoundException` passes under BOTH the fix (Exit-valued `Promise2.succeed(exit)` + shared `F.fromSandboxExit`) AND the broken `Promise2.terminate(throwable)` path — both re-raise the same sentinel singleton, so output-only checks cannot distinguish them. The invariant is structural (the code USES `succeed(Exit)`, never `terminate`) and is not externally observable from exit shape alone. | **Fixed.** Added a `Primitives2` Promise2 wrapper that counts `succeed`, `fail`, `terminate`, and `await` calls. The test now asserts: exactly 1 `succeed` call (winner publishing the Exit), 0 `fail`/`terminate` calls (the fix routes ALL teardown results through `succeed(exit)`), and `concurrentClosers - 1` `await` calls (each loser blocks on the winner's promise). The existence + identity checks combined with the structural count check fully pin the Exit-valued promise replay path. A regression to `terminate(throwable)` would trip `terminate > 0 ∨ succeed < 1`; a regression to `succeed(())` masking would still trip the identity check via masked defects. |

Total regression test count: 92 (Round 21's test was strengthened in place again, not added).

### Round 24 (Codex stop-hook review of Round 23)

| # | Finding | Fix |
|---|---------|-----|
| 24.1 (MED) | Round 23's structural counts (succeed==1, terminate==0, await==losers) do NOT prove that what winner publishes is what losers replay. A bug where winner called `succeed(some_exit)` but synthesized a different `exit` locally for its own `fromSandboxExit` would still satisfy all counts AND the sentinel identity check (because the sentinel singleton flows through both paths). The claim "fully pin the replay invariant" was stronger than the tests demonstrated. | **Fixed.** Added a third structural layer: payload-identity check. The instrumented `Promise2` wrapper now captures the EXACT `Exit` instance passed to `succeed(a)` into `succeededExitRef` and appends each return value of `await` into `awaitedExits`. The test asserts every awaited value is `eq` to the captured succeed-argument. This proves the promise is the data channel, not just a coordination signal — a per-caller synthesized `Exit` would trip this even when counts match. Combined with the existing count + identity layers, three layers now witness the invariant: (a) call-count (`succeed==1`, `terminate==0`, `await==losers`), (b) payload identity (all `await` returns `eq` to the one `succeed` argument), (c) observable throwable identity (all re-raised causes `eq` to the sentinel). |

Tightened the Round 23 claim from "fully pin" to "witness": the three layers are mutually reinforcing but the abstraction boundary (Promise2 + IO2.fromSandboxExit) ultimately caps the observable distinctions the test can make. A backend that overrode `IO2.fromSandboxExit` for higher fidelity would still satisfy all three layers.

### Round 26 (deliberate simplification)

User directive: the per-key `keyEpochs: Map[K, Long]` per-bucket barrier is too expensive for the invariant it provides.

| # | Change | Rationale |
|---|--------|-----------|
| 26.1 | Removed `BucketState.keyEpochs: Map[K, Long]` | It carried a whole `Map[K, Long]` per bucket — growing monotonically with distinct `put`/`invalidate`d keys until `invalidateAll` — to enforce "waiter retries past `put`/`invalidate` that linearizes after producer signal but before waiter wake". That invariant is a choice, not a requirement: Guava's own `LoadingCache` does NOT enforce it. The producer's caller already received `v_A` per loader-result semantics; forcing waiters (which received `v_A` via the producer's promise) to retry just to see the post-signal `put`'s value is a strict-freshness-for-waiters property with no caller-observable benefit. |
| 26.2 | Removed `WaitCtx.parkedKeyEpoch: Long`, `bumpKeyEpoch` helper, per-key branch in `awaitAndRetry` | All dependent machinery. Waiter freshness check is now two reads: `closedFlag.get()` and `bucketFor(key)` (compared to `parkedBucketRef`). |
| 26.3 | `put` / `invalidate` still displace in-flight `Computing(k, …)` by `None`-signaling their promises | Unchanged liveness guarantee: a waiter that hadn't been signaled yet wakes and retries. Only waiters that ALREADY received `Some(v_A)` from the producer are unaffected — they return `v_A` per Guava loader-result dedup. |
| 26.4 | 6 regression tests flipped from "waiter retries past barrier" to "waiter returns producer's v (Guava loader-result dedup)" | Tests were asserting the invariant we deliberately dropped. Rewrote their assertions (and headers) to pin the new, simpler invariant. |

Memory reclaimed: `BucketState` is now `case class BucketState[K, S](entries: List[BucketEntry[K, S]])` — single field. No per-key maps, no monotonic counters, no pruning concerns.

Regression tests still pass: 92/92.

### Round 25 (Codex stop-hook review of Round 24)

| # | Finding | Fix |
|---|---------|-----|
| 25.1 (MED) | Round 24's payload-identity check proved every LOSER receives the published Exit, but did not prove the WINNER re-raised from that same published Exit. The winner's code used a local `exit` variable for its own `F.fromSandboxExit(exit)`; a hypothetical regression where the winner synthesized the same sentinel via a different code path (e.g., re-building the Exit locally or using `F.terminate(sameThrowable)`) would be observationally equivalent and would pass all three Round 24 layers. The winner-path data channel was not mechanically constrained. | **Fixed at the source, not the test.** Refactored `close` so the winner re-raises via the SAME code path as losers: after `succeed(exit)`, the winner calls `F.flatMap(winnerPromise.await)(F.fromSandboxExit(_))` — the exact expression losers use. The data channel is now unambiguous: EVERY caller's terminal effect comes from `winnerPromise.await`, so the published Exit is mechanically the SOLE source. The winner's branch only runs the teardown + publish; the replay is universal. Test's await-count assertion was tightened from `concurrentClosers - 1` to `concurrentClosers` to witness the unified path — a regression where the winner re-raised from a local variable would trip this. |

Total regression test count: 92 (Round 21's test was strengthened in place once more; implementation refactor does the heavy lifting in Round 25).

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
- **Fix:** Wrapped in `uninterruptible` + `guaranteeOnFailure` that reads `fiberRef.get` and interrupts any installed fiber. Regression test: "createWithEviction construction under interrupt: every successful build has a working close".

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

A waiter parked on producer A's promise, woken with `Some(v_A)`, performs two atomic reads — `closedFlag.get()` and `bucketFor(key)` (to compare against its captured `parkedBucketRef`) — and decides:

| Condition                                             | Decision       | Rationale                                                                    |
| ----------------------------------------------------- | -------------- | ---------------------------------------------------------------------------- |
| `closedFlag.get() == true`                            | `failClosed`   | `close` has been observed; cache is terminally closed                        |
| `bucketFor(key) ne parkedBucketRef`                   | retry          | `invalidateAll` / `close` atomically swapped the structure                   |
| otherwise                                             | return `v_A`   | Guava loader-result dedup: producer computed `v_A`, waiter gets `v_A`        |

A waiter woken with `None` retries via `computeImpl` unconditionally (which re-checks `closedFlag` inside its admission modify and fails fast if set).

No per-key barrier check: a racing `put(k)` / `invalidate(k)` that linearizes between the producer's signal and the waiter's wake does NOT force a waiter retry. The producer computed `v_A`; the waiter receives `v_A`. `put`'s newer value is observed by subsequent calls on the post-displacement bucket, not by already-signaled waiters. This is Guava's loader-result contract. The per-key `keyEpochs` map that used to enforce a retry here was deleted — it carried a whole `Map[K, Long]` per bucket for a property with no caller-observable benefit.

---

## Accepted tradeoffs

### `close` name and terminal semantics

**Codex said:** `shutdown` → `close` is a public API break.

**Our decision:** Rename **accepted**. Backward compat is not a concern on the `wip/bio-cache` branch. The method is named `close`, matching standard terminal-teardown idioms (`java.lang.AutoCloseable`, Cats Effect `Resource`). Terminal semantics (post-close `computeIfAbsent` defects; `put`/`invalidate*` are no-ops) prevent post-teardown loader execution — a real safety hazard for side-effecting loaders.

### Strict-freshness-for-waiters deliberately dropped

**Earlier Codex concern:** parked waiters can return stale producer values after a later `put`/`invalidate`. Prior rounds added first tombstones, then a per-key `keyEpochs: Map[K, Long]` per bucket to force waiter retry on explicit user-intent barriers.

**Our final decision:** **Dropped in Round 26.** `keyEpochs` carried a whole `Map[K, Long]` per bucket — growing monotonically with distinct `put`/`invalidate`d keys — to enforce an invariant with no caller-observable benefit: the producer's OWN caller already got `v_A` per Guava loader-result semantics; forcing the waiter to retry just so it could also see the post-signal `put`'s value instead of `v_A` was a choice, not a requirement. We align with Guava's actual behavior instead: once a waiter receives `Some(v_A)` from its producer's promise, it returns `v_A`. `put`'s newer value is observed by subsequent `computeIfAbsent` / `get` calls.

Memory reclaimed: the per-bucket `Map[K, Long]` is gone. The `bumpKeyEpoch` helper, `parkedKeyEpoch` field in `WaitCtx`, and the per-key branch in `awaitAndRetry` are also gone.

### invalidateAll blocks in-flight-op visibility, not in-flight-op execution

**Codex said:** `invalidateAll` should be a cache-wide barrier that prevents pre-call producers from leaking stale values.

**Our final decision under the swap design:** Pre-call in-flight operations continue on the orphaned structure and their effects are INVISIBLE to post-call readers. Specifically:
- A pre-call producer's Ready publish lands on the orphan bucket. Post-call `get` reads the fresh empty vector and returns None. ✓
- A pre-call producer's promise signal reaches waiters parked on the old bucket Ref2. Those waiters detect the Ref2 identity mismatch on wake and retry against the fresh vector. ✓
- A pre-call producer's caller receives its own `v` per Guava loader-result semantics — but the cache is empty.

This is strictly stronger than the old per-bucket-traversal design: under that design, a pre-call producer could land a Ready in a not-yet-visited bucket that WAS visible to a post-call reader. Under the swap design, nothing pre-call leaks to post-call readers.

What remains non-atomic: a `put` that races the swap (caller captured the old vector pre-swap) writes to an orphan bucket — its effect is invisible. From the caller's POV, their `put` "succeeded" but subsequent reads from a different fiber don't see it. This matches `ConcurrentHashMap.clear` semantics.

### Strict-linearization window for `closedFlag` read

**Codex said:** Between a waiter reading `closedFlag.get() == false` and returning `v`, `close` can flip the flag.

**Our decision:** Accept. The waiter's decision linearizes at its read point; `close` linearizes after. A waiter that read `closedFlag == false` and committed to return its `v` has its call linearized before the close — correct. The alternative would require serializing all waiter reads through the close lock, which we don't want.

---

## Regression test coverage (92 tests)

Tests that specifically pin down Codex-flagged scenarios:

| Test name                                                                                                          | Covers                                                 |
| ------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------ |
| put AFTER producer signaled: waiter still returns producer's value (Guava loader-result dedup)                     | Round 26 — strict-freshness-for-waiters deliberately dropped |
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
| close releases parked waiters on a wedged producer with IllegalStateException                                      | Close liveness                                         |
| put releases waiter on a wedged producer WITHOUT needing close                                                     | Release without teardown                               |
| caller-side Temp.timeout on producer unblocks parked waiters (recommended liveness pattern)                        | Timeout pattern                                        |
| waiter's caller-side timeout frees only itself, not peers                                                          | Timeout scope                                          |
| interrupting computing fiber should unblock waiters                                                                | Interrupt liveness                                     |
| eager eviction + zero TTL: waiter still receives producer's value via promise                                      | Promise-payload handoff                                |
| concurrent callers with zero TTL: producer runs compute once; waiter receives producer's value                     | Dedup under zero TTL                                   |
| N waiters woken with empty cache dedup their retries (at most 1 recompute across N)                                | Retry dedup                                            |
| computeImpl does not orphan Computing when compute is interrupted                                                  | Interrupt cleanup                                      |
| invalidateAll atomic-swap: pre-call puts are cleared; concurrent puts may land on old or new structure             | Pins swap semantics (accepted tradeoff)                |
| createWithEviction construction under interrupt: every successful build has a working close                        | Leak-safe construction                                 |
| stress: concurrent computeIfAbsent and put converge on cache-consistent final state                                | Stress                                                 |
| stress: concurrent computeIfAbsent with a waiter and racing put yields consistent results                          | Stress                                                 |
| invalidateAll swap: parked waiter detects structure replacement and retries [atomic-swap regression]               | Round 16 — Ref2 identity swap detection                |
| invalidateAll swap: pre-swap producer publishes to orphan bucket; post-swap get returns None                       | Round 16 — orphan-bucket publish invisibility          |
| close swap: post-close computeIfAbsent admission defects with IllegalStateException                                | Round 16 — closedFlag admission fence                  |
| invalidateAll + concurrent computeIfAbsent: no orphan-admission hangs                                              | Round 17 — admission orphan-bucket guard               |
| close + concurrent computeIfAbsent: no orphan-admission hangs                                                      | Round 17 — close+admission orphan-bucket guard         |
| publish modify orphan check: pre-swap producer's Ready does NOT land on live structure                             | Round 17 — publish orphan-bucket guard                 |
| close fence: no compute invocation starts AFTER close has returned [Codex HIGH regression]                         | Round 18 — Stage 2 post-CAS admission rollback         |
| concurrent close callers all return only after teardown is complete [Codex HIGH regression]                        | Round 19 — lazy CAS-installed close promise            |
| concurrent close: losers observe the SAME defect as the winner [Codex HIGH regression]                             | Round 21 — Exit-valued close promise                   |

---

## Open questions for user review

1. **Producer interruption on close.** Codex asked for this. We chose not to implement: tracking all in-flight producer fibers adds complexity and memory, and conflicts with loader-result semantics (the producer's caller already has a handle to `compute`'s result). Producers naturally terminate once their compute finishes; close only needs to release WAITERS, which it does via `None` signaling.

2. **Globally-atomic flush variant.** If callers need to serialize in-flight operations against flush (stricter than ConcurrentHashMap.clear), a separate type backed by a single `Ref[CacheState]` would serialize everything through one CAS. Would be slower but atomic. Not needed for the current use case; ~documentation-only.
