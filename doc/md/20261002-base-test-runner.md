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
results. The distage execution provider/front end, rich assertion wire mapping,
transports and host plugins remain subsequent implementation.

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

The higher execution provider and its import-only compatibility gate remain
subsequent work, including the existing `makePluginloader(): PluginLoader` suite
hook. The legacy
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
