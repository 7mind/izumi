# Base test runner

`distage-test-runner` provides plain `AnyWordSpec` and `AsyncWordSpec` suites
with `should`/`must`/`can`/`in`. It inherits the fundamentals assertions and
accepts synchronous or Future test bodies. Registration stores bodies without
evaluating them; compiler macros record source positions separately from IDs.
Nested branches restore their parent path. Registration freezes when a session
discovers the suite. Concurrent discovery of a shared plain or async suite
admits one session; the others retain an explicit Discovery failure.
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
Each event consumes its sequence before the external sink is called. A sink
that records an event and then throws cannot cause the next event to reuse that
sequence. Failed event delivery is not retried.

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
spent. Projected host exceptions retain the protocol failure's causes, ordered
suppressed children and explicit field-capture diagnostics. The host Throwable
API has one primary cause; additional protocol causes retain the bootstrap's
existing projection as suppressed siblings. The projected exception also retains
its complete Failure record.

The independent JVM framework consumer compares actual body records with JUnit
test identities under SBT 1 and 2. The status ledger records the exact scope and
results. Rich assertion wire mapping, transports and host plugins remain
subsequent implementation.

## Distage environment ownership

`distage-testkit-core` exposes stateless `TestEnvironmentFactory` construction
and `SessionTestEnvironment`, whose cache belongs to one owner. Constructing the
owner loads no plugins and provisions no dependencies. Loading an environment
preserves module definitions without executing their providers.

Requests include configuration, roles, effect tag, default module, plugin loader
and merge strategy. The executable collaborators use reference identity; equal
but distinct loaders or merge strategies retain their own definitions. Repeated
requests within one owner share their environment, while independent owners
load separately. Failed construction propagates its original exception and does
not populate the cache.

The environment owner snapshots both application and bootstrap plugin requests
before retaining its configuration key or invoking the factory. Those snapshots
use the same `PluginConfig.snapshot()` policy as the plugin loader.

The production-loader and in-memory-loader fixtures exercise static modules,
including concurrent requests on JVM and JS. `SessionPluginLoader` takes a loader
factory and supplies its own `PluginPackageCache`:

```scala
new SessionPluginLoader(cache => PluginLoaderDefaultImpl.withPackageCache(cache))
```

The factory must construct its loader with the supplied cache and remain
declarative. The owner caches complete requests separately from package scans.
Both cache boundaries snapshot their sequence inputs into immutable vectors,
including request overlays, so Scala 2.12 mutable sequences cannot alter stored
keys or request-owned payload fields after loading.
Identical package names, scanner whitelists and exclusion sequences reuse scanned
definitions within that owner, even when request overlays, debug settings or
enabled-package combinations differ. Each request keeps its own merges and
overrides. Parent/child package names and changed exclusion sequences retain
separate scan boundaries. This preserves the existing memoization behavior of
function and resource providers. Independent owners scan fresh class instances.
Uncached requests always delegate; failed complete loads retain their original
exception and may be retried. A successful package scan remains cached when a
later step fails, as with the legacy package cache.

The loader's ordinary `load` and protected `scanClasspath` methods retain virtual
dispatch, including through `map`. The default loader retains its zero-argument
primary constructor for existing DI wiring; its private owned implementation
receives the package cache explicitly. JVM fixtures exercise actual planner and
resource acquisition/release alongside custom loading policies. JS supports
static modules and rejects package scanning through its existing backend.

The higher front end uses these owners through its suite hooks, as described
below. Its import-only compatibility gate remains subsequent work. The legacy
`DistageTestEnv` delegates construction to the same factory and retains its
existing global cache policy. The legacy default loader's package cache policy
also remains unchanged.

The core DistageTestRunner exposes planning separately from execution.
`plan(tests)` returns a PreparedRun containing the existing typed planning result;
`runPrepared(prepared)` executes those trees without loading configuration or
planning them again. The value is bound to its creating runner and can start once.
Foreign-owner and repeated execution fail before scope reporting. Keep the
runner's surrounding lifecycle open through execution: bootstrap injectors in
the plan retain that runner's locator. These values are in-process engine state;
the protocol carries descriptions and identities instead.

Planning executes configuration loading and bootstrap/planning extensions, while
leaving application resource providers and test bodies suspended. Abandoned plans
acquire no application resources. Execution retains the existing body, provisioning
and finalizer failure behavior. A finalizer failure propagates and prevents a
successful scope completion. The legacy `run(tests)` still begins its reporting
scope before planning.

## Distage execution provider

`distage-testkit-runner` adds `DistageExecutionProvider` to the base runner's
provider contract. Registration retains callbacks; resolution constructs the
selected environments; planning uses the core PreparedRun; execution reports
the selected logical IDs and awaits the existing engine's resource release.
The provider receives its execution context, configuration loader and runner
options explicitly. It owns registration, environment/plugin caches and
reporting state. The outer runtime uses the supplied context.

The instance-only engine parent supplies the effect tag, effect operations and
reporter without acquiring an additional runner lifecycle. The owned default
bootstrap disables process-global logging-router installation. A suite's custom
bootstrap factory is preserved; arbitrary custom hooks still require an
isolation audit.

NonFatal reporting callback exceptions are recorded immediately as Transport
failures. Reporting callbacks then return normally, allowing selected test
bodies and finalizers to settle under their existing effect semantics. The
provider outcome remains unsuccessful and retains independent finalizer
failures. This policy avoids letting a transport exception suppress a lifecycle
exception in an effect whose bracket keeps only its use failure. Reporter
identity and ordering invariants still throw; they are outside callback
recovery. Precondition skips emit the protocol's failure-free Skipped result.
An independent `InterruptedException` thrown by an Identity body follows ordinary
error recovery and emits one Test-phase failure for that logical identity.
The interruption reporting hook wraps ordinary recovery, so a recovered body
exception does not generate an additional terminal report.

MiniBIOAsync defers pending interruption inside a mask and restores the enclosing
interruption mode when that scope ends. `bracketCase` acquisition and release run
under a mask; an asynchronous release completes before its bracket exits. Owned
interruption bypasses ordinary sandbox recovery. The existing UnsafeRun2 APIs
retain `Exit.Interruption`; direct execution methods retain their public
`Exit.Uninterrupted` signatures by projecting interruption to Termination.

Cancellation requested before execution acquires no application resource;
active interruption remains subsequent work.

## Distage spec front ends

`izumi.distage.testkit.runner.spec` supplies `Spec1`, `Spec2`, `SpecZIO` and
`SpecIdentity`. They retain `should`/`must`/`can`/`in`, Functoid dependency
parameters and deferred effect construction. Spec2 retains typed-error
projection; SpecZIO also constructs its required environment through DI.
Nested branches restore their enclosing registration path, and `skip` leaves
its argument unevaluated.

Pass fresh suite factories to the same `RunSession` used for plain suites.
Concurrent discovery of the same distage suite instance admits one session;
the other retains an explicit Discovery failure. Duplicate paths, suite IDs
and test IDs also reject during discovery without evaluating configuration or
bodies. A rejected discovery is retained without repeating suite factories.
Discovery invokes declarative registration and stores positions and structured
paths. Suite constructors must keep application side effects inside registered
bodies or providers; arbitrary constructor side effects execute when the
factory runs. The library's registration path leaves configuration, plugin
loading, effects, test bodies and application resources suspended.

Selected resolution evaluates the suite configuration and its environment
hooks. `makeTestEnv`, `loadRoles`, `makeMergeStrategy`, `makePluginloader`,
`loadEnvironment` and `makeEnv` retain virtual dispatch. The default route
evaluates roles, merge strategy and loader in that order and then uses the
provider's environment owner. A suite retains its environment success or
original Planning failure as one lazy snapshot; multiple selected tests do not
repeat a failed hook. Custom hooks keep their explicit collaborators and
definitions.

App and bootstrap plugin requests carry the provider's package-cache owner.
The verified forwarding, mapped and direct synchronous reconstruction routes
share compatible scanned definitions inside a session and get fresh definitions in another
session. PluginConfig copy, snapshot and request helpers retain ownership while
preserving its six-field value shape. Explicit custom package-cache collaborators
retain their policies. The invocation scope preserves custom dispatch and allows
a shared built-in loader instance to serve distinct owners. An opaque wrapper
that reconstructs before forwarding to a hidden delegate, or a hook that hands
a reconstructed request to a worker thread, still reaches the global cache.
These reproduced ownership defects and the complete custom-hook audit remain open.

The built-in testkit `BootstrapFactory.Impl` keeps its router local to the
test environment. This also applies when a custom factory delegates to it.
Testkit no longer reads the role-app static-router property to install a
process-global router. Custom factories retain their configuration/module
hooks and definitions; role-app modules retain their explicit static setup
policy. A hook that explicitly mutates global state still requires an ownership
audit.

Effective activation uses the existing core policy: suite choices override
loaded configuration, and explicit run choices override those suite choices.
Explicit axes and filters are validated against available definitions before
application provisioning. Filtering uses the resolved choices, and resolution
and execution retain the same logical IDs. Disabled memoization removes the
cross-test memoization roots while preserving dependency sharing within each
test graph. Effective settings and configuration snapshots use the original
environment's reference identity, preserving the full input to custom loaders
and the suite/test/debug policies excluded from core structural equality.
The provider binds each effective environment to its retained AppConfig before
planning and includes that config in the environment's grouping inputs.
Resolution and DI therefore consume the same loaded snapshot. Successful
snapshots are retained; failed loads propagate their original exception, and
the effective-resolution cache retains each failed request without automatic
retry.

The runtime fixtures cover all four entry points on JVM and JS. The higher
test project also retains the legacy autoset suite and the Identity, Cats IO
and ZIO sequential-ordering suites with only the Spec1 import changed. Their
compatibility harness checks all 46 logical identities and runs fresh suite
instances twice on every supported JVM/JS compiler lane. The complete
compatibility inventory and final-head checks remain pending.

## Fundamentals test projects

Platform, BIO, collections, json-circe and language tests live in unpublished
`fundamentals-*-test` projects, with their original Scala packages, source
variants and resources. These projects depend on the production modules through
their Test configuration. Production modules do not depend on the test projects.
Distage core and extension-plugins import platform test helpers through a
Test dependency on `fundamentals-platform-test`.

`fundamentals-test-support` is an unpublished platform-specific front end.
Its JVM `izumi.fundamentals.testkit.AnyWordSpec` extends the base runner;
its JS and Native variants extend ScalaTest during the staged migration to
step 2e. Five collections suites and the language suite use this import.
The JVM test projects register both frameworks and send their arguments to
the corresponding framework explicitly. Suites using additional ScalaTest or
Scalactic facilities stay on the legacy framework pending their replacement.

Run the moved suites with, for example,
`fundamentals-collections-testJVM/testFull`,
`fundamentals-collections-testJS/testFull`, or
`fundamentals-collections-testNative/testFull`. The test projects retain the
production modules' supported compilers and platforms. BIO remains JVM/JS
pending the released Native interop artifacts. Circe's Scala 2 derivation tests
remain JVM/JS because their dependency has no Native artifact.
