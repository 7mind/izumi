# Base test runner

`distage-test-runner` provides plain `AnyWordSpec` and `AsyncWordSpec` suites
with `should`/`must`/`can`/`in`. It inherits the fundamentals assertions and
accepts synchronous or Future test bodies. Registration stores bodies without
evaluating them; compiler macros record source positions separately from IDs.
Nested branches restore their parent path. Registration freezes when a session
discovers the suite, and a suite instance cannot register in another session.
Plain suites use the caller's implicit context for Future construction.
Async suites expose an overridable implicit `executionContext`. Its default is
a per-suite forwarding context that constructors may retain; scheduling through
it requires session registration and then uses that session's borrowed executor.

A `RunSession` receives its catalogue identity, suite factories, execution
context and event sink explicitly. Discovery invokes each factory once within
that session and validates its registrations. Repeated runs require new
sessions. The execution context is a borrowed dependency; its owner releases it
after session completion. The runner does not use a global executor.

`discover`, `resolve`, `plan` and `execute` separate registration, request
validation, provider planning and resource use. Unknown explicit identities,
stale catalogues, duplicate registrations and unsupported plain-suite axes
reject before provisioning. Plans belong to the session that produced them.
The approved per-provider test snapshot determines execution/report ownership.

An `ExecutionProvider` resolves and plans its registrations. Its execution plan
reports test starts/completions and phase failures through `ProviderEvent`.
Providers cannot emit session lifecycle events or supply a different run ID.
The returned Future must await all resources, finalizers and reports. The
session reconciles each provider's terminal results with its selected set and
reported completions before announcing `RunEvent.Finished`. Invalid reports
fail explicitly as transport errors. Equal failure records retain occurrence
counts, and summary reconciliation does not replay already reported failures.

Provider payloads undergo schema validation before reporting. Invalid payloads
are rejected with transport failures rather than forwarded. In-process schema
validation does not enforce serialized frame size; actual transports must apply
the protocol's encoding/framing limits. Empty run IDs produce failed Futures
before any registration, planning or event.

The base fixtures use controlled finalization gates and independent counters on
JVM/JS/Native. Separate builds consume the published jars and expand their
macros. On the JVM, those fixtures also exercise the public test-interface
bootstrap after releasing the base fixture executor.

The JVM bootstrap is `izumi.distage.testkit.runner.bootstrap.Framework`. Its
arguments require explicit `--build-id`, `--target-id` and `--catalogue-id`
values. At this implementation stage it accepts suite selectors; other selectors
reject explicitly. Each `Runner.tasks` group launches one session on its first
ordinary suite task. Inactive suites buffer their SDK events until their own
tasks execute, and active handlers receive serialized callbacks within that
task's lifetime. Separate groups instantiate fresh suites and own executors.

Tasks await engine finalization and owned-executor termination. Caller
interruption requests cancellation and waits for cleanup before propagating the
original interruption and restoring its flag. Callback errors disable that
handler, survive in later group projections as transport failures, and propagate
after cleanup. Cancellation observed during executor shutdown also survives in
the group outcome. `Runner.done` waits for active tasks and makes the runner
spent. Projected host exceptions retain the protocol failure's cause tree.

The independent JVM framework consumer compares actual body records with JUnit
test identities under SBT 1 and 2. The status ledger records the exact scope and
results. Fundamentals test-only projects, the distage execution provider/front
end, rich assertion wire mapping, transports and host plugins remain subsequent
implementation.
