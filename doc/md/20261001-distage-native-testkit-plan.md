# Native distage test library and runner

Research and proposed implementation plan, 2026-10-01. Repository inspected at
`5be95fb6309c4b0cb9dbea37ed84c0db0a8ff870`. “Native distage runner” means a
distage-owned runner; Scala Native is a separate required execution backend.

The recommended direction is an independent assertion library and an explicit
test application built on the existing distage planning/execution engine. The
application owns discovery, selection, resource lifetime, and execution. An SBT
plugin preserves the standard `test`, `testOnly`, and `testQuick` commands while
launching that application; IntelliJ uses the same execution contract. JSON is a
boundary format; the in-process engine uses typed models.

This document records source inspection and research, not a completed runtime
reproduction or implementation. In particular, issue #2361 has not been rerun in
this investigation. Proposed interfaces and commands below do not exist yet.

## Evidence and implications

The [issue report](https://github.com/7mind/izumi/issues/2361) records lost tests
when SBT 2 selects several explicit suite names. Its explanation is that a
ScalaTest filter for one suite is applied to the combined distage test set.
The current checkout has a different registry implementation from the released
version cited in parts of the report, but retains that architectural pattern:

- [DistageTestsRegistrySingleton](../../distage/distage-testkit-scalatest/src/main/scala/izumi/distage/testkit/services/scalatest/dstest/DistageTestsRegistrySingleton.scala)
  selects one runner and collects tests from suite instances, using ScalaTest's
  `Runner.discoveredSuites` when available.
- [DistageScalatestTestSuiteRunner](../../distage/distage-testkit-scalatest/src/main/scala/org/scalatest/distage/DistageScalatestTestSuiteRunner.scala)
  applies the initiating suite's `Args.filter` to the collected tests. Its JS
  mode disables global memoization by default and documents a sequential SBT
  execution requirement for the override.
- [DistageTestRunner](../../distage/distage-testkit-core/src/main/scala/izumi/distage/testkit/runner/impl/DistageTestRunner.scala),
  [TestPlanner](../../distage/distage-testkit-core/src/main/scala/izumi/distage/testkit/runner/impl/TestPlanner.scala),
  and [TestReporter](../../distage/distage-testkit-core/src/main/scala/izumi/distage/testkit/runner/api/TestReporter.scala)
  already provide substantial independent machinery. Preserve the existing
  planning, environment merging, memoization trees, and effect execution.
- [ScalatestAbstractDistageSpec](../../distage/distage-testkit-scalatest/src/main/scala/izumi/distage/testkit/services/scalatest/dstest/ScalatestAbstractDistageSpec.scala)
  still depends on ScalaTest verbs, assertions, cancellation, and IDE finders.
  Replacing the adapter also requires a native specification front end.
- [WithTestRegistration](../../distage/distage-testkit-scalatest/src/main/scala/izumi/distage/testkit/services/scalatest/dstest/WithTestRegistration.scala)
  already collects tests per instance, but gives them identity-hash-based UIDs.
  Those UIDs are unsuitable as persistent selection identifiers.
- [DistageTestEnv](../../distage/distage-testkit-core/src/main/scala/izumi/distage/testkit/spec/DistageTestEnv.scala)
  contains global environment and default-module caches. Explicit session
  ownership must address these caches as well as the ScalaTest registry.

The [build definition](../../sbtgen/Deps.scala) targets JVM and JS, with Scala
2.12.21, 2.13.18, and 3.7.4. It does not define a Native target. The build uses SBT
2.0.9 and Scala.js 1.22.0. Scala Native support and Scala 3.3 consumer compatibility
therefore need dedicated work; neither follows from the present build matrix.

## Assertion contract

The agreed result/error policy is:

| Entry point | Result | Failure |
| --- | --- | --- |
| `assert(condition)` | `Unit` | Throws the library's assertion failure |
| `assert1[F](condition)` | `F[Unit]` | Raised inside the effect's suspended computation |
| `assert2[F](condition)` | `F[Nothing, Unit]` | An effect defect, not a typed error |

Use a portable `AssertionError` subtype carrying structured diagnostics, with a
useful message even when consumed by an unrelated runner.

`F` requires evidence for suspension and failure handling; higher-kinded arity
alone cannot supply those capabilities. For BIO, preserve the existing
`IO2.sync` defect behavior. For cats-effect, `Sync.delay` supplies the unary
behavior. Keep the plain assertion module independent of both runtimes. If the
public API needs a shared suspension capability, make it a small, explicitly
lawful boundary with adapters to the existing typeclasses. Do not expose
`QuasiIO` as a suspension guarantee: its Identity instance intentionally does
not suspend.

The first release takes ordinary Boolean expressions. Existing conveniences
such as `assertIO(effect)(predicate)` can be migrated with adapters or added
after the three requested forms are stable. They are not prerequisites for
effectful assertions.

### Diagnostics

Use two compiler implementations—Scala 2 blackbox macros and Scala 3
quotes/reflection—feeding one portable diagnostic model and renderer. Share
runtime representation and behavioral fixtures, rather than attempting to
abstract over the two compiler AST APIs. Macros execute in the JVM compiler for
all three backends; their emitted code and diagnostic runtime must be portable.
Separate macro definition compilation from consumer compilation where required,
especially on Scala 2.

Capture a source identity, expression span, the original expression text, and
observations associated with subexpression spans. Source identity should include
a normalized path relative to a supplied source root when available. Retain a
documented representation for absolute and virtual-source paths. Specify offset,
line, and column conventions; render tabs and Unicode separately from compiler
offsets.

Store the expression text at compilation time. A file path alone fails in CI,
remote runs, packaged tests, and browser JS. A source provider can enrich failures
with surrounding lines when the runner can access matching sources. Validate
available source content against the recorded span/content before placing
pointers; when it differs, display the compiled excerpt and identify the
mismatch. Missing source/range information must be represented explicitly.

Scala 3.3.0 already has `Position.sourceCode`, offsets, and line/column APIs in
[Quotes.scala](https://github.com/scala/scala3/blob/3.3.0/library/src/scala/quoted/Quotes.scala).
For Scala 2, the spike must establish the available ranges with and without
`-Yrangepos`; do not assume every typed tree retains a useful range. The current
[SourceFilePosition](../../fundamentals/fundamentals-language/src/main/scala/izumi/fundamentals/platform/language/SourceFilePosition.scala)
contains only a file name and line. Introduce a richer assertion-specific type
without forcing an unrelated repository-wide position migration.

Illustrative output:

```text
UserSpec.scala:42:10
assert(user.age >= minimum && user.enabled)
       ^^^^^^^^^^^^^^^^^^^
       user.age = 17; minimum = 18; result = false
                             user.enabled: not evaluated
```

The macro still needs expression structure to associate values with spans. The
improvement is preserving actual source and structured observations instead of
using a compiler pretty-print as the primary diagnostic.

### Semantic requirements

Start with Boolean leaves, built-in `&&`, `||`, `!`, and a conservative set of
comparison forms. Preserve the resolved operator and operand types. An operator
spelling alone is insufficient to justify rewriting a user-defined method.
Expressions outside the recognized set remain valid assertions, with an opaque
expression observation and source location.

The implementation must preserve evaluation count, order, short-circuiting,
by-name behavior, and thrown exceptions. Capturing the operands once but invoking
an overloaded comparison twice is still a semantic regression. Do not implement
`&&` by evaluating both branches to improve the message. Do not silently change
array equality to deep equality or convert unrelated exceptions into false
predicates.

For effectful forms, expression evaluation and per-execution diagnostic state
belong inside suspension. Constructing the effect must not evaluate the
condition; executing it twice must check twice, with independent observations.
`assert2` must leave the typed error channel unchanged. Failure rendering should
be bounded and lazy; a failed value renderer must preserve the original failure
and expose the rendering error.

[uTest](https://github.com/com-lihaoyi/utest) demonstrates universal assertions
across all requested language/platform families. Its
[Scala 3 tracer](https://github.com/com-lihaoyi/utest/blob/master/utest/src-3/utest/asserts/Tracer.scala)
and [ZIO smart assertions](https://zio.dev/reference/test/assertions/smart-assertions/)
are useful design references. Neither should be adopted without checking its
evaluation and failure contract against ours. Owning a small implementation is
reasonable; cloning an entire assertion framework is unnecessary.

## Test application and execution ownership

The intended dependency boundaries are an assertions module with optional effect
adapters; the existing testkit core with native specifications and session
ownership; a portable application/protocol layer; and host-specific SBT/IDE
adapters. Share protocol data independently of SBT's classloader and Scala
version. Keep ScalaTest compatibility in its own artifact. These boundaries need
not become a separate published artifact for every interface.

```mermaid
flowchart LR
  SBT["SBT test / testOnly / testQuick"] --> Plugin["SBT plugin"]
  Plugin --> App["Distage test application"]
  CLI["Standalone launcher"] --> App
  IDE["IntelliJ adapter"] --> App
  App --> Engine["Existing planner and execution engine"]
  Engine --> Events["Correlated events and terminal results"]
  Events --> Plugin
  Events --> IDE
  Events --> Reports["Console / JUnit XML / JSON"]
```

Expose four logical operations, with platform launchers around the same core:

```text
discover(catalogue)                 -> test and suite descriptors
resolve(descriptors, request)       -> selected tests and effective settings
plan(resolved selection)            -> DI plans and memoization groups
execute(plan, event sink)           -> terminal results and run outcome
```

Discovery may execute specification registration code. It must not provision
test dependencies or execute test bodies. Arbitrary side effects in a user's
suite constructor cannot be made harmless by a framework; require declarative
registration and verify that library discovery does not acquire resources.
Planning may load configuration and execute user planning extensions, so report
its failures separately from test failures.

A `RunSession` owns registration, configuration snapshots, environment caches,
memoized resources, cancellation, and reporting state. Instantiate suites through
factories per session. Release resources before declaring the run complete.
Inspect static logging setup and other bootstrap hooks when enabling concurrent
sessions; removing one singleton is not sufficient proof of isolation.

Use logical test IDs derived from build target, suite identity, structured test
path, and explicit variant identity where needed. Keep names for display and
positions for navigation. Reject duplicate logical IDs. Do not use registration
order, object identity, or a source line as the persistent identity. Retain path
segments rather than flattening them into an ambiguous space-separated string.

Selection must precede provisioning. Distinguish:

- Selecting existing suites/tests or variants.
- Filtering by their effective axis choices.
- Overriding activation choices for the requested run.
- Disabling cross-test memoization while retaining ordinary dependency sharing
  inside each individual test graph.

Define precedence between suite configuration and explicit run overrides. Resolve
and validate axis overrides before applying a filter on effective axes. The plan
output shows the final activation and sharing boundaries. Unknown axis values,
unknown explicit test IDs, and stale catalogue references are errors. An empty
explicit selection must not produce an unexplained successful run.

The JSON protocol needs a schema version, build/catalogue identity, logical IDs,
locations, effective settings, structured failures, and correlated events. A
saved selection refers to a build and is revalidated; do not serialize live
closures, DI locators, or arbitrary effect values. Use an explicit transport
channel/framing so test output cannot corrupt protocol messages. CLI, SBT, and
IDE clients share selection semantics through this contract.

## SBT integration strategy

A test framework dependency implementing `sbt.testing.Framework` and an SBT
`AutoPlugin` are different integration surfaces. The framework runs inside the
test environment. The plugin runs inside SBT and can control complete-run launch
boundaries while preserving the standard commands. There is no fundamental
conflict between a distage-owned application and standard `test*` integration.

The [test-interface Runner contract](https://github.com/sbt/test-interface/blob/master/src/main/java/sbt/testing/Runner.java)
permits repeated `tasks` calls. It describes tasks in terms of individual input
task definitions. It provides no general discovery-complete barrier before
execution. Waiting for every suite task to enter `execute` can deadlock with a
sequential or bounded scheduler. Deferring all execution to `done` also fails the
event lifetime requirements of actual hosts.

In [SBT 2.0.9 TestFramework](https://github.com/sbt/sbt/blob/v2.0.9/testing/src/main/scala/sbt/TestFramework.scala),
events are collected during task execution and delivered to listeners after
`execute` returns. The
[JUnit listener](https://github.com/sbt/sbt/blob/v2.0.9/testing/src/main/scala/sbt/JUnitXmlTestsListener.scala)
groups those events by the task group; its nested selector handling matters for
an aggregate application. SBT 1.13.0's inspected execution path has the same
per-task collection pattern. A cross-suite task cannot obtain correct ordinary
suite reporting just by changing `Event.fullyQualifiedName`.

The recommended plugin pipeline is:

```text
test / testOnly / testQuick / testFull (where available)
  -> compile and discover ordinary suite identities
  -> apply this SBT version's selection and incremental-test policy
  -> partition by build target and configured execution/fork group
  -> launch a distage test application with an explicit selection
  -> stream native events and collect terminal results
  -> publish per-suite SBT outcomes, listeners, reports, and success state
```

The names users select remain the ordinary suite names. The application is the
execution backend, not a replacement name users must know. Distage owns scheduling
inside each application and shares compatible resources across its selected
suites. Memoization remains bounded by the process/execution group, not the entire
aggregated multi-project SBT build. Honor explicit fork groups instead of silently
merging them.

There are concrete implementation seams to verify:

- In [SBT 1.13.0 Defaults](https://github.com/sbt/sbt/blob/v1.13.0/main/src/main/scala/sbt/Defaults.scala),
  `test` uses `executeTests`, while `testOnly` and `testQuick` use `inputTests`.
  Overriding only `executeTests` would leave the input commands on the old path.
- In [SBT 2.0.9 Defaults](https://github.com/sbt/sbt/blob/v2.0.9/main/src/main/scala/sbt/Defaults.scala),
  `test` delegates to the incremental input path and `testFull` uses
  `executeTests`. Its private grouping/option-processing helpers are not stable
  public extension points. Design narrow version-specific bindings over public
  keys, and establish whether a small upstream extension is preferable to copying
  host orchestration. Do not use classes in SBT's private packages as an API.
- Reuse public discovery/filter/listener keys where possible. Both versions
  expose `definedTests`, `testFilter`, and `testListeners`; SBT 2 additionally
  exposes `definedTestDigests` and `extraTestDigests`. Preserve framework arguments,
  configured filters/exclusions, setup/cleanup, fork settings, classloader lifetime,
  and aggregation. Mixed-framework projects must keep routing their other tests
  to their original frameworks exactly once.
- Preserve version-specific incremental semantics. SBT 2's
  [documented behavior](https://www.scala-sbt.org/2.x/docs/en/reference/sbt-test.html)
  makes `test` incremental/cached and supplies `testFull` for complete execution.
  Its [status recorder](https://github.com/sbt/sbt/blob/v2.0.9/main/src/main/scala/sbt/internal/IncrementalTest.scala)
  stores success by suite digest and framework arguments. Reporting success only
  for an application name would lose ordinary suite history. The spike must prove
  argument-sensitive recording through an appropriate public seam or identify
  the precise upstream API needed.
- Include normalized distage options, configuration, and relevant plugin inputs
  in invalidation. Running one selected test must not cache success for an entire
  suite under an unfiltered request. Cached exclusions and user exclusions must
  be visible as different selection reasons; neither counts as an executed test.
- Translate correlated native results into per-suite listener/result batches.
  Where SBT listeners assume contiguous suite events, buffer that projection;
  keep the native stream available for live progress. Do not emit late events
  into handlers belonging to already completed tasks. Run-level finalization
  failures must prevent a successful overall result and inappropriate success
  caching.

`distageList` and `distagePlan` are additive inspection commands. Framework
arguments after `--` supply test IDs, activation overrides, axis filtering, and
memoization settings to ordinary commands. A standalone launcher accepts the
same normalized request without SBT.

A portable `sbt.testing.Framework` adapter can still serve other hosts. An
aggregate application with nested selectors is one legitimate mapping; ordinary
suite tasks are another when the sharing scope is narrower. Neither constrains
the plugin's standard command names. Choose that secondary mapping after the
application/plugin path works. Never register both specs and their containing
application for execution in the same mode, and never silently fall back to a
different sharing scope.

Build the plugin for SBT 1 and 2 separately while sharing its protocol/logic.
Keep SBT implementation dependencies out of the portable engine. Forked JVM
tests execute in the test JVM. JS and Native use their actual target programs,
not JVM evaluation of the specifications. The architectural direction is clear;
the exact supported SBT wiring remains an executable spike, not a proven adapter.

## Scala.js and Scala Native

[Scala.js Runner](https://github.com/scala-js/scala-js/blob/v1.22.0/test-interface/src/main/scala/sbt/testing/Runner.scala)
supports controller/worker communication and task serialization; its
[Task](https://github.com/scala-js/scala-js/blob/v1.22.0/test-interface/src/main/scala/sbt/testing/Task.scala)
adds asynchronous completion. Never block the JS event loop waiting for effects.
Use serializable test/application identities and reconstruct executable state
in the target runtime.

Scala Native also has
[task serialization and runner messaging](https://github.com/scala-native/scala-native/blob/v0.5.12/test-interface-sbt-defs/src/main/scala/sbt/testing/Runner.scala).
Its inspected adapter launches native runner processes. Neither separate native
processes nor separate JS runtimes can share a memoized live object. Keep an
entire sharing group within one execution process; introduce explicit shards
only when their resource-sharing cost is accepted. A single application task can
still run its internal tests concurrently.

Start standalone applications with an explicit catalogue of suite factories.
SBT can use its discovered test definitions and supported reflective
instantiation. A generated catalogue is an alternative for closed-world linking,
but its build ordering must be designed: generating sources from the same
compilation's analysis can create a cycle or read stale discovery. Compile suite
definitions first and generate a separate launcher when taking that route.
Avoid runtime classpath scanning as the portable discovery mechanism.

Native feasibility must cover the dependency closure: fundamentals, BIO runtime,
logging, distage core, framework configuration/resources, and plugin loading.
The pinned [izumi-reflect 3.0.8 build](https://github.com/zio/izumi-reflect/blob/v3.0.8/build.sbt)
already declares Native targets, which removes one obvious concern but does not
prove the remaining closure links. Audit the existing `.jvm` and `.js`
implementations; neither set should be blindly reused as the Native backend.
JVM-only integrations such as Docker remain separately identified capabilities.

Scala Native's [0.5.12 compatibility table](https://scala-native.org/en/latest/changelog/0.5.x/0.5.12.html)
covers Scala 2.12, 2.13, and 3.3. Thus the requested language matrix is plausible
at the toolchain level; izumi portability is the unverified part.

For Scala 3, compile published APIs/macros against the oldest supported language
baseline and test consumers on newer releases. Check the complete dependency
closure at that baseline. A library compiled with 3.7 is not automatically
consumable by 3.3; see Scala's
[compatibility rules](https://docs.scala-lang.org/scala3/reference/language-versions/binary-compatibility.html).
Include 3.3.0 if “3.3+” is literal, plus the maintained 3.3 patch and current
stable consumer lanes. Runtime/toolchain changes such as the newer Scala standard
library require dedicated consumer checks. Pin compatible stable dependencies
when implementation begins rather than upgrading this repository during research.

## Reporting, coverage, and IntelliJ

Use correlated run/suite/test events and a terminal result model. JUnit XML and
console output are projections of this model. Assertion failures, unexpected
exceptions, planning failures, explicit skips, cancellation, and run-level
teardown failures remain distinguishable. Concurrent test events must not depend
on adjacency to find their matching start/end. A selected test that never starts
because setup failed still needs a visible outcome.

For portable runs, the build-tool host can write XML and enrich source excerpts;
the browser need not provide a filesystem. JUnit XML does not require a JUnit
runtime or test engine dependency. Reconcile selected IDs, executed IDs,
terminal events, and report counts. A process that exits without a completed run
is an incomplete failure, even if every received test event was successful.

For code coverage, first integrate Scoverage and validate collection/reporting
through every launch mode. The current
[sbt-scoverage documentation](https://github.com/scoverage/sbt-scoverage)
supports Scala 2.12/2.13/3, but restricts JS/Native support to Scala 2.
The repository currently disables JS coverage in its generated settings and
omits Scala 3 coverage in CI with a compiler-crash comment. Those are observed
configuration choices, not evidence that every current compiler has that crash.

| Execution target | Initial coverage strategy |
| --- | --- |
| JVM, Scala 2 | Scoverage, including forked runs and multiple modules |
| JVM, Scala 3.3+ | Scoverage on explicitly tested compiler versions; include assertion macro fixtures |
| JS/Native, Scala 2 | Validate documented Scoverage support with the chosen toolchain, runtime, and output transport |
| JS/Native, Scala 3 | Track as a separate tooling gap; do not promise parity from JVM support |

JaCoCo is a possible JVM alternative. JS engine coverage with source maps and
LLVM-based Native coverage are research options, not drop-in guarantees of Scala
source or branch coverage. Do not create a new instrumentation engine as part of
the runner replacement.

Keep code coverage separate from exact test execution accounting. Shared setup
and concurrent tests also make per-test coverage attribution a distinct problem:
subtracting global counters before/after a test cannot isolate overlapping work.
Defer attribution unless required; isolated runs or instrumentation that tracks
execution context would need their own design.

For IntelliJ, stabilize IDs, locations, launch requests, cancellation, and the
event stream first. Then add Scala PSI recognition, gutter actions, run/debug
configurations, a structured test console, navigation, and rerun-failed support.
Use the shared application/protocol to resolve dynamic tests; the IDE should not
invent a second execution model by parsing test-name expressions.

JetBrains documents
[run configuration integration](https://plugins.jetbrains.com/docs/intellij/run-configurations.html)
and the [execution API](https://plugins.jetbrains.com/docs/intellij/execution.html).
Its [MUnit integration](https://github.com/JetBrains/intellij-scala/tree/idea262.x/scala/test-integration/testing-support-munit)
is a useful implementation reference, subject to checking the actual supported
Scala-plugin extension points. Existing MUnit-specific classes are not a promise
of stable third-party APIs. JVM debugging is the initial target; JS/Native
debugger integration is separately validated. JUnit XML alone cannot provide the
live IDE experience.

## Delivery sequence and acceptance gates

| Step | Deliverable | Observable acceptance |
| --- | --- | --- |
| 0a | Small application/plugin spike for SBT 1 and 2 | Standard `test*` commands launch the application; multiple explicit suites, incremental reruns, changed arguments, sequential scheduling, forks, mixed frameworks, and JUnit output preserve suite identities and counts |
| 0b | Native and Scala 3.3 feasibility spike | Link and run a minimal DI-backed test on Native; compile a 3.3 consumer of the proposed dependency closure; record concrete blockers before estimating the port |
| 1a | Plain assertion macro, spans, diagnostics | Compile and execute behavioral fixtures on 2.12, 2.13, and 3.3+, across JVM/JS/Native; standalone artifact has no ScalaTest dependency |
| 1b | `assert1`, `assert2`, effect adapters, temporary ScalaTest bridge | No eager checks; repeat/concurrent execution is independent; BIO failures are defects; old runner correctly displays new failures |
| 2a | Native specification registration and session ownership | Discovery acquires no test resources; repeated/concurrent sessions do not share registration or run resources; duplicate IDs fail explicitly |
| 2b | Application discovery, selection, planning, and execution | List/resolve/run agree on IDs; axes and memoization controls work; one acquire/release per intended sharing scope |
| 2c | JVM SBT integration and structured/JUnit reporting | Standard command semantics match the SBT version; exact execution and reporting sets agree; per-suite history is correct; no successful incomplete runs |
| 2d | JS and Native target integrations | Linker retains selected suites; serialized tasks reconstruct correctly; async JS completion and target shutdown preserve events and finalization |
| 3 | Coverage integration | A fixture with known executed/unexecuted branches produces the expected report under each claimed combination; instrumentation is absent from published normal artifacts |
| 4 | IntelliJ integration | Run suite/test, navigate failure, rerun failed tests, cancel, and debug a JVM test using the same IDs and settings as CLI/SBT |
| 5 | Migration and ScalaTest retirement | Migrated dependencies and tests run without ScalaTest/Scalactic; only an explicitly retained legacy adapter depends on them during the transition |

Assertions can ship independently and need not await the runner. The two initial
spikes establish the constraints most likely to change the total scope. The
largest uncertainties are Native portability and SBT scheduling/identity parity,
not the syntax of the three assertion entry points.

Acceptance fixtures should check behavior through public boundaries. Macro
fixtures record counters/order and inspect failures using an independent oracle,
so the assertion under test is not its own verifier. Include short-circuiting,
overloaded operators, thrown operands, by-name calls, generic expressions,
multiline source, missing/moved sources, and lazy messages.

Runner fixtures need at least five suites with three tests each: selecting all
five executes/reports exactly 15; selecting two executes/reports exactly six;
selecting one test executes/reports exactly one. Check body-execution records,
not just reported totals. Add equal display names in different suites, wildcard
selection, multiple modules, repeated runs in one process, planning failures,
resource acquisition/release failures, cancellation, and worker serialization.
For SBT 2, distinguish an incremental no-op from a full run. Verify that changes
to activation, memoization, and test selection invalidate the relevant cached
success, and that partial-suite runs cannot suppress a subsequent complete run.
For a correction to the legacy issue, first reproduce the original failure and
confirm its cause before implementing that correction.

Use deterministic in-process selection/session checks for most behavioral
coverage, plus real SBT/Node/Native process fixtures for host contracts. Exercise
published macro artifacts from separate consumer compilations. Run both a
baseline Scala 3.3 lane and newer consumer lanes; compiling only at the newest
version cannot demonstrate backwards consumer compatibility.

During migration, allow both directions: new assertions with the old runner,
and the new runner executing tests whose bodies still throw ScalaTest failures.
Put temporary translation in a separate compatibility adapter. Native spec
definitions must stop inheriting ScalaTest suite/finder classes before both
frameworks can coexist without duplicate discovery. Inventory cancellation,
exception assertions, property-testing integrations, and wiring tests before
deleting the old artifact; replacing `assert` alone is not complete retirement.
