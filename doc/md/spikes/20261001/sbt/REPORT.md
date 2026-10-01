# Gate 0a: JVM SBT application/plugin spike

Executed 2026-10-01 in isolated worktree based on
`4feabed5857067d051e357bc1aa62bbb68c96892`. This is a stub application host-contract
spike, not a production distage runner and not a reproduction of issue #2361.

## Result

The `loadedTestFrameworks` substitution is a viable **non-forked JVM seam** on
SBT 1.13.0 and 2.0.9. It preserves SBT's ordinary discovery, selection, scheduling,
framework argument parsing, listeners, incremental records, and per-suite JUnit
reporting. No SBT private API was called. Both versions give the selected set to
one `Runner.tasks` call in the exercised in-process group. The proxy retains an
ordinary task for every selected suite, starts the application once when the first
task executes, and buffers its results for each suite's original task handler.
It never waits for the other suite tasks to enter `execute`, and never emits to a
handler after that task returns.

Forks instantiate the class named by `testFrameworks`, bypassing the substituted
host framework. The exercised fork runner also receives the selected set in one
`Runner.tasks` call. A target-side registered `ApplicationFramework` supplies the
same whole-selected-set launch and per-suite result projection. One default fork
group executes A+B in one application; two configured fork groups execute A+B and
C+D+E in two applications. Resource acquisition/release is once per group. This
proves a concrete fork bootstrap without copying SBT orchestration. It does not
make a whole-set call a portable test-interface guarantee for other hosts.

Stock incremental invalidation is insufficient in two independently reproduced
cases: an external configuration file and a compiled implementation accessed only
by `Class.forName` string. Both versions compile the changed implementation but
execute no selected test bodies afterward. SBT 1 additionally records a partial
suite run as full success, and ignores changed framework arguments in `testQuick`.
The spike executes public-key corrective policies: conservative distage reruns
under SBT 1, and extra class/resource digests under SBT 2. The former deliberately
sacrifices incremental no-ops for distage suites; foreign-framework cache behavior
is retained. The latter conservatively invalidates foreign suites too because
`extraTestDigests` is global to the configuration.

## Fixture and execution environment

- Linux x86_64, OpenJDK 25.0.1+8 (Nix build).
- Launcher executable from installed SBT 1.11.7 package; actual SBT versions are
  selected by each fixture's `project/build.properties`: 1.13.0 and 2.0.9.
- Fixture Scala version 2.13.18; all application/spec sources are Java, compiled
  by SBT. Plugin build uses SBT 1's Scala 2.12.21 / SBT 2's Scala 3.8.4.
- `org.scala-sbt:test-interface:1.0`; no ScalaTest or distage dependency.
- `fixture/` and `fixture-sbt2/` contain portable sources/builds and a local
  external configuration input `di-only.txt`.
- Five discovered `Spec` subclasses have three reflected body invocations each.
  An independently fingerprinted `ForeignSuite` has three bodies and uses its
  original framework. The independent `target/audit.log` ledger records actual
  Java body entry, launch selection/arguments, and resource acquisition/release.
- `run_cases.py` independently compares body identity sets/multiplicities and
  freshly generated SBT JUnit XML names/counts/case selectors. It deletes only
  the fixture's previous ledger and `TEST-*.xml` reports before each check.
  Ignored local `evidence/*-fork-partial-final.xml` snapshots are actual SBT XML
  with JVM properties removed. Ignored local `logs/` files contain parsed JSON
  checks, audit ledgers, and host output. These captures are not versioned;
  the driver creates its log directory and restores changed fixture inputs.
  `session clear` makes SBT 2 server state explicit between cases.

Exact complete rerun from this directory, with the configured environment:

```sh
direnv exec /home/pavel/work/safe/7mind/izumi python verify.py
```

Portable equivalent: put Python 3, Java and the `sbt` launcher on PATH and run
`python verify.py`. Builds resolve their declared SBT, Scala and test-interface
artifacts; first execution requires dependency downloads.

The driver executes, sequentially for versions 1 and 2:

```sh
python run_cases.py 1
python extra-cases.py 1
python fork-cases.py 1
python dynamic-cases.py 1
python run_cases.py 2
python extra-cases.py 2
python fork-cases.py 2
python dynamic-cases.py 2
```

Every build command is executed as `sbt -batch -Dsbt.supershell=false '<SBT
commands>'`; the exact argument vector is written to that case's local JSON. Multiple
commands are passed as one semicolon-delimited SBT string. With the installed
launcher, SBT 2 joined separate command arguments without delimiters and rejected
the joined input; the initial failure and corrected invocation are retained.

## Executed matrix

`A`–`E` below mean `spike.SuiteA`–`spike.SuiteE`. Counts refer to actual distage
stub bodies; foreign bodies are listed separately.

| Check / exact SBT selection | SBT 1.13.0 | SBT 2.0.9 | Evidence suffix |
| --- | --- | --- | --- |
| `testOnly spike.SuiteA spike.SuiteB`, twice in one command session | 12 bodies, 2 applications; A/B XML 3 each | Same | `explicit-repeat` |
| `testOnly spike.Suite* -spike.SuiteC` | 12 bodies, 1 app; A/B/D/E XML 3 each | Same | `exclusion` |
| `testOnly spike.Suite*` | 15 bodies, 1 app; five XML suites 3 each | Same | `wildcard` |
| `test` (1) / `testFull` (2) | 15 bodies, 1 app; foreign 3 exactly once | Same | `full` |
| Subsequent `testQuick` (1) / `test` (2) | 0 bodies / 0 apps | Same | `incremental-repeat` |
| Modify SuiteA class, then `testOnly spike.SuiteA -- --one` | 1 body; A XML 1 | Same | `partial` |
| Next `testQuick spike.SuiteA` (1) / `test spike.SuiteA` (2) | **0: unsafe partial success cache reproduced** | 3: unfiltered cache remains distinct | `after-partial` |
| `testOnly spike.SuiteA -- --different` | 3 bodies; supplied argument visible in ledger | Same | `changed-arguments` |
| Change external `di-only.txt`, then `testQuick` / `test` | **0: omitted input reproduced** | Same | `di-stock` |
| `testQuick spike.SuiteA -- --new-arg` / `test spike.SuiteA -- --new-arg` | **0: arguments ignored** | 3, then repeated identical request 0 | `arguments-stock` / `arguments-new`, `arguments-same` |
| Conservative SBT 1 quick policy, after configuration change | 15 bodies; foreign cached suite remains skipped | N/A | `di-conservative` |
| Conservative SBT 1 policy: partial A, then full quick A | 1 + 3 bodies; final A XML 3 | N/A | `partial-conservative` |
| Public SBT 2 extra file digest: first / identical / changed file | N/A | 15 / 0 / 15 bodies; foreign 3 / 0 / 3 | `di-extra-initial`, `di-extra-cached`, `di-extra-changed` |
| Modify reflected `WiringOnly` implementation, then incremental command | **0 despite compiling modified Java implementation** | Same | `dynamic-stock` |
| Conservative SBT 1 quick policy after reflected implementation edit | 15 bodies, observes implementation-two | N/A | `dynamic-conservative` |
| SBT 2 class+file digest: first / identical / implementation edit | N/A | 15 / 0 / 15 bodies, observes implementation-three | `dynamic-digest-initial`, `dynamic-digest-cached`, `dynamic-digest-changed` |
| Modified SuiteA, target bootstrap fork: partial A then full incremental A | Conservative policy executes 1 + 3 bodies | Stock argument-sensitive history executes 1 + 3 bodies | `fork-partial-safe` |
| Stock fork with only host substitution, select A+B | 6 bodies, **2 target application launches**, no host proxy | Same | `fork` |
| Register target bootstrap, fork, select A+B | 6 bodies, 1 target application / acquire / release; A/B XML 3 each | Same | `fork-whole` |
| Two explicit fork groups A+B / C+D+E | 15 bodies, 2 target applications / acquires / releases; five XML suites 3 each | Same | `fork-groups` |

Each evidence suffix denotes the ignored local output
`logs/sbt{1,2}-<suffix>.{log,audit,json}`.
`logs/verify.log` records the complete initial 40-case driver outcome. The added
fork partial-selection checks are captured in
`logs/fork-partial-final-verification.log`; subsequent `verify.py` runs include
those checks too. The source edits and exact
public-key policy expressions are executable in the scripts.

Sequential scheduling is enforced by `Test / parallelExecution := false`.
For A+B, `TASK_ENTER A`, application execution/release, `TASK_EXIT A`, then
`TASK_ENTER B` is the observed order. The public `show Test / definedTestDigests` observation confirms five distinct
SHA256 values for A–E (`logs/sbt2-distinct-digests.log`), so the JVM history
checks do not depend on equal empty-suite digests. Both the non-forked and forked adapters
report all events inside each task's execution lifetime. Bodies use the same
`test1`–`test3` display names across suites; XML retains their owning suite names.

## Public policies and precise scope

SBT 1's correction composes its public `Defaults.selectedFilter` with the inherited
`testQuick / testFilter`: selected distage names always run; other names keep
stock success filtering. This honors explicit selection and exclusions. It does
not suppress stock partial-success recording; rather, it stops trusting that
record for distage suites. A future efficient policy must either suppress partial
success through a supported listener binding, or own a complete-request history.
That more efficient policy has not been implemented by this spike.

SBT 2's `extraTestDigests` task is explicitly `Def.uncached`, depends on test
compilation, and hashes the reflected implementation class bytes plus external
configuration bytes. The prototype does not derive those inputs from an actual
scanned distage plugin graph. Production must compute the complete relevant
classes/resources/configuration/options/activation-property/environment closure
or deliberately use a conservative full invalidation policy. A static dependency
on `Application` exists; there is no static class reference to `WiringOnly`, whose
lookup is exclusively a string. This approximates dynamically wired dependencies
and is distinct from the separate external file reproduction.

A repeated harness run initially reused a valid SBT 2 class+file digest from an
earlier run; unchanged suites correctly stayed cached, violating the harness's
assumption that introducing the same policy would always rerun all suites. That
failed expectation is preserved in
`logs/verify-initial-cached-digest-failure.log`. The harness now uses an explicit
fresh external configuration input before its initial digest check. Clearing
SBT session settings does not clear persisted action successes.

The SBT 2 host framework substitution and explicit `testGrouping` override both
require `Def.uncached`; initial JsonFormat compile rejections are retained as
`sbt2-initial.log` and `sbt2-fork-groups-initial-failed.log`, respectively. This is
an SBT 2 task-definition constraint, not a defect in test selection.

## Observed source behavior versus remaining work

The runtime evidence agrees with the inspected SBT
[TestFramework 1.13.0](https://github.com/sbt/sbt/blob/v1.13.0/testing/src/main/scala/sbt/TestFramework.scala),
[TestFramework 2.0.9](https://github.com/sbt/sbt/blob/v2.0.9/testing/src/main/scala/sbt/TestFramework.scala),
and [SBT 2 incremental implementation](https://github.com/sbt/sbt/blob/v2.0.9/main/src/main/scala/sbt/internal/IncrementalTest.scala):
ordinary task/result lifetimes matter, and the SBT 2 stock input path records
framework arguments. These were originally inspected as downloaded source files
under `/srv/nvme/tmp/izumi-testkit-research-20261001`; the executable matrix is
separate evidence rather than an inference from those files.

Not checked: genuine distage planning/memoization, multiple SBT projects or custom
configurations, setup/cleanup hooks, cancellation/worker loss, finalization failure
and cache behavior, concurrent producer events inside one suite, classloader leak
or loader lifetime, or standalone/IDE hosts. The application is synchronous and
buffers its selected-set results before per-suite host projection; streaming and
bounded buffering remain production requirements. JVM test compilation here does
not establish Scala 3.9 macro/library compatibility or JS/Native portability.

Gate 0a establishes viable JVM seams and the exercised host/invalidation contracts.
It is not evidence that the later production acceptance gate 2d is complete.
