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
macros. The status ledger records actual verification and scope: fundamentals
test-only projects, the distage execution provider/front end, rich assertion
wire mapping, transports and host adapters remain subsequent implementation.
