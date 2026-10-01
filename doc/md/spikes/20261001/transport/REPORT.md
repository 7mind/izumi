# Spike 0c: real target transport under SBT

The aggregate transport was first observed 2026-10-01 in an isolated worktree based
on reviewed plan commit `4feabed5857067d051e357bc1aa62bbb68c96892`; original source
baseline `5be95fb6309c4b0cb9dbea37ed84c0db0a8ff870`. The host per-suite projection
was added and measured on 2026-10-01 in the main checkout (branch `wip/sbt-2`, HEAD
`8459c3c76a27f8c4c4454cab894b8d180650760a`), with the fixture and driver changes in
this directory not yet committed. No production code changed.

**Gate 0c passes for the stub transport in the measured matrix.** A plugin-owned
host projection wraps the Scala.js 1.22.0 and Scala Native 0.5.12 host-side proxy
frameworks. It turns one aggregate target task per sharing group into ordinary
per-suite SBT tasks.
- On SBT 1.13.0 and 2.0.9, for JS and Native at Scala 3.3.7, every selected suite
  gets its own listener group, JUnit file, `Tests.Output` entry and
  incremental-history record.
- Each sharing group acquires and releases the stub resource once.
- A forced aggregate failure or target-process death is memoized for its group and
  reported as an error for every suite of that group.
- An SBT 2 smoke run at Scala 3.9.0 passes on both platforms.

The final driver run `20261001-193528` passed all 23 SBT invocations (105 checked
steps). This stub does not establish the real engine closure, streaming results,
cancellation, or Native framing for a plugin-owned launcher; see
[Residual risks](#residual-risks-and-unverified-behavior). The earlier aggregate-only
measurements, which made the gate partial, are kept under
[History](#history-aggregate-only-transport-previous-status-partial).

## Host projection

[`fixture/project/TransportProjection.scala`](fixture/project/TransportProjection.scala)
is a single meta-build source. SBT 1 compiles it with Scala 2.12.21 and SBT 2 with
Scala 3.8.4, which reports three deprecation warnings for `_` type wildcards.

- `TransportJsProjectionPlugin` (`requires = ScalaJSPlugin`) and
  `TransportNativeProjectionPlugin` (`requires = ScalaNativePlugin`) are enabled
  explicitly on `js` and `native`. There are two plugins so that `requires` places
  each one's setting after its platform plugin's settings.
- The setting redefines `Test / loadedTestFrameworks` from its previous value, the
  platform plugin's map of host proxies (`FrameworkAdapter(TransportSpike)`). Only the
  `transport.TransportFramework` entry is wrapped; a missing entry fails the task.
- SBT 2 needs `Def.uncached`. On SBT 1, an implicit no-op `Def.uncached` extension
  lets the same source compile; this is the pattern Scala.js's own `PluginCompat`
  uses. SBT 2 resolves the real member before the extension.
- The default mode is `per-suite`. `-Dtransport.projection=aggregate` returns the map
  unchanged, which reproduces the earlier aggregate-only mapping. Each evaluation
  prints `PROJECTION_MODE`.
- `ProjectionRunner.tasks` chooses the sharing groups itself: all selected suites, or
  ABC and DE with `two-groups`. It calls the platform runner's `tasks` once per
  group. Each call must return exactly one aggregate target task, the target's
  existing representative-`TaskDef` task; otherwise `tasks` throws. It returns one
  host `SuiteTask` per selected stock `TaskDef`.
- The first `SuiteTask.execute` of a group runs the aggregate on its own SBT thread,
  with a buffering `EventHandler`. The platform adapters keep one JS runtime or
  native process per thread.
- Events arrive on the adapter's RPC thread and are keyed by
  `NestedTestSelector.suiteId()`. A non-nested selector, or a suite outside the
  group, turns the group outcome into a failure. An event that arrives after the
  aggregate returned is rejected.
- The outcome, failure included, is published once under a monitor. The other tasks
  of the group block until it exists and never relaunch the aggregate. A fatal error
  still publishes a failure outcome before it propagates.
- Each `SuiteTask` then emits only its own suite's events, inside its own `execute`,
  as ordinary events: its own `TaskDef` name and fingerprint, `TestSelector(testName)`,
  and the original status, throwable and duration. If the group failed, each task
  also emits one `Status.Error` event with a `SuiteSelector` and the memoized
  throwable, after any buffered events for its suite.

Target additions ([`shared/Transport.scala`](fixture/shared/Transport.scala)):
- `first-only` runs only `test1` of each selected suite, which makes a partial
  request.
- `abort-group` makes the target task throw before acquisition and before any event.
- `exit-group` makes the target process exit with status 3 at the same point
  (`Platform.exit`).
- `fail-b` and `two-groups` are unchanged; `StubApplication` now takes its
  tests-per-suite count explicitly.

Build additions ([`build.sbt`](fixture/build.sbt)):
- the two plugins;
- a `suiteResults` task that prints every `Tests.Output` entry. `sbt.SuiteResult` has
  no `toString`, so `show executeTests` names the suites but not their status;
- a `spikeCollect <label>` command that moves the explicit JUnit reports written
  since the previous step into `target/spike-collect/<label>`.

## Driver

[`verify.py`](verify.py) runs the SBT invocations one after another. Each invocation
is
`sbt -batch -Dsbt.supershell=false -sbt-version <v> [-Dtransport.projection=aggregate] 'spikeCollect start' <step commands> 'spikeCollect <step>' ...`,
with every command passed as a separate argument. The driver splits the log at the
`SPIKE_COLLECT` lines and checks each step against explicit expectations:
- the multiset of target `BODY` lines;
- the `CHECK` lines: one per executed group, `acquire=1 release=1`, with the group's
  body count;
- the projection traces: the groups, one `PROJECTION_LAUNCH` per group, and one
  `PROJECTION_EMIT` per selected suite with its event count and error flag;
- the target's `DESERIALIZE`, `ABORT` and `EXIT` counts, which show there was no
  relaunch;
- each collected JUnit file: its suite name, its test, failure and error counts, and
  its ordered `(classname, name, outcome)` cases;
- the `SUITE_RESULT` lines;
- the suites listed by `show executeTests`;
- five distinct `definedTestDigests`;
- the exit code.

A failing step is always the last one in its invocation, and its leftover reports are
collected after exit. Any mismatch fails the run. Captured outputs go to the ignored
`logs/<run>/` directory: the full SBT log, a JSON file with the parsed facts and the
exact argv of each invocation, the reports collected per step, and `summary.json`.

A negative control ran two `sbt1-main` steps, `js-testonly-ab` and `js-results`, with
their per-suite expectations but under `-Dtransport.projection=aggregate`. Both steps
failed on mode, groups, emits, report files and case lists, so the checks do
distinguish the old mapping.

SBT 2 records each test success in its action cache under (framework arguments,
suite digest). That cache is the OS-level global local cache
(`SysProp.globalLocalCache`, overridable with `sbt.global.localcache` or
`SBT_LOCAL_CACHE`), not the fixture's `target/`. `executeTests` also records
successes, because it uses the `test`-scoped listeners. A record left by an earlier
run, or by another checkout with an equal digest, can therefore satisfy `test`.

So that each observed skip is attributable to the check in progress, every SBT 2
history check first adds a fresh `Tests.Argument("history-<run>-...")`. The target
ignores the argument, but it changes `testOptionDigests` and therefore every suite
digest. The driver verifies the five distinct digests after setting it. In the final
run, the two `fail-b` invocations saw identical digests, and the full and partial
checks saw disjoint digests.

## Measured results

The values below come from the final run `20261001-193528`. `A`..`E` stand for
`transport.SuiteA`..`E`, and `P` for `js` or `native`. Each row was observed in all
four combinations (SBT 1.13.0 and SBT 2.0.9, each on JS and on Native, at Scala 3.3.7)
unless the row says otherwise.

| # | Commands | Observed |
| --- | --- | --- |
| 1 | `P/testOnly A B` | 6 bodies, 1 group, 1 launch; exactly two XML files, A and B, each with cases `test1..3` and classname equal to the suite |
| 1 | `P/test` (SBT 1) / `P/testFull` (SBT 2) | 15 bodies; five XML files with 3 cases each |
| 2 | `show P / Test / executeTests` | 15 bodies; the `Output` map lists A..E, each with its own `SuiteResult` |
| 2 | `P/suiteResults` | A..E `Passed`, 3 passed each |
| 2 | `fail-b`, then `show ...executeTests` and `P/suiteResults` | the map lists A..E; B `Failed` (passed=2, failed=1), A, C, D and E `Passed`; B's XML has a `failure` at `test1`, the other files have none |
| 3 | SBT 2, fresh digests: `P/testFull`, then `P/test` | five distinct digests; 15 bodies, then 0 |
| 3 | SBT 2, `fail-b` and fresh digests: `P/testFull` (exits 1), then `P/test` in a new invocation (exits 1) | five distinct digests, identical in both invocations; 15 bodies, then 3 (B only, one B group); B fails again |
| 3 | SBT 2, fresh digests: `P/testOnly A -- first-only`, `P/test A`, `P/test A` | 1 body (A#test1), then 3, then 0; A's XML has 1 case, then 3 |
| 3 | SBT 2, changed arguments: `P/test A -- changed-arg` twice | 3 bodies, then 0 |
| 4 | SBT 1 stock: `P/test`, then `P/testQuick` | 15 bodies, then 0: all five suites recorded |
| 4 | SBT 1 stock: `P/testOnly A -- first-only`, `P/testQuick A`, `P/testQuick A -- changed-arg` | 1, 0, 0 bodies: the partial run is cached as a success and changed arguments are ignored, reproduced on JS and Native |
| 4 | SBT 1 with the 0a policy: `P/testOnly A -- first-only`, `P/testQuick A`, `P/testQuick` | 1 body, then 3 (the full A suite, a 3-case XML), then 15 |
| 4 | SBT 1, `fail-b`: `P/test` (exits 1), then `P/testQuick` in a new invocation (exits 1) | 15 bodies, then 3 (B only); B fails again |
| 5 | every executed group above | exactly one `CHECK ... acquire=1 release=1`, with that group's body count |
| 5 | `P/testOnly * -- two-groups`; with `two-groups` set, `P/suiteResults`, then the same again with `parallelExecution := false` | 15 bodies, 2 groups, 2 launches; `CHECK` ABC=9 and DE=6, each 1/1; five XML files; A..E `Passed` |
| 6 | `abort-group`: `P/suiteResults`, `show P / Test / executeTests` | per command: 1 launch, 1 target deserialization, 1 `ABORT`, 0 bodies, 0 `CHECK` (no acquisition); A..E `Error` (errors=1); five XML files, each with 1 error case; `show` lists A..E |
| extra | `exit-group`: `P/suiteResults` | 1 launch, 1 deserialization, 1 `EXIT`, 0 bodies; five XML error cases. Native: A..E `Error` in `Tests.Output`, exit 0. JS: exit 1 and no `Output` (see below) |
| 3.9.0 | SBT 2, `set ThisBuild / scalaVersion := "3.9.0"`, then `P/testOnly A B`, `P/testFull`, `P/suiteResults` | 6 bodies with A and B XML files; 15 bodies with five XML files; A..E `Passed`. Run from the `scala-3.9.0` JS fast-link and Native LLVM-link outputs (built in run `20261001-192749`, reused here) |
| history | `-Dtransport.projection=aggregate`: `P/testOnly A B`, `P/suiteResults`; on SBT 2 with fresh digests, `P/testFull`, then `P/test` | 6 bodies but a single `TEST-transport.SuiteA.xml` with 6 cases (classnames A,A,A,B,B,B); 15 bodies and only `A Passed` in `Tests.Output` (an A XML with 15 cases); 15 bodies, then 12 (B..E, one B XML with 12 cases). This matches the earlier findings |

The SBT 1 policy is the 0a public-key expression, scoped to the project:

```scala
set P / Test / testQuick / testFilter := { val inherited = (P / Test / testQuick / testFilter).value; args => { val selected = Defaults.selectedFilter(args); Seq((name: String) => if (name.startsWith("transport.Suite")) selected.exists(_(name)) else inherited(args).exists(_(name))) } }
```

SBT 1 records per-suite history through its stock `TestStatusReporter`. It also
records a partial run as a success and ignores framework arguments. On JS and Native,
as on the JVM, the conservative policy stops a partial run from suppressing a complete
run, at the cost of never skipping a selected distage suite in `testQuick`.

## Concurrency and failure observations

The fixture keeps `Test / parallelExecution := true`. Across the 80 steps that
launched aggregates (92 launches):
- Every multi-suite step that ran in parallel had at least one `PROJECTION_WAIT` on
  another SBT thread: 60 of 60 steps, 214 waits in total. A five-suite group
  typically showed 4 waiters while the fifth task launched.
- The launching suite's events were always emitted on the launching thread.
- No step relaunched an aggregate: launches equalled groups, and target
  deserializations equalled launches.
- No invocation hung or timed out.
- In parallel `two-groups` steps, the two groups ran in two distinct target processes
  on five threads.
- With `parallelExecution := false`, both groups ran one after the other on a single
  thread, in the **same** target process (same pid), with 0 waits. This held for
  both the JS runtime and the native process. The adapters key runtimes by SBT
  thread, so sharing groups are not isolated by process.

How each forced failure was memoized:
- **`abort-group`.** The aggregate's `execute` threw
  `RPCException(IllegalStateException)`. The other four tasks were already waiting;
  they reported the same error without relaunching.
- **`exit-group` on Native.** After `Force close ... finished with non-zero value 3`,
  the pending call failed with `ClosedException(RunTerminatedException)`.
  `RunnerAdapter.done()` skips the closed worker, so `Tests.Output` holds five
  `Error` entries.
- **`exit-group` on JS.** The same `ClosedException` was memoized, and every suite
  reported its error to its listener group and XML file. Then `ProjectionRunner.done`
  called `RunnerAdapter.done` (`RunnerAdapter.scala:58`), which called `done` on the
  dead worker and threw `ClosedException`. `allTestGroupsTask` failed, so
  `executeTests` produced no `Output` and the command exited 1. This is the platform
  adapter's behavior, with or without the projection.

No failing case reported success.

## Exact commands

Run these from this directory in the authorized environment: Java 25.0.1, Node
22.21.1, SBT launcher 1.11.7, Linux x86_64, and pinned Nix clang/LLVM/lld 21.1.2.

```sh
export TRANSPORT_LLVM_PATH=/nix/store/sqlnjj8c3n3si3sjnadhdbcwgrk97g2w-clang-wrapper-21.1.2/bin:/nix/store/b5bmnvk17mq8qm5b8bpi9fkyr5g2d2m4-llvm-21.1.2/bin:/nix/store/nla41igcnyykzirqfs1fi167ssxhc37p-lld-21.1.2/bin
direnv exec /home/pavel/work/safe/7mind/izumi python3 verify.py             # all invocations
direnv exec /home/pavel/work/safe/7mind/izumi python3 verify.py sbt1-main   # invocations by name prefix
```

The driver puts `TRANSPORT_LLVM_PATH` in front of `PATH` for SBT, and runs the
invocations below in order from `fixture/`. Each line expands to:
- `sbt -batch -Dsbt.supershell=false -sbt-version <version> [property]`;
- then `'spikeCollect start'`;
- then each step's commands, each step followed by `'spikeCollect <step>'`. This
  marker is omitted after a final step that fails.

Within one invocation, `P` expands first to `js` and then to `native`. Names ending
in `-P` are two invocations, one per project. `<run>` is the run id.

```
sbt1-main                        1.13.0 exit 0  'P/testOnly transport.SuiteA transport.SuiteB' 'P/test' 'show P / Test / executeTests' 'P/suiteResults' 'P/testOnly * -- two-groups' 'set P / Test / testOptions += Tests.Argument("two-groups")' 'P/suiteResults' 'set P / Test / parallelExecution := false' 'P/suiteResults'
sbt1-fail-b                      1.13.0 exit 0  'set P / Test / testOptions += Tests.Argument("fail-b")' 'show P / Test / executeTests' 'P/suiteResults'
sbt1-abort                       1.13.0 exit 0  'set P / Test / testOptions += Tests.Argument("abort-group")' 'P/suiteResults' 'show P / Test / executeTests'
sbt1-aggregate                   1.13.0 exit 0  -Dtransport.projection=aggregate 'P/testOnly transport.SuiteA transport.SuiteB' 'P/suiteResults'
sbt1-exit-js                     1.13.0 exit 1  'set js / Test / testOptions += Tests.Argument("exit-group")' 'js/suiteResults'
sbt1-exit-native                 1.13.0 exit 0  'set native / Test / testOptions += Tests.Argument("exit-group")' 'native/suiteResults'
sbt1-quick                       1.13.0 exit 0  'P/test' 'P/testQuick' 'P/testOnly transport.SuiteA -- first-only' 'P/testQuick transport.SuiteA' 'P/testQuick transport.SuiteA -- changed-arg' '<policy above>' 'P/testOnly transport.SuiteA -- first-only' 'P/testQuick transport.SuiteA' 'P/testQuick'
sbt1-history-fail-full-P         1.13.0 exit 1  'set P / Test / testOptions += Tests.Argument("fail-b")' 'P/test'
sbt1-history-fail-incremental-P  1.13.0 exit 1  'set P / Test / testOptions += Tests.Argument("fail-b")' 'P/testQuick'
sbt2-main                        2.0.9  exit 0  as sbt1-main, with 'P/testFull' instead of 'P/test'
sbt2-fail-b                      2.0.9  exit 0  as sbt1-fail-b
sbt2-abort                       2.0.9  exit 0  as sbt1-abort
sbt2-aggregate                   2.0.9  exit 0  -Dtransport.projection=aggregate 'P/testOnly transport.SuiteA transport.SuiteB' 'P/suiteResults' 'set P / Test / testOptions += Tests.Argument("history-<run>-P-aggregate")' 'show P / Test / definedTestDigests' 'P/testFull' 'P/test'
sbt2-exit-js, sbt2-exit-native   2.0.9  exit 1, 0  as on SBT 1
sbt2-history-success             2.0.9  exit 0  'set P / Test / testOptions += Tests.Argument("history-<run>-P-full")' 'show P / Test / definedTestDigests' 'P/testFull' 'P/test' 'set P / Test / testOptions += Tests.Argument("history-<run>-P-partial")' 'show P / Test / definedTestDigests' 'P/testOnly transport.SuiteA -- first-only' 'P/test transport.SuiteA' 'P/test transport.SuiteA' 'P/test transport.SuiteA -- changed-arg' 'P/test transport.SuiteA -- changed-arg'
sbt2-history-fail-full-P         2.0.9  exit 1  'set P / Test / testOptions += Tests.Argument("fail-b")' 'set P / Test / testOptions += Tests.Argument("history-<run>-P-fail-b")' 'show P / Test / definedTestDigests' 'P/testFull'
sbt2-history-fail-incremental-P  2.0.9  exit 1  the same two settings and digest check, then 'P/test'
sbt2-scala390                    2.0.9  exit 0  'set ThisBuild / scalaVersion := "3.9.0"' 'P/testOnly transport.SuiteA transport.SuiteB' 'P/testFull' 'P/suiteResults'
```

The per-invocation JSON records the exact argument vector of every invocation.

## Residual risks and unverified behavior

- **Results are buffered, not streamed.** No event of any suite reaches SBT before
  its whole group finishes, and memory grows with the size of the group's events.
  Production needs per-suite completion signals from the target and bounded
  buffering.
- **A skipped suite looks like an empty one.** If the target sends no events for a
  selected suite, the projection reports an empty passing suite. The protocol needs
  explicit per-suite completion or manifest records. Not exercised.
- **Waiters hold SBT threads.** Up to group size − 1 SBT threads block per group. The
  projection cannot deadlock by itself: a task waits only for an aggregate that is
  already running on another thread and needs no SBT thread. Not measured: thread
  limits below the group size, custom `concurrentRestrictions` or tags, and many
  concurrent groups.
- **Cancellation was not tested.** This covers SBT cancel, Ctrl-C and timeouts.
  Waiters use the interruptible `Object.wait`; the launching task blocks inside the
  adapter's RPC await. Cleanup of target processes and runtimes after cancellation
  is unknown.
- **Groups are not process-isolated.** The serial run shared one target process
  between groups. After a target dies, the adapters keep the dead runtime cached for
  that thread, so a later group whose first task lands on the same thread would fail
  without launching. Not exercised.
- **JS loses the `Output` map after a target death.** When a worker has died,
  `RunnerAdapter.done()` throws and the `Tests.Output` map is lost. Listener groups
  and XML files still carry the per-suite errors, and the command fails.
- **Target logs are not routed per suite.** Target logs, such as the `CHECK` line, go
  to the launching suite's loggers.
- **Two failure paths are implemented but unexercised.** The stub never sends
  unattributable events (non-nested selectors or foreign suite ids), or events after
  the aggregate returned.
- **Suite-level errors render oddly in XML.** They use `SuiteSelector`, which SBT's
  JUnit listener renders as the test case name
  `(It is not a test it is a sbt.testing.SuiteSelector)`. How IDE and rerun tools
  handle this is unverified.
- **Sharing groups come from a fixture argument that both sides compute.** Production
  must derive groups from the DI plan without acquiring resources, and host and
  target must agree. The wrapper fails unless each `tasks` call returns exactly one
  aggregate.
- **SBT 2 history is shared across checkouts.** It is content-addressed in the global
  cache, so checkouts with equal digests share records. As 0a showed,
  distage-only inputs are not part of stock digests; this was not re-tested here.
- **Not verified:**
  - Scala 2.12 and 2.13 fixture builds;
  - browser JS environments, Windows and macOS;
  - several frameworks in one project;
  - aggregates that return nested tasks;
  - multi-project builds and custom configurations;
  - the SBT server, BSP and IntelliJ;
  - Native framing for a plugin-owned launcher. The adapters' socket RPC is the
    chosen transport here;
  - real distage provisioning, which belongs to the 0b engine closure.

## History: aggregate-only transport (previous status: partial)

These measurements predate the host projection. To reproduce them with the current
fixture, add `-Dtransport.projection=aggregate` to the SBT command line. The driver's
`sbt1-aggregate` and `sbt2-aggregate` invocations recheck the key ones. The SBT 2
history commands below also need fresh suite digests. Per-suite runs now record B..E
under the stock digests in SBT 2's global cache, so a later aggregate-mode `test`
can skip them. `sbt2-aggregate` therefore adds a nonce argument first. Apart from
that, the commands behave as before; `StubApplication` now takes an explicit
tests-per-suite count. The status at the time read:

> **Gate 0c is partial.** Whole-run execution, selection, target task serialization,
> resource lifetime, and asynchronous JS completion work on the real platforms.
> The aggregate framework preserves nested testcase identities but does **not**
> produce ordinary per-suite SBT result groups. Plugin-owned per-suite projection
> still needs an executed acceptance test.

The ignored local `evidence/summary.json` records checks for both controlled
failure runs and the single public-channel launch. Captured evidence paths below
refer to local outputs, not versioned files or fixture dependencies.

### Fixture and observed matrix

[Runnable fixture](fixture/build.sbt) has five discovered suite classes, three
bodies each, and a stub application whose public report exposes body/acquisition/
release counts. Selection reaches the target before acquisition. One aggregate
application task executes each explicit sharing group. `two-groups` chooses
ABC and DE; `fail-b` makes B#test1 fail persistently without changing selection.
There are no live resource objects in the serialized payload.

Final suite declarations are in separate source files with distinct marker1..5
implementations to make the stock host digests distinguishable.

Fixture checks: Behavioral/Active/Blackbox/Group for stub application accounting;
Effectual/Good-Communication for controlled SBT/Node/native processes and reports.
Origin: specified transport invariants. The deliberate failure is a transport
probe, not a defect correction or a production assertion implementation.

| SBT | Scala | Platform | Actual executed checks |
| --- | --- | --- | --- |
| 1.13.0 | 3.3.7 | JS 1.22.0 | `testOnly A B`:6; `test`:15; standalone linked app:6 |
| 1.13.0 | 3.3.7 | Native 0.5.12 | `testOnly A B`:6; `test`:15; standalone native app:6 |
| 2.0.9 | 3.3.7 | JS 1.22.0 | `testOnly A B`:6; `testFull`:15; two groups:9+6; standalone app:6 |
| 2.0.9 | 3.3.7 | Native 0.5.12 | `testOnly A B`:6; `testFull`:15; two groups:9+6; standalone app:6 |
| 2.0.9 | 3.9.0 | JS 1.22.0 | actual compile/fast-link/`testOnly A B`:6 |
| 2.0.9 | 3.9.0 | Native 0.5.12 | actual compile/LLVM-link/`testOnly A B`:6 |

Each row's executed sharing group acquired once and released once, before host
completion. Stock console counts matched emitted bodies. `SERIALIZE` and
`DESERIALIZE` traces show both platform adapters transported the complete selected
suite payload. JS used the async `Task.execute` continuation after a 20ms timer;
`JS_SCHEDULE`, `JS_CALLBACK`, bodies, finalization, and SBT success occurred in
that order. No blocking wait was used in the JS target.

SBT 2 `test` was also invoked after prior successes and had an empty cached
selection. Those original five empty suites had identical stock digests, so this
does not establish independent per-suite success history or DI-aware invalidation.
`testFull` provides the complete-run check; `test` is incremental.

Two explicit groups ran in separate actual target processes: JS PIDs 35431/35450,
Native PIDs 35483/35497. Each logged acquisition/release 1 and 9 or 6 bodies. This
proves ownership **within each task**, not live-resource sharing across workers.

### Aggregate framework constraints reproduced

1. An aggregate task with an undiscovered synthetic `TaskDef` name
   `transport.Application` was serialized but never executed by SBT 2. `testOnly
   A B` succeeded with `Passed: Total 0` and `No tests to run`. The target-side
   implementation is insufficient to introduce a new host task identity.
   Captured failure (`evidence/transport-sbt2-js.txt`).
2. Retaining the selected representative `TaskDef` (lexically first suite) made
   execution work. Events use that task's fully qualified name and
   `NestedTestSelector(actualSuiteId, actualTestId)`. Stock JUnit testcase
   classname/name pairs are correct, including B#test1, but the report file and
   `<testsuite>` name are representative A. A full 15-body run becomes one
   `TEST-transport.SuiteA.xml`; two groups become A and D reports.
3. The initial empty suite classes had identical `definedTestDigests`. Different
   marker constants and then different marker method names in the same source
   still produced equal digests. Separating the suite classes into five source
   files achieved five **observed distinct** stock digests. No digest salt or
   policy override was used.
   Original digest check (`evidence/transport-sbt2-digests-original.txt`),
   same-file marker constants (`evidence/transport-sbt2-distinct-failure1.txt`),
   same-file unique methods (`evidence/transport-sbt2-unique-failure1.txt`).
4. With five distinct stock digests verified before each command, two identical
   unfiltered SBT 2 `test` invocations using the same persistent `fail-b` argument
   each reran all 15 bodies and failed exactly B#test1 (14 successes/1 failure).
   The proposed hypothesis that B would be marked successful and skipped was
   **not reproduced in this controlled failure probe**. Console output named
   failed suite A. XML identified classname B, name test1. This does not prove
   correct independent success caching for the nonrepresentative suites; the
   success-history probe below shows it is incomplete.
   First controlled run (`evidence/transport-sbt2-separated-failure1.txt`),
   second controlled run (`evidence/transport-sbt2-separated-failure2.txt`),
   first controlled XML (`evidence/separated-failure1.xml`),
   second controlled XML (`evidence/separated-failure2.xml`).
5. With the same five distinct stock digests and **no framework arguments**,
   `js/testFull` executed A..E (15 bodies) and passed; the immediately following `js/test`
   selected B..E and executed 12 again. This directly demonstrates successful
   aggregate execution cached only representative A. The earlier all five skip
   was not evidence of correct independent suite history: their digests aliased.
   Controlled success-history run (`evidence/transport-sbt2-separated-success-history.txt`),
   A report after full (`evidence/success-history-A.xml`),
   B report after incremental (`evidence/success-history-B.xml`).
6. `show js / Test / executeTests` confirmed the material projection defect:
   `Output(Failed,Map(transport.SuiteA -> sbt.SuiteResult...),...)`. Only A appears
   in the result map, although A..E ran and B contains the failure.
   Host result (`evidence/transport-sbt2-host-result.txt`).

This mapping establishes counts and nested identities, not ordinary per-suite
listeners/results/history. Do not mark the ordinary reporting part of gate 0c
complete based on testcase classname alone.

### Public standalone launch comparison

Separate JS and Native app projects execute the same stub application without a
framework adapter. Scala.js uses public main-module initializer settings and
`run`; Native uses a separately linked executable and public `run` with explicit
suite arguments. Both platforms and both SBT versions launched six bodies with
acquire/release 1. These runs establish actual target launch, not an SBT test task
binding or host listener projection.

The additional `launchJsCom` fixture task directly calls the public
`JSEnv.startWithCom` API against a linked `transport.ComMain`. The host sends
an explicit two-suite selection; the target emits six EVENT messages and one
END message after release. Ordinary body output is sent independently to stdout.
The host checks exactly seven channel messages, acknowledges QUIT, and waits for
successful process exit. The task opts out of SBT 2 state caching with
`@transient` and out of multi-project task aggregation explicitly. This is a
Node-specific termination demonstration;
browser lifecycle behavior and a full protocol schema are outside this spike.
Channel evidence (`evidence/transport-sbt2-js-com.txt`).

Native public launch is demonstrated, but a separate protocol channel for native
user output has not been implemented or verified here. The fixture's stdout
APP_EVENT markers do not claim to provide production framing.

### Toolchain and reproduced setup failures

Both pinned plugins resolve on SBT 1 and SBT 2, including `_sbt2_3` artifacts.
Native 0.5.12 remains the latest stable published SBT 2 plugin in the inspected
[Maven metadata](https://repo.maven.apache.org/maven2/org/scala-native/sbt-scala-native_sbt2_3/maven-metadata.xml).
Its Scala 3.9.0 compiler plugin is also published; the actual execution above
confirms usability beyond metadata.
Retained compiler metadata (`evidence/native390-metadata.xml`).

The initial fixture incorrectly requested `scalajs-test-interface_sjs1_3` and
relied on an implicit `%%%` operator under SBT 2. Both failed at the expected
build/dependency step; platform plugins already supply their test interface, so
redundant declarations were removed. These are fixture errors, not unavailable
platform plugin artifacts.

Naming the Native fixture project `native` caused its native-test executable path
to collide with compiler workDir after successful linking. Native explicitly
reported DirectoryNotEmptyException and requested a different executable name.
The correction uses public `Test / nativeConfig.withBaseName("transport-stub")`.
Before correction (`evidence/transport-sbt1-targets.txt`),
successful Native/app runs (`evidence/transport-sbt1-native-apps.txt`).
The initial public-channel task lacked `@transient`; SBT 2 rejected a required
HashWriter for JSEnv/input state. Marking the task transient corrected that
reproduced build error. Its initial default aggregation launched once per fixture
project; `launchJsCom / aggregate := false` now produces exactly one launch. A
ComMain source initially placed alongside shared JS platform sources failed
ordinary js/Compile because StubApplication belongs to Test there; moving it to
a dedicated launcher source directory corrected the boundary.

Nix `_FORTIFY_SOURCE` and executable-stack linker warnings did not prevent the
observed successful builds; compiler diagnostics were logged with `[error]` even
when they were warnings. No host system settings were modified.

### Exact commands

Run from `fixture/`. Environment used the already-authorized main checkout's
Nix shell; SBT launcher 1.11.7 bootstrapped the explicitly requested SBT version.
Java 25.0.1, Node 22.21.1; Linux x86_64; pinned Nix clang/LLVM/lld 21.1.2.

```sh
export TRANSPORT_LLVM_PATH=/nix/store/sqlnjj8c3n3si3sjnadhdbcwgrk97g2w-clang-wrapper-21.1.2/bin:/nix/store/b5bmnvk17mq8qm5b8bpi9fkyr5g2d2m4-llvm-21.1.2/bin:/nix/store/nla41igcnyykzirqfs1fi167ssxhc37p-lld-21.1.2/bin
# Prefix that path inside direnv's child shell for each Native command.
direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'export PATH="$TRANSPORT_LLVM_PATH:$PATH"; sbt -batch -Dsbt.supershell=false -sbt-version 1.13.0 "js/testOnly transport.SuiteA transport.SuiteB" "js/test" "native/testOnly transport.SuiteA transport.SuiteB" "native/test" "jsApp/run" "nativeApp/run transport.SuiteA transport.SuiteB"'
direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'export PATH="$TRANSPORT_LLVM_PATH:$PATH"; sbt -batch -Dsbt.supershell=false -sbt-version 2.0.9 "js/testOnly transport.SuiteA transport.SuiteB" "js/test" "js/testFull" "native/testOnly transport.SuiteA transport.SuiteB" "native/test" "native/testFull" "js/testOnly * -- two-groups" "native/testOnly * -- two-groups" "jsApp/run" "nativeApp/run transport.SuiteA transport.SuiteB"'
direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'export PATH="$TRANSPORT_LLVM_PATH:$PATH"; sbt -batch -Dsbt.supershell=false -sbt-version 2.0.9 "set ThisBuild / scalaVersion := \"3.9.0\"" "js/testOnly transport.SuiteA transport.SuiteB" "native/testOnly transport.SuiteA transport.SuiteB"'
# Final suites have five distinct stock digests. First verify successful history.
direnv exec /home/pavel/work/safe/7mind/izumi sbt -batch -Dsbt.supershell=false -sbt-version 2.0.9 'show js / Test / definedTestDigests' 'js/testFull' 'js/test'
# Execute this identical failure command twice; each exits 1.
direnv exec /home/pavel/work/safe/7mind/izumi sbt -batch -Dsbt.supershell=false -sbt-version 2.0.9 'set js / Test / testOptions += Tests.Argument("fail-b")' 'show js / Test / definedTestDigests' 'js/test'
direnv exec /home/pavel/work/safe/7mind/izumi sbt -batch -Dsbt.supershell=false -sbt-version 2.0.9 'set js / Test / testOptions += Tests.Argument("fail-b")' 'show js / Test / executeTests'
direnv exec /home/pavel/work/safe/7mind/izumi sbt -batch -Dsbt.supershell=false -sbt-version 2.0.9 launchJsCom
```

Ignored local evidence .txt files remove ANSI colors and repetitive C-header warning lines;
commands, distinct failures, transport/body/counter traces, and terminal results
are preserved locally. Original unfiltered logs remain under the
task-specific `/srv/nvme/tmp/izumi-native-testkit-spikes-20261001` scratch area.
Generated build outputs can be recreated from the retained fixture.
XML snapshots omit JVM system properties; testcase and failure data are retained.

### Plan amendments and remaining checks

- Gate 0c must explicitly require per-suite `Tests.Output`, listener group names,
  XML grouping, and incremental history, including nested failure and success
  probes with verified distinct suite digests. The distinct-digest successful
  aggregate actually cached only its representative; stock test reran B..E (12 bodies). Same-file stub suites can alias
  stock digests; marker constants alone did not eliminate that aliasing here.
- Treat aggregate target Framework execution as a working transport building
  block requiring plugin-owned host projection. An undiscovered aggregate task
  name is not accepted by stock SBT; do not silently change discovery identities.
- Keep sharing groups in one task/process. Explicit shards demonstrably create
  independent acquisitions on the actual targets.
- A public JSEnv channel and separately linked Native app are viable launch
  paths. Selecting between them and the platform adapters still requires the
  host projection implementation and Native framing acceptance test.
- Preserve observed Scala 3.3.7 and 3.9.0 results separately. This spike did not
  verify Scala 2.12/2.13 source compilation, literal 3.3.0, browser JS, Windows,
  cancellation/finalizer failures, arbitrary test selectors, concurrent sessions,
  real distage provisioning, or Native port dependencies. The portability spike
  owns actual izumi closure feasibility.
- Source-span macro probing remains step 1c acceptance; it was deferred following
  the prioritized failure accounting and explicit Scala 3.9 transport steering.
