# Interruption Signal Investigation

## Problem Statement

Rare failures to interrupt tests running in `Identity` effect type in `InterruptionTestBlockingZIO_AllEffects`.
- With `v_good` (fork + CompletableFuture wait): rare Identity test non-interruption
- With `v_badRunOrFork` (runOrFork): also fails to interrupt inner ZIO tests

## Original Prompt Context

Goals:
1. Trace execution path, document findings (this file)
2. Reproduce Identity non-interruption in `ZIORunOrForkInterruptedFlagReproTest.scala`
3. Reproduce ZIO non-interruption with `v_badRunOrFork` in repro test

Key files:
- Test: `distage/distage-testkit-scalatest/.jvm/src/test/scala/izumi/distage/testkit/distagesuite/interruption/InterruptionTest.scala`
- ZIO Runner: `fundamentals/fundamentals-bio/.jvm/src/main/scala/izumi/functional/bio/UnsafeRun2.scala` (ZIORunner class, v_good/v_badRunOrFork methods)
- Repro test: `fundamentals/fundamentals-bio/.jvm/src/test/scala/zio/test/ZIORunOrForkInterruptedFlagReproTest.scala`
- RunnerToF (JVM blocking): `distage/distage-testkit-core/.jvm/src/main/scala/izumi/distage/testkit/runner/impl/RunnerToFPlatformSpecific.scala`
- QuasiIORunner: `fundamentals/fundamentals-bio/.jvm/src/main/scala/izumi/functional/quasi/QuasiIORunner.scala`
- Identity par traverse: `fundamentals/fundamentals-bio/.jvm/src/main/scala/izumi/functional/quasi/__QuasiAsyncPlatformSpecific.scala`
- QuasiIO Identity: `fundamentals/fundamentals-bio/src/main/scala/izumi/functional/quasi/QuasiIO.scala` (QuasiIOIdentity object)
- QuasiAsync/QuasiTemporal: `fundamentals/fundamentals-bio/src/main/scala/izumi/functional/quasi/QuasiAsync.scala`
- Blocking runtime: `distage/distage-testkit-scalatest/.jvm/src/main/scala/izumi/distage/testkit/services/scalatest/dstest/TestRunnerRuntimePlatformSpecific.scala`
- Test runner: `distage/distage-testkit-core/src/main/scala/izumi/distage/testkit/runner/impl/DistageTestRunner.scala`
- Test tree runner: `distage/distage-testkit-core/src/main/scala/izumi/distage/testkit/runner/impl/TestTreeRunner.scala`
- Test dispatch: `distage/distage-testkit-core/src/main/scala/izumi/distage/testkit/runner/TestkitRunnerModule.scala`
- Exit/ZIOExit: `fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Exit.scala`
- Reproduction script: `debug/interruption-loop.sh`

## Architecture: Full Execution Path

### Class Hierarchy

`InterruptionTestBlockingZIO_AllEffects` extends `InterruptionTest` extends `Spec1[Identity]`.

`testRunnerRuntime()` returns `TestRunnerRuntime.defaultBlockingRuntimeFor[zio.Task]`.

### Thread and Fiber Topology

```
Thread t (test-created thread):
  _doRunTests(...)
    testRunnerRuntime().runTests(...)
      blockingRuntimeFor[zio.Task]:
        runnerLifecycle.use { zioRunner =>
          zioRunner.runBlocking(TestkitRunnerModule.runWithOverrides[zio.Task](...))
        }
```

`zioRunner.runBlocking(...)` = `UnsafeRun2.ZIORunner.unsafeRun(...)` = `v_good(zioEffect)`:

```
Thread t:
  v_good(outerZIOEffect):
    fork ZIO fiber F_outer
    CompletableFuture.get()  <-- thread t blocks here

ZIO Fiber F_outer (on ZIO executor):
  TestkitRunnerModule.runWithOverrides[zio.Task](...)
    DistageTestRunner[zio.Task].run(tests)
      parTraverseExt.groupedParTraverse(envs) {
        proceedEnv(identityEnv) → ZIO fiber F_id
        proceedEnv(catsEnv)     → ZIO fiber F_cats
        proceedEnv(zioEnv)      → ZIO fiber F_zio
      }
```

For each environment, `proceedEnv` calls `runnerToF.runToF(innerRunner, testTreeBody)`:

```
ZIO Fiber F_id:
  RunnerToF.BlockingImpl[zio.Task].runToF(identityRunner, testTreeBody):
    QuasiAsync[zio.Task].maybeSuspendInterruptible {  // = ZIO.attemptBlockingInterrupt
      scala.concurrent.blocking {
        QuasiIORunner.IdentityImpl.runBlocking(testTreeBody())  // = testTreeBody()
      }
    }
```

### Identity Test Tree Execution

Inside `testTreeBody()`, the test tree runner uses `QuasiAsync[Identity]` for parallel dispatch:

```
ZIO blocking thread B_id (running attemptBlockingInterrupt block):
  QuasiIORunner.IdentityImpl.runBlocking(testTreeBody()) = testTreeBody()
    TestTreeRunner[Identity].traverse(...)
      proceedMemoizationLevel(...)
        QuasiAsync[Identity].parTraverse_(suites) {          <-- OUTER parTraverse
          QuasiAsync[Identity].parTraverse_(tests) {         <-- INNER parTraverse
            runner.proceedTest(...)                            <-- individual test
          }
        }
```

`QuasiAsync[Identity].parTraverse_` is implemented by `__QuasiAsyncPlatformSpecific.parTraverseIdentityImpl`:

```
B_id (ZIO blocking thread):
  parTraverseIdentityImpl (suite-level):
    MiniBIO parTraverse → dispatches to QuasiAsyncIdentityBlockingIOPool
    Await.result(minibioFuture, Duration.Inf)  <-- B_id blocks here

    Worker threads S1, S2, S3 (from QuasiAsyncIdentityBlockingIOPool):
      S1: parTraverseIdentityImpl (test-level):
        MiniBIO parTraverse → dispatches to QuasiAsyncIdentityBlockingIOPool
        Await.result(minibioFuture, Duration.Inf)  <-- S1 blocks here

        Worker threads T1..T5 (from QuasiAsyncIdentityBlockingIOPool):
          T1: Thread.sleep(10_000)  <-- actual test body
          T2: Thread.sleep(11_000)
          ...
```

### Identity QuasiIO Semantics

For `Identity`, QuasiIO operations are trivial:
- `F.maybeSuspend(eff)` = `eff`
- `F.suspendF(eff)` = `eff`
- `F.guarantee(fa)(fin)` = `try { fa } finally { fin }`
- `F.guaranteeOnInterrupt(fa)(cleanup)` = `try { fa } catch { case t: InterruptedException => cleanup(t); throw t }`
- `FT.sleep(n.seconds)` = `Thread.sleep(n * 1000)`

So each individual test body reduces to:
```scala
try {
  try {
    countDownLatch.countDown()
    Thread.sleep(n * 1000)  // 10-14 seconds
  } catch {
    case t: InterruptedException => logger.info("interrupted"); throw t
  }
  signalNotInterrupted()   // THIS IS THE FAILURE SIGNAL
  logger.crit("not interrupted")
} finally {
  signalStopped()
}
```

### ZIO Inner Test Execution (zio.Task environment)

```
ZIO Fiber F_zio:
  RunnerToF.BlockingImpl[zio.Task].runToF(zioRunner, testTreeBody):
    ZIO.attemptBlockingInterrupt {
      blocking {
        QuasiIORunner.BIOImpl[ZIO].runBlocking(testTreeBody())
          = UnsafeRun2.ZIORunner.unsafeRun(testTreeBody())
          = v_good(testTreeBody())   <-- NESTED v_good!
      }
    }
```

For ZIO tests, there's a **nested ZIO runtime**: the outer ZIO is the test runner, the inner ZIO is the test's own runtime. The inner `v_good` forks another fiber and blocks on another CompletableFuture.

## Interrupt Signal Propagation Path

### Step 1: t.interrupt() → v_good catches InterruptedException

```
t.interrupt()
  → Thread t's interrupt flag set
  → CompletableFuture.get() throws InterruptedException
  → v_good catch block:
      wasInterrupted = true
      interruptionFiber = fork(fiber.interruptAs(FiberId.None))
      interruptedOneShot.get()   // wait for ZIO fiber interruption to complete
      Thread.currentThread().interrupt()  // restore interrupt flag
      resultFuture.get()  // should return immediately (fiber completed)
```

### Step 2: ZIO Fiber Tree Interruption

```
fiber.interruptAs(FiberId.None)
  → F_outer interrupted
  → collectAllParDiscard interrupt propagation:
      → F_id interrupted (Identity env)
      → F_cats interrupted (Cats env)
      → F_zio interrupted (ZIO env)
```

### Step 3: Identity Env Fiber Interruption

```
F_id interrupted:
  → attemptBlockingInterrupt's onInterrupt handler:
      → thread.interrupt() on B_id (the ZIO blocking thread)
  → B_id's Await.result() throws InterruptedException
  → parTraverseIdentityImpl catch block:
      → parTraverseThreads.forEach(_.interrupt())  // interrupt suite threads S1, S2, S3
      → throw InterruptedException
  → Each suite thread S is interrupted:
      → S's Await.result() throws InterruptedException
      → S's parTraverseIdentityImpl catch block:
          → parTraverseThreads.forEach(_.interrupt())  // interrupt test threads T1..T5
          → throw InterruptedException
  → Each test thread T is interrupted:
      → Thread.sleep() throws InterruptedException
      → guaranteeOnInterrupt handler: logger.info("interrupted")
      → InterruptedException re-thrown
```

### Step 4: ZIO Env Fiber Interruption (relevant for v_badRunOrFork)

```
F_zio interrupted:
  → attemptBlockingInterrupt's onInterrupt handler:
      → thread.interrupt() on B_zio (the ZIO blocking thread)
  → B_zio is blocked in inner v_good/v_badRunOrFork's CompletableFuture.get()
  → CompletableFuture.get() throws InterruptedException
  → Inner v_good catch block handles it (interrupts inner fiber, waits for completion)
```

## Potential Race Conditions

### Race 1: CompletableFuture.get() vs interrupt flag (v_good)

Java's `CompletableFuture.get()` checks `result != null` FIRST, before checking interrupt flag:

```java
public T get() throws InterruptedException, ExecutionException {
    Object r;
    if ((r = result) == null)     // <-- checks result FIRST
        r = waitingGet(true);     // <-- only enters interruptible wait if result is null
    return (T) reportGet(r);
}
```

If the ZIO fiber completes (result becomes non-null) before `get()` checks, the interrupt flag is IGNORED. The call returns normally without throwing InterruptedException.

**Impact**: If this happens in v_good, `wasInterrupted` stays false, no fiber interruption is triggered, and the interrupt flag is silently set but not acted upon. Tests continue running.

**Likelihood**: Very low for 10-second-sleep tests, but nonzero. The fiber must complete before the interrupt is detected by CompletableFuture.get().

### Race 2: Await.result() completion vs interrupt (parTraverseIdentityImpl)

Scala's `Await.result()` uses `AbstractQueuedSynchronizer.acquireSharedInterruptibly()` which checks `Thread.interrupted()` FIRST:

```java
if (Thread.interrupted())
    throw new InterruptedException();
if (tryAcquireShared(arg) < 0)
    doAcquireSharedInterruptibly(arg);
```

**Impact**: For `Await.result()`, interruption always takes priority over completion. This race is NOT a problem for Scala's `Await.result()`.

### Race 3: parTraverseThreads tracking vs interrupt delivery

In `parTraverseIdentityImpl`, worker threads add themselves to `parTraverseThreads` and remove themselves in `finally`:
```scala
parTraverseThreads.add(thread)
try { f(a) }
finally { parTraverseThreads.remove(thread) }
```

If a worker thread finishes its task and removes itself from the set BEFORE the interrupted parent iterates the set, the worker is not interrupted.

**Impact**: If a test somehow completes normally (not interrupted), the worker removes itself and is missed by the interrupt sweep. But tests sleep 10+ seconds, making this extremely unlikely.

### Race 4: ZIO attemptBlockingInterrupt thread capture

ZIO's `attemptBlockingInterrupt` captures a reference to the executing thread. When the fiber is interrupted, it calls `thread.interrupt()` on this reference. If the thread has already finished the blocking effect when the interrupt arrives, the `thread` reference may be null, and no interrupt is sent.

**Impact**: If the Identity test tree completes before the ZIO interrupt reaches the fiber, no interrupt is sent to the blocking thread.

### Race 5: OneShot.get() vs resultFuture completion (v_good)

After `interruptedOneShot.get()` returns, the original fiber should have an exit value. But there's a possible gap: the interruption fiber completes → OneShot fires → but the original fiber's observer hasn't fired on resultFuture yet. Then `Thread.currentThread().interrupt()` sets the flag, and `resultFuture.get()` might throw InterruptedException (uncaught!).

**Impact**: Could cause the runner thread to exit with an uncaught InterruptedException instead of properly propagating the interrupt.

### Race 6: v_badRunOrFork's fire-and-forget interruption

In `v_badRunOrFork`'s catch block:
```scala
case _: InterruptedException =>
  wasInterrupted = true
  runtime.unsafe.fork(fiber.interruptAs(FiberId.None))  // fire and forget!
```

The interruption is NOT awaited. Then:
```scala
if (wasInterrupted) Thread.currentThread().interrupt()
resultFuture.get()  // with interrupt flag set, may throw!
```

If `resultFuture` isn't complete yet (interruption still propagating), `resultFuture.get()` would throw a SECOND InterruptedException (uncaught), causing the runner to exit before interruption completes.

**Impact for ZIO tests**: The inner `v_badRunOrFork` fires the interrupt but doesn't wait for it to propagate. The outer `attemptBlockingInterrupt` catches the exception, but the inner ZIO fiber's tests may not have been interrupted yet.

## Key Difference: Repro Test vs Actual Architecture

The current repro test (`ZIORunOrForkInterruptedFlagReproTest`) uses:
- **Identity par traverse**: `Executors.newCachedThreadPool` + `Future { f(a) }` + `Await.result(Future.sequence(futures))`

The actual testkit uses:
- **Identity par traverse**: `__QuasiAsyncPlatformSpecific.parTraverseIdentityImpl` with **MiniBIO** fibers + `QuasiAsyncIdentityBlockingIOPool` (cached thread pool) + `Await.result(minibioFuture)`

### MiniBIO-based par traverse details

```scala
// __QuasiAsyncPlatformSpecific.parTraverseIdentityImpl
val parTraverseThreads = ConcurrentHashMap.newKeySet[Thread]()
val F = MiniBIOAsync.WeakAsyncForMiniBIOAsync
val future = parTraverseImpl(l.iterator.to(Iterable)) {
  a => F.syncBlocking {
    val thread = Thread.currentThread()
    parTraverseThreads.add(thread)
    try { f(a) }
    finally { parTraverseThreads.remove(thread) }
  }
}.runSyncToFirstAsyncBoundaryOrOnEC(ec)
val result = try {
  Await.result(future, Duration.Inf)
} catch {
  case t: InterruptedException =>
    parTraverseThreads.forEach(_.interrupt())
    throw t
}
```

Key characteristics:
1. Uses MiniBIO fibers (not Scala Futures) for parallelism
2. `syncBlocking` wraps each task in `syncThrowable(scala.concurrent.blocking(f))`
3. `runSyncToFirstAsyncBoundaryOrOnEC(ec)` produces a `Future[Exit.Uninterrupted[E, A]]`
4. Worker threads are from the shared `QuasiAsyncIdentityBlockingIOPool`
5. Interrupt propagation: main thread catches InterruptedException → iterates `parTraverseThreads` → interrupts workers
6. Nested: suite-level parTraverse dispatches to suite threads, each runs test-level parTraverse

The repro test's simple `Future { ... }` + `Future.sequence` approach may have different interrupt propagation characteristics than MiniBIO's fiber-based approach. To reproduce the issue, the repro test should use the same MiniBIO-based `parTraverseIdentityImpl` mechanism.

## PRIMARY HYPOTHESIS: Late-starting MiniBIO workers missed by interrupt cascade

### The CountDownLatch Issue

In `InterruptionTest`:
```scala
lazy val countDownStart: CountDownLatch = new CountDownLatch(tests.size - suites.size)
```

If `tests.size = 45` (9 suites * 5 tests) and `suites.size = 9`, then `countDownStart = 36`.
This means the latch reaches 0 after only 36 out of 45 tests call `countDown()`.
When `t.interrupt()` is called, up to 9 tests might NOT have started yet.

### The MiniBIO Worker Race

In `parTraverseIdentityImpl`, when the main thread catches `InterruptedException`:
```scala
case t: InterruptedException =>
  parTraverseThreads.forEach(_.interrupt())  // only interrupts threads IN the set
  throw t
```

If a MiniBIO worker hasn't started yet (queued on the EC), it's NOT in `parTraverseThreads`.
After `forEach` completes, the late worker starts, adds itself to the set, and enters Thread.sleep.
Nobody interrupts this worker.

### The earlyFailure race

MiniBIO's `parTraverseN_` has an `earlyFailure` mechanism:
```scala
def go(): MiniBIOAsync[E, Unit] = suspendSafe {
  if (earlyFailure.get().isDefined) { unit }  // bail out
  else { queue.poll() match { ... } }
}
```

But if a late worker checks `earlyFailure` BEFORE any interrupted worker sets it, the late worker
proceeds to run the test body. The window: between `earlyFailure.get()` returning None and the
late worker entering Thread.sleep, another worker's failure must propagate through MiniBIO's
`guaranteeOnFailure` to set `earlyFailure`.

### Complete Race Scenario

1. `countDownStart.await()` returns (36/45 tests started)
2. `t.interrupt()` → ZIO fiber interrupted → blocking thread interrupted
3. Suite thread S1 interrupted → `parTraverseThreads_s1.forEach(_.interrupt())` → 4 out of 5 test workers interrupted
4. Worker T1_5 was queued on EC, not yet running → NOT in `parTraverseThreads_s1`
5. `forEach` completes → S1 throws InterruptedException
6. T1_5 starts on EC → calls `go()` → `earlyFailure` not yet set → proceeds
7. T1_5 enters Thread.sleep(10_000) → nobody interrupts it
8. Thread.sleep completes normally → `signalNotInterrupted()` → allTestsInterrupted = false

### Why the repro test doesn't reproduce this

The repro test initializes the latch to the FULL test count:
```scala
startedLatch = new CountDownLatch(TotalTestsAllEffects)  // ALL tests
```

So ALL tests are guaranteed to have started when the interrupt is sent.
No late workers → no race condition.

## RESOLUTION: Bug in InterruptionTest latch initialization

### Root Cause

The bug was in `InterruptionTest.scala` itself, not in ZIO or unsafeRun implementations.

```scala
// BUG: tests.size = 45, suites.size = 9, so countDownStart = 36
lazy val countDownStart: CountDownLatch = new CountDownLatch(tests.size - suites.size)
```

This allowed the interrupt to fire after only 36 of 45 tests had started. Up to 9 tests
could be queued in the MiniBIO executor but not yet running when `parTraverseThreads.forEach(_.interrupt())`
was called, missing those workers entirely.

### Fix

Replaced CountDownLatch with per-test Promise handles derived from `tests`. Each test gets
its own Promise, and the main thread awaits `Future.sequence(allPromises)`. Since the
promises are structurally derived from `tests.map(_ => Promise[Unit]())`, a size mismatch
is impossible.

### Why the repro test couldn't reproduce

The repro test (`ZIORunOrForkInterruptedFlagReproTest`) used `CountDownLatch(TotalTestsAllEffects)`
which waited for ALL tests. This eliminated the race window entirely, so the repro test
correctly passed every time — the bug was specifically in InterruptionTest's latch count.

## Debug Run Commands

```bash
# Run the specific test class
direnv exec . sbt --batch ";project distage-testkit-scalatestJVM; testOnly izumi.distage.testkit.distagesuite.interruption.InterruptionTestBlockingZIO_AllEffects"

# Run the repro test
direnv exec . sbt --batch ";project fundamentals-bioJVM; testOnly zio.test.ZIORunOrForkInterruptedFlagReproTest"

# Run the full interruption loop
bash debug/interruption-loop.sh 10
```


### ADDENDUM

# Interruption Signal Investigation

## Problem Statement

Rare failures to interrupt tests running in `Identity` effect type in `InterruptionTestBlockingZIO_AllEffects`.
- With `v_good` (fork + CompletableFuture wait): rare Identity test non-interruption
- With `v_badRunOrFork` (runOrFork): also fails to interrupt inner ZIO tests

## Key files

- Test: `distage/distage-testkit-scalatest/.jvm/src/test/scala/izumi/distage/testkit/distagesuite/interruption/InterruptionTest.scala`
- ZIO Runner: `fundamentals/fundamentals-bio/.jvm/src/main/scala/izumi/functional/bio/UnsafeRun2.scala` (ZIORunner class, v_good/v_badRunOrFork methods)
- Repro test: `fundamentals/fundamentals-bio/.jvm/src/test/scala/zio/test/ZIORunOrForkInterruptedFlagReproTest.scala`
- Identity par traverse: `fundamentals/fundamentals-bio/.jvm/src/main/scala/izumi/functional/quasi/__QuasiAsyncPlatformSpecific.scala`
- Reproduction script: `debug/interruption-loop.sh`

## Root Cause Identity interruption failures: Bug in InterruptionTest latch initialization

The bug was in `InterruptionTest.scala` itself, not in ZIO or unsafeRun implementations.

```scala
// BUG: tests.size = 45, suites.size = 9, so countDownStart = 36
lazy val countDownStart: CountDownLatch = new CountDownLatch(tests.size - suites.size)
```

The test has 9 suites (3 effect types × 3 suites each) with 5 tests per suite = 45 tests total.
The CountDownLatch was initialized to `45 - 9 = 36`, so the interrupt fired after only 36 of 45
tests had called `countDown()`. Up to 9 tests could be queued on the MiniBIO executor but not yet
running when `parTraverseThreads.forEach(_.interrupt())` was called, missing those workers entirely.

### Race scenario

1. `countDownStart.await()` returns (36/45 tests started)
2. `t.interrupt()` → ZIO fiber interrupted → blocking thread interrupted
3. Suite thread S1 interrupted → `parTraverseThreads.forEach(_.interrupt())` → misses workers not yet in the set
4. Late-starting worker enters `Thread.sleep(...)` with nobody to interrupt it
5. Sleep completes normally → `signalNotInterrupted()` → test reports failure

### Why the repro test couldn't reproduce

The repro test (`ZIORunOrForkInterruptedFlagReproTest`) used `CountDownLatch(TotalTestsAllEffects)`
which waited for ALL tests. This eliminated the race window entirely, so the repro test
correctly passed every time.

## Fix Applied

Two changes to `InterruptionTest.scala`:

### 1. Replaced CountDownLatch with per-test unique Promises

Each `nSecondsTest(n)` creates its own `Promise[Unit]` for started and stopped signals
during suite construction. These promises are appended to shared `ConcurrentLinkedQueue[Future[Unit]]`
collectors (append-only, never polled). Each test body closes over its own unique promise.

The main thread awaits `Future.sequence(allStartedFutures)` and `Future.sequence(allStoppedFutures)`.

Structural guarantee: each `nSecondsTest` call both registers one `in { ... }` test AND appends
one future to the collector. After suite construction, `assert(startedPromises.size() == tests.size)`
validates the 1:1 correspondence. A size mismatch is structurally impossible.

## Next Steps: Verification

### Verify fix with parallel stress test

Run the interruption loop 3 times in parallel using git worktrees to stress-test
that the fix is correct under load:

```bash
# Create 3 worktrees from the current branch
BRANCH=$(git rev-parse --abbrev-ref HEAD)
git worktree add /tmp/exchange/wt-int-1 "$BRANCH"
git worktree add /tmp/exchange/wt-int-2 "$BRANCH"
git worktree add /tmp/exchange/wt-int-3 "$BRANCH"

# Run 30 iterations in each worktree in parallel
(cd /tmp/exchange/wt-int-1 && bash debug/interruption-loop.sh 30) &
(cd /tmp/exchange/wt-int-2 && bash debug/interruption-loop.sh 30) &
(cd /tmp/exchange/wt-int-3 && bash debug/interruption-loop.sh 30) &
wait
# Check all three logs for failures

# Cleanup
git worktree remove /tmp/exchange/wt-int-1
git worktree remove /tmp/exchange/wt-int-2
git worktree remove /tmp/exchange/wt-int-3
```

### Investigate v_badRunOrFork separately

The v_badRunOrFork ZIO non-interruption may be a real ZIO/unsafeRun issue
(Race 6 in the original analysis: fire-and-forget interruption without awaiting).
This is separate from the InterruptionTest latch bug and should be investigated
independently once the Identity fix is confirmed.
