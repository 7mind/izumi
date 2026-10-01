# Acceptance checklist: native distage test library and runner

This is the completion contract for implementing
[the plan](20261001-distage-native-testkit-plan.md). It restates as numbered
items the gate clauses of steps 1a–5 and the acceptance requirements the plan
states outside its gate table. For each item it gives the point at which the item
must hold.

The owner controls this file. The implementing agent may add items or strengthen
them, but may not remove, narrow, or reinterpret one. If the agent cannot meet an
item, or finds it contradicted by evidence, the item is *waiting on owner*, never
done. Edits to the plan do not change this file.

Where an item and the plan differ on what must be achieved, the stricter of the
two applies; the plan defers work only through its open decisions. This file
alone defines when and where an item is evaluated. Its evaluation points,
supersessions, scope rule, 1a.2 carve-outs, and lanes L1–L6 override the plan's
unqualified wording.

## Rules

- **States**, recorded per item in the
  [status ledger](20261001-distage-native-testkit-status.md):
  - not started;
  - in progress;
  - waiting on a named external condition;
  - waiting on owner;
  - done.
- **Evaluation points.**
  - *Step* items are verified at the commit that completes their step, and the
    status ledger records that commit.
  - *Final* items are verified there too, and verified again on the final head.
  - Each transitional item names the final item that supersedes it.
- **Evidence.** Every verification records the commands run, the observed result
  (counts, exit codes, report contents), and the commit. A test counts as
  evidence only after it has been confirmed to exercise the item it is cited
  for.
- **Scope.** An item that names one SBT version holds on that version. Every
  other 2d item holds on both SBT 1.13.0 and SBT 2.0.9. On JS and Native,
  forked variants do not apply, because the platform plugins reject
  `fork := true`.
- **Other requirements.** The plan's other requirements on the implementation
  also apply.
  - **What counts.** Statements made with must, must not, never, needs, should,
    remain, or in the imperative, outside the gate table and not restated here.
  - **What does not.** Descriptions of observed or external behaviour, such as
    spike results and SBT or platform facts; alternatives the plan does not
    choose; and proposed follow-ups.
  - **Enumeration.** Before the first commit of step 1a, the agent adds each
    such requirement to the "Other requirements" section of this file as an
    item, O.1 onwards.
    - Each item cites its plan line and is marked *step* or *final* under its
      assigned step.
    - These items follow every rule here, including states, evidence, and
      completion.
    - The reviewer subagent checks the O items against the plan at every step
      boundary, and the owner may amend them.
- **Completion.** The goal is complete only when every item is done at each of
  its evaluation points.

## Lanes

Local equivalents of the CI jobs in `.github/workflows/build.yml`, run through
`direnv exec . mdl ... :gen <action>`. Each `:gen` rewrites the tracked generated
files with that lane's flags. Run lanes in a disposable worktree, or regenerate
with the committed flags afterwards (item L5).

- **L1** (final): JVM lanes. Run `platform:jvm` on JDK 17, 21, and 25, each with
  Scala 2.12, 2.13, and 3. Use `:coverage` for Scala 2 and `:test` for Scala 3,
  as CI does.
- **L2** (final): JS lanes. Run `platform:js-nojvm` on JDK 21 with Scala 2.12,
  2.13, and 3, using CI's action per Scala version. CI also runs JDK 17 and 25.
  Locally, one JDK is accepted because the JDK only hosts the compiler and linker
  for JS; the remaining risk is JDK-specific compiler or linker behaviour, which
  CI catches once the branch is pushed.
- **L3** (final): Native lanes. Use the platform values that step 1a adds, on
  JDK 21 with Scala 2.12, 2.13, and 3. One JDK is accepted on the same grounds
  as L2.
- **L4** (final): the site lane, `platform:js` on JDK 21 with Scala 3,
  `:site-test`.
- **L5** (final): after `bash sbtgen.sc --js --native`,
  `git diff --exit-code build.sbt project/plugins.sbt project/build.properties`
  succeeds.
- **L6** (final, from the completion of step 1a): a local `publishLocal` of the
  `--js --native` build produces a `_native0.5_*` artifact on each Scala version
  for every published module that 1a.2 covers. It produces none for
  `distage-testkit-scalatest`.

## General

- **G.1** (final): Acceptance fixtures check behavior through public
  boundaries. Most behavioral coverage uses deterministic in-process selection
  and session checks; host contracts use real SBT, Node, and Native process
  fixtures.

"Scala 3 consumer lanes" are those the plan's
[publishing baseline](20261001-distage-native-testkit-plan.md#scala-3-publishing-baseline)
names: 3.9.0, then the newest 3.9.x patch and the newest stable Scala Next
release once they exist.

## 1a: Native build of the existing libraries

- **1a.1** (final): `sbtgen/Deps.scala` adds Native to its cross targets, with
  Scala Native pinned to 0.5.12.
- **1a.2** (final): Every module that builds for JS also builds for Native on
  2.12, 2.13, and the repository's Scala 3 compiler, except the legacy
  `distage-testkit-scalatest`, and every such published module publishes for
  Native. Unpublished test-only projects build and run their tests on Native but
  publish nothing. The portable protocol module's Native variant compiles with
  3.8.4, per 2a.1. Publishing is checked by L6.
- **1a.3** (final): `.native` source sets replace the spike's fixture-local files
  and build-time patches.
- **1a.4** (final): Cats Effect is 3.7.x, with `scalac-compat-annotation` on
  Scala 2 compile classpaths while typelevel/cats-effect#4693 is open.
- **1a.5** (final): A released zio-interop-cats supplies Native artifacts. No
  committed build resolves a local or snapshot version.
- **1a.6** (step, superseded by 5.6): Every shared test suite that runs on JS also
  passes on Native through ScalaTest's Native artifacts, or moves to a
  platform-specific source set with its reason recorded.
- **1a.7** (final): A facility whose dependency has no Native artifact, such as
  the Scala 2 circe-derivation macros, gets a Native implementation or is
  documented as unavailable there.
- **1a.8** (step, superseded by 5.7): `distage-testkit-core` runs the 0b engine
  checks as Native tests: a DI and configuration test, and four parallel tests
  that share one memoized `Lifecycle` resource, acquired and released exactly
  once.
- **1a.9** (final): CI builds and tests every Native lane from clean outputs.
  `build.yml` defines the Native jobs, `gen` fails for an unknown platform, each
  Native lane runs Native test projects, and L3 passes.
- **1a.10** (step): Existing JVM and JS lanes still pass: L1 and L2.
- **1a.11** (final): logstage-core's shared sink tests pass on Native. Each of its
  JVM-specific facilities (the file sinks, the threading log queue, and the JUL
  adapter) either has a Native implementation whose tests pass on Native, or is
  documented as unavailable there.

## 1b: build matrix for the assertion modules

- **1b.1** (final): The assertion modules build on Scala Native.
- **1b.2** (final): They compile with the repository's Scala 3 compiler (3.9.0
  after step 2a).
- **1b.3** (final): Separate consumer builds compile against locally published
  artifacts on every Scala 3 consumer lane. After 2a, those artifacts are
  compiled with 3.9.0.
- **1b.4** (step): Existing JVM, JS, and Native lanes still pass: L1–L3.

## 1c: plain assertion macro, spans, diagnostics

- **1c.1** (final): Behavioral fixtures compile and execute on 2.12, 2.13, and
  Scala 3, across JVM/JS/Native.
- **1c.2** (final): Fixtures compiled on 2.12 with and without `-Yrangepos`, and
  on 2.13 with range positions disabled, render exact spans or the explicit
  representation for missing ranges.
- **1c.3** (final): The assertion artifacts' dependency graphs contain no
  `org.scalatest` or `org.scalactic` module and no izumi module above
  fundamentals.
- **1c.4** (final): Macro fixtures record counters and order, and inspect
  failures with an independent oracle. They cover short-circuiting, overloaded
  operators, thrown operands, by-name calls, generic expressions, multiline
  source, missing and moved sources, and lazy messages.
- **1c.5** (final): The plain assertion contract holds.
  - `assert(condition)` returns `Unit`. On failure it throws a portable
    `AssertionError` subtype that carries structured diagnostics and gives a
    useful message even under an unrelated runner.
  - The plain assertion module is independent of both effect runtimes.
  - Evaluation count, order, short-circuiting, by-name behavior, and thrown
    exceptions are preserved. Array equality does not silently become deep
    equality, and unrelated exceptions do not become false predicates.
  - Expressions outside the recognized set remain valid assertions, with an
    opaque observation and their source location.
  - Diagnostics carry the source identity, the expression span, the expression
    text stored at compile time, and subexpression observations. When the
    available source differs from the recorded content, the failure shows the
    compiled excerpt and identifies the mismatch. Missing source or range
    information is represented explicitly.
  - Failure rendering is bounded and lazy. A failed value renderer preserves
    the original failure and exposes the rendering error.

## 1d: `assert1`, `assert2`, effect adapters, temporary ScalaTest bridge

- **1d.1** (final): On 2.12, 2.13, and Scala 3 across JVM/JS/Native, no check is
  eager.
- **1d.2** (final): On the same lanes, repeat and concurrent executions are
  independent.
- **1d.3** (final): On the same lanes, BIO failures are defects.
- **1d.4** (step, superseded by 5.5): The old runner correctly displays new
  failures on its existing JVM/JS targets.
- **1d.5** (final): The effect forms' contract holds.
  - `assert1[F]` raises its failure inside the effect's suspended computation.
  - `assert2[F]` fails as an effect defect and leaves the typed error channel
    unchanged.
  - The BIO adapter preserves the existing `IO2.sync` defect behavior, and the
    Cats Effect adapter suspends with `Sync.delay`.
  - `QuasiIO` is not exposed as a suspension guarantee.

## 2a: repository-wide Scala 3.9 move

- **2a.1** (final): Every Scala 3 module compiles with 3.9.0 and passes its
  existing tests on every platform it builds for. The exceptions are the SBT 2
  plugin and the portable protocol module (all platform variants), which compile
  with SBT 2's 3.8.4 and resolve no 3.9-compiled artifact.
- **2a.2** (final): Dependency and compiler-flag conditions no longer match only
  the exact version 3.7.4.
- **2a.3** (final): The 3.9.0 backend race (scala/scala3#27209) is fixed in a
  released compiler, or 3.9.0 builds use backend parallelism 1 until it is.
- **2a.4** (final): Consumer builds on every Scala 3 lane compile against locally
  published artifacts. They expand every closure macro that references compiler
  internals, including a compile-time plan check and `ScalaRelease`
  materialization, unless those references were removed.

## 2b: base runner, front ends, test-only projects, session ownership

- **2b.1** (final): `distage-test-runner` and the assertions module depend only on
  fundamentals modules and the portable protocol module.
- **2b.2** (final): The SBT project graph has no cycle.
- **2b.3** (final): Fundamentals tests run from unpublished `fundamentals-*-test`
  projects on the JVM.
- **2b.4** (step, superseded by 2e.1): Their JS lanes, and the Native lanes from
  1a, keep running until the base runner takes them over in 2e.
- **2b.5** (final): Plain suites with synchronous and `Future` bodies register
  and execute on the base runner.
- **2b.6** (final): `distage-testkit-runner` extends it as an execution provider.
- **2b.7** (final): Discovery acquires no test resources and executes no test
  bodies.
- **2b.8** (final): Repeated and concurrent sessions do not share registration or
  run resources.
- **2b.9** (final): Duplicate IDs fail explicitly.
- **2b.10** (final): Existing distage and plain suites that use no other
  ScalaTest API compile after dependency and import changes only. On the final
  head, a compatibility fixture verifies this. It holds such suites in their
  migrated form, and they compile and pass. The status ledger records a diff of
  each against its pre-migration source, and that diff touches only import
  lines.
- **2b.11** (final): Each run session instantiates suites through its own
  factories, and the run is declared complete only after its resources are
  released.

## 2c: application discovery, selection, planning, and execution

- **2c.1** (final): List, resolve, and run agree on IDs.
- **2c.2** (final): Unknown axis values and unknown explicit test IDs fail before
  provisioning.
- **2c.3** (final): An activation override changes the activation shown by the
  plan output.
- **2c.4** (final): With cross-test memoization disabled, a memoized resource is
  acquired and released once per test, while sharing inside each test graph is
  unchanged. Otherwise it is acquired and released once per intended sharing
  scope.
- **2c.5** (final): A stale catalogue reference is an error. An explicit request
  to the application that selects nothing never produces an unexplained
  successful run; such requests are test IDs after `--`, CLI, IDE, and saved
  selections. SBT's own suite-name selection follows 2d.3.
- **2c.6** (final): Logical test IDs derive from the build target, the suite
  identity, the structured test path, and an explicit variant identity where
  needed. Path segments stay separate rather than being flattened into a string.
  Registration order, object identity, and source lines are never the persistent
  identity. Names serve display and positions serve navigation.
- **2c.7** (final): The precedence between suite configuration and explicit run
  overrides is defined. Axis overrides are resolved and validated before a
  filter on effective axes applies. The plan output shows the final activation
  and sharing boundaries.
- **2c.8** (final): Planning failures, including failures of configuration
  loading and of user planning extensions, are reported separately from test
  failures.
- **2c.9** (final): The JSON protocol carries:
  - a schema version;
  - the build and catalogue identity;
  - logical IDs and locations;
  - effective settings;
  - structured failures;
  - correlated events.

  A saved selection refers to a build and is revalidated. No live closures, DI
  locators, or effect values are serialized. An explicit transport channel with
  framing keeps test output from corrupting protocol messages. CLI, SBT, and IDE
  clients share selection semantics through the protocol.

## 2d: JVM SBT integration and structured/JUnit reporting

The scope rule above applies to every 2d item.

- **2d.1** (final): Plain and distage suites in one module and one SBT command
  share selection, reporting, and history semantics.
- **2d.2** (final): Every selected suite has a terminal completion record from
  the target. A fixture whose target skips one selected suite reports that suite
  as an error, never as an empty pass.
- **2d.3** (final): Standard command semantics match the SBT version, except that
  incremental runs may rerun distage suites that stock SBT would skip, never the
  reverse.
- **2d.4** (final): Exact execution and reporting sets agree.
- **2d.5** (final): Per-suite history is correct.
- **2d.6** (final): Editing the suite class, or an implementation reachable only
  through a scanned plugin, reruns the suite under SBT 2 `test` and SBT 1
  `testQuick`.
- **2d.7** (final): Untracked configuration inputs force a rerun with an explicit
  cache decision.
- **2d.8** (final): A partial-suite run, in-process or forked, does not suppress
  a later complete run on either SBT version.
- **2d.9** (final): Concurrent test events within one suite all reach SBT 1
  listeners.
- **2d.10** (final): No incomplete run reports success.
- **2d.11** (final): The runner fixtures exist and pass, with real SBT process
  fixtures for host contracts and body-execution records checked, not just
  reported totals.
  - Five suites of three tests each. Selecting all five executes and reports
    exactly 15, selecting two exactly six, and selecting one test exactly one.
  - Equal display names in different suites.
  - Wildcard selection, multiple modules, and repeated runs in one process.
  - Planning failures, resource acquisition and release failures, and failing
    bodies.
  - An application launch failure, reported for every selected suite without a
    relaunch.
  - Cancellation, worker serialization, and SBT's default parallel task
    execution.
  - Five-suite sharing groups with host thread limits of one and two, completing
    without a barrier that waits for all suite tasks to start.
  - After cancellation or target-process death, a new command in the same SBT
    session executes successfully, with fresh run resources and without caching
    the incomplete run.
  - On SBT 2, an incremental no-op is distinguished from a full run.
  - Incremental fixtures first verify distinct stock suite digests. Then, under
    SBT 2 `test` and SBT 1 `testQuick`, a cached success is invalidated by
    changes to the suite class, activation, memoization, test selection,
    configuration resources, and implementations reachable only through
    scanned plugins.
  - Partial-suite runs, in-process or forked, cannot suppress a later complete
    run.
  - Parallel tests in one suite emit concurrent events, and SBT 1 listeners
    receive every one.
- **2d.12** (final): Events stream. Test start and finish events reach the
  distage event stream and the IDE channel before their group completes, while
  SBT listener batches may stay buffered per suite.
- **2d.13** (final): Framework arguments, configured filters and exclusions,
  setup and cleanup, fork settings, classloader lifetime, and aggregation behave
  as they do for stock frameworks.
- **2d.14** (final): Stock tasks in every enabled configuration route distage
  specs through the same application contract. Fixtures cover multi-project
  builds and a test configuration besides `Test`.
- **2d.15** (final): Mixed-framework projects route their other tests to their
  original frameworks exactly once.
- **2d.16** (final): Cached exclusions and user exclusions remain different
  selection reasons, and neither counts as an executed test.
- **2d.17** (final): No component emits late events into the handlers of
  completed tasks. Every component that calls a host `EventHandler` serializes
  its calls per task.
- **2d.18** (final): A run-level finalization failure prevents a successful
  overall result and prevents success caching.
- **2d.19** (final): Specs and their containing application are never both
  registered for execution in the same mode, and no run silently falls back to
  a different sharing scope.
- **2d.20** (final): The in-process 0a matrix passes with only the target
  bootstrap registered, or the status ledger records the measured need that
  justifies host substitution.
- **2d.21** (final): The plugin is built for SBT 1 and SBT 2. `distageList` and
  `distagePlan` exist. Framework arguments after `--` supply test IDs,
  activation overrides, axis filtering, and memoization settings. A standalone
  launcher accepts the same normalized request without SBT.
- **2d.22** (final): Results stay distinguishable and reconciled.
  - Assertion failures, unexpected exceptions, planning failures, explicit
    skips, cancellation, and run-level teardown failures remain
    distinguishable.
  - Concurrent test events are matched without relying on adjacency.
  - A selected test that never starts because setup failed has a visible
    outcome.
  - Selected IDs, executed IDs, terminal events, and report counts reconcile.
  - A process that exits without a completed run is an incomplete failure, even
    if every event it sent was a success.
- **2d.23** (final): The portable engine has no SBT implementation dependency.
  Forked JVM tests execute in the test JVM, and JS and Native run their actual
  target programs, not JVM evaluation of the specifications.

## 2e: JS and Native target integrations

- **2e.1** (final): The base runner takes over the `fundamentals-*-test` JS and
  Native lanes.
- **2e.2** (final): The runner layers link on Native and pass their own tests on
  2.12, 2.13, and 3.9.
- **2e.3** (final): Native engine fixtures execute Identity, ZIO, and Cats Effect
  bodies with DI and configuration, shared lifecycle acquisition and release,
  assertion failures, and finalizer failures. Linking effect libraries alone is
  insufficient.
- **2e.4** (final): The linker retains selected suites.
- **2e.5** (final): Items 2d.1–2d.23 hold on JS and on Native, with real Node and
  Native process fixtures, under the scope rule above.
- **2e.6** (final): With a target-side framework, serialized tasks reconstruct
  correctly.
- **2e.7** (final): Async JS completion and target shutdown preserve events and
  finalization.
- **2e.8** (final): JS and Native use serializable test and application
  identities and reconstruct executable state in the target runtime. Nothing
  blocks the JS event loop waiting for effects. Each sharing group runs within
  one execution process, unless an explicit shard accepts the loss of resource
  sharing.

Browser JS follows the plan's "Browser JS" open decision.

## 3: coverage integration

- **3.1** (final): A fixture with known executed and unexecuted branches
  produces the expected report under each claimed combination. The claimed
  combinations follow the plan's coverage table:
  - Claimed: JVM on Scala 2.12 and 2.13, including forked runs and multiple
    modules.
  - Claimed: JVM on the Scala 3 compiler the build uses (3.9.0 after 2a),
    including the assertion macro fixtures. If Scoverage fails there, the status
    ledger records a minimal reproduction and the item is waiting on owner.
  - Claimed only if validated: JS and Native on Scala 2, where the documented
    Scoverage support must validate with the chosen toolchain, runtime, and
    output transport. The status ledger records the validation result either
    way.
  - Not claimed: JS and Native on Scala 3.
- **3.2** (final): Instrumentation is absent from published normal artifacts.

## 4: IntelliJ integration

The adapter is an IntelliJ plugin built with the IntelliJ Platform Gradle Plugin.
It targets the IntelliJ IDEA release that matches the Scala plugin branch the
plan cites (`idea262.x`). Headless tests in the IntelliJ Platform test framework
verify each item; the status ledger records the exact IDE, plugin, and Gradle
versions. If an item cannot be automated after a recorded attempt, it is
*waiting on owner*, with manual verification steps in the status ledger, until
the owner confirms it.

- **4.1** (final): Running a suite and running a single test from the plugin's
  run configurations shows the same test IDs and outcomes in the structured
  test tree as CLI and SBT runs.
- **4.2** (final): A failure navigates to its source location.
- **4.3** (final): Rerunning failed tests executes exactly the failed tests.
- **4.4** (final): Cancelling a run stops it, and the tree reports it as
  cancelled.
- **4.5** (final): A debugger session on a JVM test stops at a breakpoint in the
  test body.
- **4.6** (final): Items 4.1–4.5 use the same IDs and settings as CLI and SBT.

## 5: repository-wide migration and ScalaTest retirement

- **5.1** (final): No current published module or repository test dependency
  contains `org.scalatest` or `org.scalactic`.
- **5.2** (final): No module's tests use a runner layer that depends on that
  module, and such tests live in a test-only project above that layer.
- **5.3** (final): Migrated suites reference neither package.
- **5.4** (final): Each migrated module discovers, executes, and reports the same
  selected tests. Per-module suite and test counts recorded before migration
  equal those after it, or the status ledger lists every difference with its
  reason: a removal the plan's inventory permits, or an owner-approved change.
- **5.5** (final): No legacy adapter remains. A temporary one exists only during
  the staged migration.
- **5.6** (final, supersedes 1a.6): Every shared test suite that runs on JS also
  passes on Native through the new runner, or sits in a platform-specific source
  set with its reason recorded.
- **5.7** (final, supersedes 1a.8): The 0b engine checks run as Native tests
  through the new runner, from a test-only project above `distage-testkit-core`.
- **5.8** (final): Every facility in the plan's migration inventory has its
  replacement.
- **5.9** (final): User-facing documentation describes the new library and
  runner. That covers the microsite's `distage-testkit.md` page and the other
  pages that mention ScalaTest. The installation and migration documentation
  states that the distage SBT plugin is required for SBT use, and L4 passes.

## Other requirements

The implementing agent adds items O.1 onwards here before the first commit of
step 1a, as the "Other requirements" rule specifies.
