# Native distage test library and runner

Research and proposed implementation plan, 2026-10-01. Repository inspected at
`5be95fb6309c4b0cb9dbea37ed84c0db0a8ff870`. “Native distage runner” means a
distage-owned runner; Scala Native is a separate required execution backend.
Elsewhere in this document, “Native” means Scala Native only; the distage-owned
specification DSL and its registration are called the distage spec front end.

The recommended direction is an independent assertion library and an explicit
test application built on the existing distage planning/execution engine. The
application owns discovery, selection, resource lifetime, and execution. An SBT
plugin preserves the standard `test`, `testOnly`, and `testQuick` commands while
launching that application; IntelliJ uses the same execution contract. JSON is a
boundary format; the in-process engine uses typed models.

This document combines source inspection with the executed spikes linked below.
The spikes exercise stub host applications and portability fixtures; they are not
a production runner implementation. Issue #2361 itself has not been rerun in this
investigation. Proposed production interfaces and commands below do not exist yet.
Decisions that belong to the project owner are listed under
[Open decisions](#open-decisions), with the assumption this plan proceeds under.

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
  Replacing the adapter also requires a distage spec front end.
- [WithTestRegistration](../../distage/distage-testkit-scalatest/src/main/scala/izumi/distage/testkit/services/scalatest/dstest/WithTestRegistration.scala)
  already collects tests per instance, but gives them identity-hash-based UIDs.
  Those UIDs are unsuitable as persistent selection identifiers.
- [DistageTestEnv](../../distage/distage-testkit-core/src/main/scala/izumi/distage/testkit/spec/DistageTestEnv.scala)
  contains global environment and default-module caches. Explicit session
  ownership must address these caches as well as the ScalaTest registry.
- `ScalatestAbstractDistageSpec` defaults to the deprecated `TestConfig.forSuite`,
  which on the JVM
  [scans the suite's package](../../distage/distage-testkit-core/.jvm/src/main/scala/izumi/distage/testkit/model/TestConfigPlatformSpecific.scala)
  for plugins. The JVM
  [test configuration loader](../../distage/distage-testkit-core/.jvm/src/main/scala/izumi/distage/testkit/runner/impl/services/BootstrapFactory.scala)
  merges system properties and environment overrides, and the default activation
  strategy reads activations from that configuration. Effective wiring and
  activation therefore depend on inputs that are not static class dependencies of
  the suite.

The [build definition](../../sbtgen/Deps.scala) targets JVM and JS, with Scala
2.12.21, 2.13.18, and 3.7.4. It does not define a Native target. The build uses SBT
2.0.9 and Scala.js 1.22.0. The user subsequently permitted Scala 3.9.0 as the
implementation target and made 3.3 retention conditional on a 3.9 compiler
producing compatible consumer output. The executed compatibility check below
fails that condition: JVM classfile targeting does not downgrade Scala TASTy.
Native support and the complete 3.9 closure still require dedicated work in
steps 1a, 2a, and 2e; successful stub transport does not establish engine portability.

### Executed feasibility status

| Spike | Observed result | Remaining acceptance |
| --- | --- | --- |
| [0a: JVM SBT](spikes/20261001/sbt/REPORT.md) | Public host substitution and fork bootstrap work on SBT 1.13.0/2.0.9; selection/body/XML counts agree; DI omission and partial-cache failures reproduced; safe public policies exercised | Real engine integration, streaming/concurrent events, failures/cancellation, classloader lifetime, multi-project/configuration behavior |
| [0b: portability](spikes/20261001/portability/REPORT.md) | Not passed. Real JVM closure encounters compiler/API/version-gating failures; diagnostic Native approximation compiles 17 modules but stops in framework config decoding before testkit compilation or link | Resolve dependency/platform/config blockers, then link and execute actual TestPlanner/DistageTestRunner with DI/configuration |
| [0c: target transport](spikes/20261001/transport/REPORT.md) | Partial. Actual JS/Native stub execution works on SBT 1/2 at Scala 3.3.7 and on SBT 2 at 3.9.0; complete groups acquire/release once; aggregate result/XML grouping differs from ordinary suite reporting | Per-suite host projection and verified independent incremental history; Native protocol framing; real engine closure |

All three reports distinguish executed outcomes from source observations and
record exact commands and retained evidence. They do not reproduce the legacy
issue or implement the production runner.

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
For Scala 2, step 1b must establish the available ranges with and without range
positions. Scala 2.13 enables `-Yrangepos` by default and Scala 2.12 does not, so
most 2.12 consumers compile without ranges. Do not assume every typed tree
retains a useful range. The current
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
adapters; the existing testkit core with the distage spec front end and session
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
  -> stream distage events and collect terminal results
  -> publish per-suite SBT outcomes, listeners, reports, and success state
```

The names users select remain the ordinary suite names. The application is the
execution backend, not a replacement name users must know. Distage owns scheduling
inside each application and shares compatible resources across its selected
suites. Memoization remains bounded by the process/execution group, not the entire
aggregated multi-project SBT build. Honor explicit fork groups instead of silently
merging them.

The [executed JVM SBT spike](spikes/20261001/sbt/REPORT.md) establishes a concrete
non-forked seam on SBT 1.13.0 and 2.0.9: substitute only distage's entry in
`loadedTestFrameworks`, capture each group's selected definitions in `Runner.tasks`,
and launch the application once on the first suite task's `execute`. Buffer its
results and emit each suite's events during that suite's ordinary task lifetime.
Five suites execute 15 bodies; two explicit suites execute six; JUnit keeps five
or two ordinary suite files with three test cases each. Sequential scheduling,
explicit exclusions, repeated commands, and a mixed framework all passed. SBT 2's
proxy task binding requires `Def.uncached`.

The fork bypasses that host substitution. A registered test-classpath framework
provides the application bootstrap instead: both exercised SBT versions pass the
fork group's full selected set to `Runner.tasks`, and per-suite tasks project the
same application's results. One default group acquires/releases once; two explicit
fork groups acquire/release twice. Ordinary tasks therefore preserve full-group
sharing on the JVM; one aggregate host task is unnecessary there. This selected-set
call is observed SBT behavior, not a guarantee from the generic test-interface.

Production bindings must preserve the following host contracts:

- In [SBT 1.13.0 Defaults](https://github.com/sbt/sbt/blob/v1.13.0/main/src/main/scala/sbt/Defaults.scala),
  `test` uses `executeTests`, while `testOnly` and `testQuick` use `inputTests`.
  Overriding only `executeTests` would leave the input commands on the old path.
- In [SBT 2.0.9 Defaults](https://github.com/sbt/sbt/blob/v2.0.9/main/src/main/scala/sbt/Defaults.scala),
  `test` delegates to the incremental input path and `testFull` uses
  `executeTests`. `testOnly` evaluates `inputTests(testSelected)`, so settings
  scoped to `testOnly` are not the ones it reads. Its private
  grouping/option-processing helpers are not stable public extension points.
  Design narrow version-specific bindings over public keys, and establish whether
  a small upstream extension is preferable to copying host orchestration. Do not
  use classes in SBT's private packages as an API.
- Reuse public discovery/filter/listener keys where possible. Both versions
  expose `definedTests`, `testFilter`, `testListeners`, and
  `loadedTestFrameworks`; SBT 2 additionally exposes `definedTestDigests` and
  `extraTestDigests`. `definedTests` is computed by `Tests.discover` over
  `loadedTestFrameworks`, so reusing it requires a registered framework whose
  fingerprints match distage specs. The JVM spike uses a registered test-classpath
  framework and its fingerprints for discovery, substitutes its non-forked entry,
  and retains a target-side bootstrap for forks. Stock tasks in every enabled
  configuration must route distage specs through the same application contract. Preserve framework arguments, configured
  filters/exclusions, setup/cleanup, fork settings, classloader lifetime, and
  aggregation. Mixed-framework projects must keep routing their other tests to
  their original frameworks exactly once.
- Evaluate `loadedTestFrameworks` substitution, which Scala.js and Scala Native
  already use for their host-side framework proxies, as the non-forked seam. It
  keeps SBT's own input-test path, where SBT 2 passes framework arguments to its
  success recorder. In-process SBT also passes each test group's selected set to
  one `Runner.tasks` call per framework; that is host behavior, not a
  test-interface guarantee. In JS and Native projects the platform plugins
  already define `loadedTestFrameworks`, so in cross-built projects the distage
  plugin composes with their value instead of replacing it. Forked groups need a
  separate mechanism, because
  [ForkTests](https://github.com/sbt/sbt/blob/v2.0.9/main-actions/src/main/scala/sbt/ForkTests.scala)
  sends framework class names and the fork instantiates the frameworks again.
  The executed JVM spike proves that a target-side bootstrap works for this seam;
  it does not require a plugin-owned fork launcher.
- Preserve version-specific incremental semantics where they remain sound. SBT 2's
  [documented behavior](https://www.scala-sbt.org/2.x/docs/en/reference/sbt-test.html)
  makes `test` incremental/cached and supplies `testFull` for complete execution.
  Its [status recorder](https://github.com/sbt/sbt/blob/v2.0.9/main/src/main/scala/sbt/internal/IncrementalTest.scala)
  stores success by suite digest and framework arguments. Reporting success only
  for an application name would lose ordinary suite history. The JVM proxy and
  fork bootstrap retain ordinary suite tasks, so SBT 2 stock input commands retain
  per-suite, argument-sensitive success recording. Executed checks distinguish
  `testFull` from cached `test`, new arguments from identical arguments, and a
  partial run from a later unfiltered run in-process and forked.
- Stock invalidation is insufficient for DI-wired suites. SBT 2 derives a suite's
  digest from the suite class's transitive class dependencies, the `Test`
  configuration's own resources, test options, and `extraTestDigests`. SBT 1
  `testQuick` compares the last success time with compilation timestamps of the
  same class closure and ignores resources. Neither covers implementations wired
  only through scanned plugins, configuration files outside SBT 2's hashed `Test`
  resources, or activation inputs from system properties or the environment.
  Stock semantics would therefore skip a suite whose effective wiring changed,
  which is the silent non-execution class of #2361. Add a DI digest of the
  classes and resources reachable through each suite's plugin configuration, its
  configuration files, normalized distage options, and activation-relevant
  properties and environment. On SBT 2, contribute it through `extraTestDigests`
  (global and conservative) or a `definedTestDigests` override (per suite); on
  SBT 1, a composed `testQuick / testFilter` can always invalidate selected
  distage suites while retaining stock filtering for other frameworks. The spike
  independently reproduces skipped reruns after editing an external configuration
  file and after compiling a reflected implementation absent from the static
  suite dependency closure. SBT 2 class/resource extra digests produce a measured
  run/no-op/rerun sequence. Global extra digests also invalidate other frameworks
  in the configuration; a per-suite override or own store can avoid that cost.
  The fixture approximates dynamic wiring; deriving the
  complete real distage input closure remains production work.
- Running one selected test must not cache success for an entire suite under an
  unfiltered request. SBT 1's
  [status recorder](https://github.com/sbt/sbt/blob/v1.13.0/testing/src/main/scala/sbt/TestStatusReporter.scala)
  keys success by suite name only, and SBT 1 installs it for `test`, `testOnly`,
  and `testQuick`. With stock listeners, a partial `testOnly` records the whole
  suite as succeeded and a later `testQuick` skips it; the spike reproduces that
  behavior after first recompiling the suite. The exercised safe initial SBT 1
  policy always reruns explicitly selected distage suites. It honors user patterns
  and exclusions through the public `Defaults.selectedFilter` and delegates other
  suites to the inherited stock filter. It does not suppress stock partial-success
  recording; it avoids trusting those entries for distage incremental selection.
  SBT 2's exercised stock input path receives framework arguments, keeping partial
  and complete requests distinct even in a fork. A separate plugin-owned success
  store is an option for efficient SBT 1 history or a transport that publishes
  results outside the stock input path; its necessity is not established for the
  measured JVM seams. Such a store would include suite, normalized selection, DI
  digest, and relevant static compilation inputs. Its per-suite recording and
  failure semantics require a separate executed acceptance gate, including forked
  groups. Cached exclusions and user exclusions remain different selection
  reasons; neither counts as an executed test.
  For an efficient own store, evaluate SBT 2's public `sbt.util.ActionCache`
  against the suite's public `definedTestDigests` entry, including machine-wide
  and remote reuse. SBT 1's precise static closure comes from Zinc's internal
  `sbt.internal.inc.Analysis` relations; prefer a conservative digest of every
  test-classpath file when using public inputs. Any version-pinned use of those
  internal relations needs an explicit boundary and measured justification.
  These are proposed follow-up choices; the spike did not implement or benchmark
  either success store.
- Translate correlated distage results into per-suite listener/result batches.
  Where SBT listeners assume contiguous suite events, buffer that projection;
  keep the distage event stream available for live progress. Do not emit late
  events into handlers belonging to already completed tasks. Every component that
  calls a host `EventHandler` serializes its calls per task:
  [SBT 1.13.0](https://github.com/sbt/sbt/blob/v1.13.0/testing/src/main/scala/sbt/TestFramework.scala)
  collects task events in an unsynchronized buffer, while SBT 2.0.9 switched to a
  thread-safe collection. Run-level finalization failures must prevent a
  successful overall result and inappropriate success caching.

`distageList` and `distagePlan` are additive inspection commands. Framework
arguments after `--` supply test IDs, activation overrides, axis filtering, and
memoization settings to ordinary commands. A standalone launcher accepts the
same normalized request without SBT.

A portable `sbt.testing.Framework` adapter can still serve other hosts. An
aggregate application with nested selectors is one legitimate mapping. The JVM
spike also proves that ordinary suite tasks can project one application with a
complete sharing group, without narrowing its resource lifetime. Neither constrains
the plugin's standard command names. On the JVM, choose that mapping after the
application/plugin path works; on JS and Native, the transport decision below
may require it earlier. Never register both specs and their containing
application for execution in the same mode, and never silently fall back to a
different sharing scope.

SBT discovers suites only through frameworks listed in `testFrameworks`. The
[default list](https://github.com/sbt/sbt/blob/v2.0.9/testing/src/main/scala/sbt/TestFramework.scala)
does not include distage, and nothing on the test classpath can add itself to
it. Once specs stop inheriting ScalaTest classes, a build that adds the new
testkit without the distage SBT plugin or an explicit framework registration
discovers no distage specs, and `test` succeeds. The library cannot detect this
from the test classpath. The plugin is therefore a hard requirement of SBT use,
which the installation and migration documentation must state. Registering a
distage framework in `testFrameworks` without the plugin is unsupported: it runs
under stock SBT accounting, which this section shows is unsound for DI-wired
suites and partial selections. The residual risk remains for builds that ignore
the requirement. Migration acceptance reconciles discovered suite and test counts
before and after each module migrates.

Build the plugin for SBT 1 and 2 separately while sharing its protocol/logic.
Keep SBT implementation dependencies out of the portable engine. Forked JVM
tests execute in the test JVM. JS and Native use their actual target programs,
not JVM evaluation of the specifications. The architectural direction is clear;
the JVM spike proves the exercised discovery, non-forked proxy, fork bootstrap,
reporting, and conservative/digest invalidation seams. Production streaming,
concurrent event delivery, cancellation, finalization failures, setup/cleanup,
classloader lifetime, and multi-project configurations remain gate 2d work.

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

For stock SBT `test*` commands, both platform plugins reach the target runtime
through their test adapters. The
[Scala.js](https://github.com/scala-js/scala-js/blob/v1.22.0/sbt-plugin/src/main/scala/org/scalajs/sbtplugin/ScalaJSPluginInternal.scala)
and
[Scala Native](https://github.com/scala-native/scala-native/blob/v0.5.12/sbt-scala-native/src/main/scala/scala/scalanative/sbtplugin/ScalaNativePluginInternal.scala)
plugins install host-side framework proxies by overriding `loadedTestFrameworks`
and reject `fork := true`. Their
[Scala.js](https://github.com/scala-js/scala-js/blob/v1.22.0/test-adapter/src/main/scala/org/scalajs/testing/adapter/TestAdapter.scala)
and
[Scala Native](https://github.com/scala-native/scala-native/blob/v0.5.12/test-runner/src/main/scala/scala/scalanative/testinterface/adapter/TestAdapter.scala)
test adapters start one JS runtime or native process per SBT thread that uses
them. Decide the JS/Native transport before the JVM SBT integration (step 2d)
fixes the plugin's structure:

- A target-side `sbt.testing.Framework` reached through the platform test
  adapter, exposing one aggregate task per sharing group that reports nested
  selectors. That framework is then a required component on JS and Native, not a
  secondary mapping. An aggregate task changes stock reporting boundaries.
  Do not infer missing success records merely from missing per-suite tasks:
  nested selectors and the host's listener processing also matter. The transport
  spike must measure task groups, XML grouping, and each suite's incremental
  history independently. If stock aggregate accounting differs from ordinary
  suite reporting, a host-side projection or plugin-driven adapter is a design
  option, not an already proven integration.
- A plugin-owned launcher over the platforms' public run interfaces: a Scala.js
  `JSEnv` run with a communication channel, or a separately linked Native
  application.

The [executed transport spike](spikes/20261001/transport/REPORT.md) proves whole
selected-group execution on actual Node/Scala.js and native executables, including
asynchronous JS completion, task serialization, and acquisition/release once per
group. SBT 1/2 runs at 3.3.7 and SBT 2 runs at 3.9.0 pass the stub target checks.
An undiscovered synthetic aggregate `TaskDef` is serialized but never executed:
SBT succeeds with zero tests. Retaining a selected representative suite definition
makes the task execute, with nested selectors preserving testcase owner names.
However, all 15 test cases go into one representative SuiteA XML file, and the
host `Tests.Output` result map contains only SuiteA even when SuiteB owns the
failure. Aggregate execution therefore needs a separate ordinary per-suite host
projection before gate 0c passes.

Initial empty-suite cache observations used five identical stock digests and
cannot prove independent suite history. Even distinct marker methods in one
source file did not eliminate that aliasing. The final probe puts the five suites
in separate source files and verifies five distinct digests before each command.
Two identical SBT 2 `test` requests with persistent SuiteB failure both execute
15 bodies and fail exactly that test; a supposed cache skip of the failed suite
was not reproduced. This controlled failure result does not prove successful
per-suite history or DI-aware invalidation. The separate success probe uses the
same five distinct digests and no arguments: `testFull` executes 15 and passes,
then immediate `test` selects B–E and executes 12 again. This directly proves
only representative A received stock successful-history recording in the measured
aggregate path. Host projection must preserve per-suite reporting and success
recording, or explicitly own compatible history; the latter remains a design
option rather than a demonstrated custom-store implementation. Future history
fixtures must verify distinct digests and check successful and failing suites.

The sibling spike also directly runs a linked JS application through public
`JSEnv.startWithCom`: an explicit two-suite request yields six EVENT messages,
then END after release; ordinary body output travels separately on stdout, and
the host acknowledges QUIT and waits for successful exit. A separately linked
Native application executes the same six bodies, but an independent Native
protocol channel has not been verified. These launch paths are viable building
blocks; neither yet supplies stock per-suite host test binding.

With the stock per-thread adapters, keeping a sharing group within one process
holds only when the whole group executes as one target task. The transport spike
proves separate configured groups use separate actual processes and resources.
A host-side wrapper may expose ordinary suite result tasks while driving one
target task per group, but that composition needs its own executed spike; do not
assume the JVM's proxy structure transfers unchanged.

Start standalone applications with an explicit catalogue of suite factories.
SBT can use its discovered test definitions and supported reflective
instantiation. A generated catalogue is an alternative for closed-world linking,
but its build ordering must be designed: generating sources from the same
compilation's analysis can create a cycle or read stale discovery. Compile suite
definitions first and generate a separate launcher when taking that route.
Avoid runtime classpath scanning as the portable discovery mechanism.

Native feasibility must cover the dependency closure: fundamentals, BIO runtime,
logging, distage core, framework configuration/resources, and plugin loading.
The [portability spike](spikes/20261001/portability/REPORT.md) inventories a
19-project Native approximation. Pinned Cats Effect 3.6.3 lacks the required
Native 0.5 publication, and ZIO interop cats/tracer 23.1.0.5 has no checked Native
publication. Diagnostic JVM-Provided substitutions expose later source errors;
they cannot be linked or shipped as Native runtime dependencies. Reusing JS
platform sources exposes JS timers/crypto/environment imports; reusing JVM BIO
exposes a missing Native ZIO `fromCompletionStage`. A diagnostic adapter advances
compilation without proving its runtime semantics. With explicit mixed-platform
substitutions, 17 modules compile on 3.9.0, but framework compilation stops deriving
`Decoder[Option[RenderingOptions]]` in `LoggerConfigLoader.scala:41`. The testkit
and retained DI/configuration application never compile or link. This is a bounded
list of port prerequisites, not an estimate or evidence that the engine works.
`distage-testkit-core` depends on `distage-framework`, and `TestPlanner` uses
configuration loading, the role launcher's logger and activation parsing, module
providers, and plugin configuration. The configuration extension uses pureconfig
on the JVM and circe on JS, so Native needs an explicitly chosen backend.
The pinned [izumi-reflect 3.0.8 build](https://github.com/zio/izumi-reflect/blob/v3.0.8/build.sbt)
already declares Native targets, which removes one obvious concern but does not
prove the remaining closure links. Audit the existing `.jvm` and `.js`
implementations; neither set should be blindly reused as the Native backend.
JVM-only integrations such as Docker remain separately identified capabilities.

Scala Native 0.5.12 and Scala.js 1.22.0 actually compile/link/run the transport
stub on Scala 3.9.0 in the sibling transport spike. Earlier successful 3.3.7
runs remain recorded evidence. Neither result proves the izumi dependency closure.

Use Scala 3.9.0 as the proposed primary producer/consumer lane under the user's
latest permitted target ([Scala 3.9 release guidance](https://www.scala-lang.org/news/3.9/)).
The [separate producer/consumer probe](spikes/20261001/portability/REPORT.md)
compiles ordinary classes, inline code, and a quoted macro with 3.9.0,
`-release:17`, and `-source:3.3`. A separate 3.3.0 consumer fails before macro
expansion: it expects TASTy 28.3 and finds 28.9. A separate 3.9.0 consumer runs
all three forms; `javap` reports JVM major version 61. These are different
compatibility dimensions: old JVM classfiles and source syntax do not provide
old Scala consumer output. No tested flag downgrades TASTy. If 3.3 consumers are
retained as an additional explicit requirement, they need a separate 3.3 producer
lane and closure fixes; do not assume dual publication or a backport is accepted.

The earlier actual closure attempts on 3.3.0/3.3.7/3.3.8 fail in
`fundamentals-functoid` at `Implicits.searchIgnoring` / `Expr.summonIgnoring`.
These APIs exclude dummy implicit symbols; ordinary implicit search is not a
semantically justified replacement. Separately packaged 3.3.8
basics/literals/language artifacts are consumed by a 3.3.0 macro fixture, which
proves only that limited artifact set. On 3.9.0, the actual JVM closure crashes
in `FunctoidDummyImplicit.scala` at `genBCode` with backend parallelism 16. A single
backend-parallelism=1 control passes functoid, then encounters exact-version
PureConfig dependency gating tied to 3.7.4. That control does not establish the
compiler exception's root cause. Correct generated dependency/flag conditions and
isolate the compiler failure before declaring a 3.9 upgrade successful.

The SBT 2 plugin compiles with SBT 2's own Scala version (3.8.4 for SBT 2.0.9).
A shared library published only as 3.9 Scala artifacts cannot automatically be
consumed by that older host compiler. Keep the tested Java/test-interface or a
version-compatible host logic boundary, and verify cross-artifact consumption
rather than introducing a new TASTy mismatch. The JVM spike already crosses the
host/test classloader boundary through Java reflection; production protocol data
must retain an explicit language-neutral or compatible representation.

Consumer compilers expand the closure's macros, so a macro built on the chosen
publishing compiler runs inside each supported consumer compiler. Macros that use only the public
`scala.quoted` API fall under the compatibility rules above. Two existing macros
do not:
[PlanCheckMaterializer](../../distage/distage-framework/src/main/scala-3/izumi/distage/framework/PlanCheckMaterializer.scala)
casts `Quotes` to the compiler's `QuotesImpl`, and
[ScalaReleaseMaterializer](../../fundamentals/fundamentals-language/src/main/scala-3/izumi/fundamentals/platform/language/ScalaReleaseMaterializer.scala)
reads `dotty.tools.dotc.config.Properties`. Compiler internals are outside the
public macro API, so such a macro can fail during expansion in a newer compiler.
Remove those references, isolate them behind version-checked shims, or have
consumer fixtures expand each such macro on every Scala 3 lane (step 2a). New
assertion macros use only the public `scala.quoted` API.

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
| JVM, supported Scala 3 lanes | Scoverage on explicitly tested compiler versions; include assertion macro fixtures |
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
The Scala plugin's
[MUnit integration](https://github.com/JetBrains/intellij-scala/tree/idea262.x/scala/test-integration/testing-support-munit)
is a reference for configuration types, configuration producers, and framework
detection only: it runs MUnit through IntelliJ's JUnit 4 starter and depends on
the JUnit plugin. The plugin's
[test-runners module](https://github.com/JetBrains/intellij-scala/tree/idea262.x/scala/test-integration/test-runners),
whose ScalaTest, uTest, and specs2 runners report to the structured test console
through TeamCity service messages, is the closer precedent for a custom runner.
Those runners print the messages to stdout, where test output can corrupt them.
The precedent therefore applies to test-tree building and console integration,
not to transport: the IntelliJ adapter reads the explicit-channel protocol and
feeds the structured console from it.
Check the actual supported Scala-plugin extension points; existing
framework-specific classes are not a promise of stable third-party APIs. JVM
debugging is the initial target; JS/Native debugger integration is separately
validated. JUnit XML alone cannot provide the live IDE experience.

## Delivery sequence and acceptance gates

| Step | Deliverable | Observable acceptance |
| --- | --- | --- |
| 0a | Executed JVM application/plugin spike ([report](spikes/20261001/sbt/REPORT.md)) | Standard `test*` commands launch the application; multiple explicit suites, incremental reruns, changed arguments, sequential scheduling, forks, mixed frameworks, and JUnit output preserve suite identities and counts. The spike records its discovery mechanism, its non-forked and forked seams, its incremental-invalidation inputs, and its measured stock SBT 2 recording and conservative SBT 1 quick policy for non-forked and forked groups; a custom success store is a separate proposed optimization |
| 0b | Native closure and Scala publishing compatibility spike ([report](spikes/20261001/portability/REPORT.md)) | `TestPlanner` and `DistageTestRunner` link and run a DI-backed test on Native with configuration loading and a static, non-scanning plugin configuration, or each concrete Native blocker (module, missing API, or dependency) is recorded; the spike lists every module it linked, including the configuration backend chosen for Native. The proposed dependency closure compiles with the permitted 3.9 compiler and is consumed from a separate build, or each concrete blocker is recorded; the older-consumer compatibility condition is tested separately and its TASTy failure retained; blockers are recorded before estimating the port |
| 0c | Partially executed JS/Native transport spike ([report](spikes/20261001/transport/REPORT.md)) | With a stub application, SBT 1 and 2 `test` and `testOnly` on JS and on Native preserve per-suite identities and counts through the chosen transport; every selected suite gets its own listener group and JUnit file; SBT 1 `testQuick` and SBT 2 incremental `test` record per-suite history, checked independently with verified distinct suite digests, successful/failing suites, changed arguments and partial requests; a shared stub resource is acquired and released exactly once per sharing group |
| 1a | Build matrix for the assertion modules | The assertion modules and their dependencies build on Scala Native and compile with the selected Scala 3 publishing compiler; separate consumer builds compile against locally published artifacts on every Scala 3 consumer lane; existing JVM and JS lanes still pass |
| 1b | Plain assertion macro, spans, diagnostics | Behavioral fixtures compile and execute on 2.12, 2.13, and the selected Scala 3 lane, across JVM/JS/Native. Fixtures compiled on 2.12 with and without `-Yrangepos`, and on 2.13 with range positions disabled, render exact spans or the explicit representation for missing ranges. The assertion artifacts' dependency graphs contain no `org.scalatest` or `org.scalactic` module |
| 1c | `assert1`, `assert2`, effect adapters, temporary ScalaTest bridge | No eager checks; repeat/concurrent execution is independent; BIO failures are defects; old runner correctly displays new failures |
| 2a | Scala 3.9 closure and compiler compatibility | Every module in the closure identified by 0b compiles with the permitted Scala 3.9 compiler and passes its existing tests; consumer builds on every Scala 3 lane compile against it and expand every closure macro that references compiler internals, including a compile-time plan check and `ScalaRelease` materialization, unless those references were removed |
| 2b | Distage spec front end and session ownership | Discovery acquires no test resources; repeated/concurrent sessions do not share registration or run resources; duplicate IDs fail explicitly; plain non-DI suites can register and execute through the front end; existing testkit suites that use no ScalaTest-specific API compile after dependency and import changes only |
| 2c | Application discovery, selection, planning, and execution | List/resolve/run agree on IDs. Unknown axis values and unknown explicit test IDs fail before provisioning. An activation override changes the activation shown by the plan output. With cross-test memoization disabled, a memoized resource is acquired and released once per test while sharing inside each test graph is unchanged; otherwise it is acquired and released once per intended sharing scope |
| 2d | JVM SBT integration and structured/JUnit reporting | Standard command semantics match the SBT version, except that incremental runs may rerun distage suites that stock SBT would skip, never the reverse; exact execution and reporting sets agree; per-suite history is correct. Editing the suite class, or an implementation reachable only through a scanned plugin, reruns the suite under SBT 2 `test` and SBT 1 `testQuick`. A partial-suite run, in-process or forked, does not suppress a later complete run on either SBT version. Concurrent test events within one suite all reach SBT 1 listeners. No incomplete run reports success |
| 2e | Native port of the testkit closure; JS and Native target integrations | The closure links on Native and passes its portable tests; linker retains selected suites; the SBT checks of step 2d hold on JS and Native; with a target-side framework, serialized tasks reconstruct correctly; async JS completion and target shutdown preserve events and finalization |
| 3 | Coverage integration | A fixture with known executed/unexecuted branches produces the expected report under each claimed combination; instrumentation is absent from published normal artifacts |
| 4 | IntelliJ integration | Run suite/test, navigate failure, rerun failed tests, cancel, and debug a JVM test using the same IDs and settings as CLI/SBT |
| 5 | Repository-wide migration and ScalaTest retirement | No current published module or repository test dependency contains `org.scalatest` or `org.scalactic`; migrated suites reference neither package; each migrated module discovers, executes, and reports the same selected tests. A temporary legacy adapter may exist only during the staged migration and is removed before this gate closes |

Assertions (steps 1a–1c) can ship independently and need not await the runner.
The initial spikes (0a–0c) establish the constraints most likely to change the
total scope. The largest uncertainties are Native portability and SBT
scheduling/identity parity, not the syntax of the three assertion entry points.

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
verified distinct stock suite digests for incremental fixtures,
resource acquisition/release failures, cancellation, and worker serialization.
For SBT 2, distinguish an incremental no-op from a full run. Under SBT 2 `test`
and SBT 1 `testQuick`, verify that changes to the suite class, activation,
memoization, test selection, configuration resources, and implementations
reachable only through scanned plugins invalidate the relevant cached success,
and that partial-suite runs, in-process or forked, cannot suppress a subsequent
complete run. Emit concurrent events from
parallel tests in one suite and check that SBT 1 listeners receive every event.
A correction to the legacy runner, if scheduled (see
[Open decisions](#open-decisions)), first reproduces the original failure and
confirms its cause.

Use deterministic in-process selection/session checks for most behavioral
coverage, plus real SBT/Node/Native process fixtures for host contracts. Exercise
published macro artifacts from separate consumer compilations. Run both a
selected producer lane and separate supported consumer lanes. A 3.3 consumer
check is mandatory only if that additional support is retained; it must consume
a separately built compatible producer, not 3.9 artifacts with old-source flags.

During migration, allow both directions: new assertions with the old runner,
and the new runner executing tests whose bodies still throw ScalaTest failures.
Put temporary translation in a separate compatibility adapter. Distage spec
front-end definitions must stop inheriting ScalaTest suite/finder classes before
both frameworks can coexist without duplicate discovery. From then on, SBT
discovers migrated specs only through the distage plugin, so each migrated
module reconciles its discovered suite and test counts with its pre-migration
counts. Within the retirement scope, inventory
cancellation, exception assertions, property-testing integrations, wiring tests,
and compile-time type-check assertions before deleting the old artifact;
replacing `assert` alone is not complete retirement. The testkit's own DSL tests
use `assertCompiles`, and such checks need separate Scala 2 and Scala 3
implementations.

## Open decisions

These choices belong to the project owner. The plan proceeds under the stated
assumptions; a different answer changes the listed steps.

- **Scope of ScalaTest retirement.** The requested endpoint removes ScalaTest
  entirely, including plain repository suites and every current published module.
  Stage the migration through temporary compatibility artifacts, then delete them
  before step 5 closes. The earlier inventory found 118 test files extending
  `AnyWordSpec` directly; 24 of the 25 files using `assertTypeError`,
  `assertDoesNotCompile`, or `assertCompiles` were plain suites, and ScalaTest was a
  global test dependency. These are prior source observations, not a refreshed
  exact count. The new front end must support plain non-DI suites as well as
  distage suites, and include exception/cancellation/compile-check replacements.
- **Legacy correction for #2361.** Assumed: the new runner and plugin address the
  failure class. A correction to the released ScalaTest-based runner is separate
  maintenance tracked by the issue, not a step of this plan; scheduling it adds a
  step independent of 0a–5.
- **Scala 3 publishing baseline.** The latest user instruction permits 3.9 and
  retains 3.3 only if the 3.9 compiler can emit compatible consumer artifacts.
  The separate consumer check fails that condition at TASTy loading, despite
  Java 17 bytecode and Scala 3.3 source targeting. The primary implementation
  lane is therefore proposed as 3.9.0; retaining 3.3 would require an explicit
  separate producer/API compatibility workstream. Keep earlier 3.3 results as
  evidence, without treating a backport or dual publication as automatically
  authorized. SBT 2.0.9 host-side code remains on its own 3.8.4 compiler.
- **Source compatibility of the spec front end.** Assumed: the front end keeps
  the `Spec1`, `Spec2`, `SpecZIO`, and `SpecIdentity` entry points and the
  `should`/`must`/`can`/`in` registration shape, so a suite that uses no
  ScalaTest-specific API migrates by dependency and import changes. Bodies
  returning ScalaTest `Assertion`, matchers, and other ScalaTest APIs migrate
  explicitly, temporarily through the compatibility adapter. A new DSL changes
  steps 2b and 5.
