# Published JVM host sharing fixture

This Linux process fixture measures the target bootstrap under SBT 2.0.9.
Two plain suites and three SpecIdentity suites each declare three tests
with equal display paths across suites. The DI suites discover a scanned plugin
and share a memoized Lifecycle resource. Every body, acquisition and release
creates a physical record with CREATE_NEW. The verifier compares exact body
identities, resource IDs and JUnit identities, rejecting failure, error and
skipped reports. Records are preserved before the next request.

The plugin declares production and dummy repository bindings. The default
activation selects production; resource IDs identify the selected binding so
the plugin consumer can verify activation overrides through physical records.

The concrete suites use separate source files and distinct marker methods so
SBT 2 can produce distinguishable stock suite digests. Marker methods are fixture
inputs to static history; test bodies and the resource-sharing contract are
unchanged. The plugin consumer checks the distinct-digest precondition before
its incremental cases.

Twelve cases cover full/explicit selection, repeated requests in one process,
sequential/default scheduling, wildcards, exclusions, a separate foreign control
framework, host thread limits one/two, and forked selection/full/two-group runs.
The target bootstrap is registered directly; the mixed-framework case adds its
foreign control. There is no host framework substitution. An explicit two-group
fork keeps SuiteC separate from SuiteD/SuiteE and requires two resource lifetimes.

After publishing the runner's dependency closure, run from the repository root:

```sh
python3 test-fixtures/host-sharing-consumer/verify-matrix.py \
  --artifact-version 1.3.0-SNAPSHOT \
  --sbt-version 2.0.9 \
  --scala-version 3.9.0 2.13.18 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/host-sharing-example
```

Each lane uses a fresh independent build and SBT 2 cache directory. The evidence
directory must be new. These are Behavioral-Active, Effectual,
Good-Communication checks of actual host processes. This measurement fixture
does not complete the SBT plugin, individual-test selection, incremental
invalidation, streaming, multi-project/configuration or failure/recovery
requirements. The production SBT integration still requires the distage plugin.

`verify-counted-forks.py` uses the published distage plugin and built-in framework
with five DI suites. Each SBT process runs a successful baseline, an exit-zero
halt after all fifteen bodies and resource release, then incremental recovery
and repeat without settings reapplication. The halt must produce an explicit
host task failure. Positive runs require exact fifteen-case XML results. All
four commands must use distinct receipt directories and clean them up.

```sh
python3 test-fixtures/host-sharing-consumer/verify-counted-forks.py \
  --artifact-version 1.3.0-SNAPSHOT \
  --sbt-version 2.0.9 \
  --scala-version 3.9.0 2.13.18 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/counted-forks-example
```

Publish the current JVM protocol, base runner, both SBT plugins and their
dependency closure first. Each run requires a new evidence directory. This
fixture verifies owned fork completion and same-process command recovery;
held delivery, SDK task-error replacement, mixed frameworks, comprehensive
history, cancellation and cleanup faults require separate controls.

`verify-held-forks.py` checks a real held host event batch with five DI suites.
A public framework forwards the production runner and tasks, recording entry
and return from the child runner's `done`. A public listener holds the first
three-event suite batch. The driver releases that callback only after it records
all fifteen physical bodies, the paired DI release, an empty host receipt
directory, and a live child inside `done` with no completion receipt. It then
checks all fifteen XML cases and repeats the command in the same SBT process
with normal delivery, fresh resources and a new cleaned receipt directory.

```sh
python3 -B test-fixtures/host-sharing-consumer/verify-held-forks.py \
  --artifact-version 1.3.0-SNAPSHOT \
  --sbt-version 2.0.9 \
  --scala-version 3.9.0 2.13.18 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/held-forks-example
```

The hold mode changes through an explicit fixture input file, without settings
reapplication. Each lane freezes the held observation before opening its gate.
The fixture never publishes an acknowledgement. Its forwarding framework
changes only completion instrumentation and public framework argument routing;
production execution and receipt waiting remain delegated. This control does
not establish a complete SDK logger drain, task-exception reporting, mixed
frameworks, history, cancellation, cleanup faults or final runner acceptance.

`verify-mixed-forks.py` executes five DI suites together with the unchanged
foreign control framework. Each command must execute eighteen distinct bodies
and return the same six suites and eighteen successes through SBT's public
`Tests.Output` and JUnit XML. The foreign bodies retain their plain resource
marker, and the DI bodies share one recorded Lifecycle. A repeat in the same
SBT process must use a fresh DI resource and cleaned command receipt directory.

```sh
python3 -B test-fixtures/host-sharing-consumer/verify-mixed-forks.py \
  --artifact-version 1.3.0-SNAPSHOT \
  --sbt-version 2.0.9 \
  --scala-version 3.9.0 2.13.18 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/mixed-forks-example
```

The helper reuses the held fixture's public completion instrumentation while
forwarding production tasks without a delivery gate. It preserves the foreign
framework's source and registration. A public result logger records and checks
the SDK output before invoking the inherited logger. This bounded normal
mixed-framework control does not establish held foreign delivery, a complete
logger drain, all framework orders, cache history, failures or cancellation.

`verify-global-exit-ack.py` is a public JVM prototype for the measured SBT2 held
foreign-output omission. It forwards the production Runner/Tasks and installs
a shutdown hook owned by each child's session. Public `testGrouping` supplies
the declared fork membership through `ForkOptions.runJVMOptions`; public ordered
selection predicates record the effective admitted suites after configured
filters. A public listener acknowledges each fork's selected groups and waits
for its child to acknowledge that set before the final group callback returns.
Prototype files are separate from the production owned-suite receipts. The
original foreign framework stays unchanged; no SBT private API or orchestration
is substituted.

```sh
python3 -B test-fixtures/host-sharing-consumer/verify-global-exit-ack.py \
  --artifact-version 1.3.0-SNAPSHOT --sbt-version 2.0.9 \
  --scala-version 3.9.0 2.13.18 --framework-order own-first \
  --evidence-dir /srv/nvme/tmp/izumi-impl/global-exit-ack-example
```

Use a separate evidence directory for `--framework-order foreign-first`.
The controls cover all/partial selection, configured exclusion, repeated ordered
patterns, two fork groups and a separate foreign-only group within a mixed
command. Both framework orders pass those controls on all three Scala versions.
An empty fork group and an entirely user-excluded selection additionally pass on
Scala 3.9: they start no child and receive no acknowledgement. Each command checks
physical identities, public SDK results, positive XML, fresh DI resources and
command receipt cleanup. A held observation checks only its fork's required
bodies; another fork may start later.

While held, that fork's child is alive and has not acknowledged completion.
Own-first enters shutdown after owned done finishes. Reverse order waits in owned
done when that group includes owned suites; a foreign-only group has no owned
tasks to await. Each finishes after the foreign callback is released.

The listener records a missing child acknowledgement before throwing. The public
result task also checks that record and each active fork's child/host completion
markers: SDK2 can swallow a listener exception while returning successful body
results. `verify-ack-recovery.py` runs a baseline, a controlled `halt(0)` after
host acknowledgement but before child-ready, and recovery in one SBT process:

```sh
python3 -B test-fixtures/host-sharing-consumer/verify-ack-recovery.py \
  --artifact-version 1.3.0-SNAPSHOT --sbt-version 2.0.9 \
  --scala-version 3.9.0 --framework-order own-first \
  --evidence-dir /srv/nvme/tmp/izumi-impl/ack-recovery-example
```

Use another evidence directory for each Scala version or framework order. The
fault command must return a failed public input-task result with
`INCOMPLETE_DYNAMIC_FORK_ACKNOWLEDGEMENT`; the expected-failure harness then lets
the batch continue to recovery. Its outer process exit zero records successful
rejection and recovery, while all eighteen body outcomes remain successful.
This does not provide a structured run-error or failed XML projection.

This is a prototype, with disjoint suite membership and stock SDK2 fork scheduling.
Production integration, foreign-only commands, cache history, overlapping group
membership, cancellation, premature death, cleanup faults and complete
error/logger drain remain open. Files publish through a temporary file and atomic
move; a paused-writer contract check is still outstanding. SBT1 uses a different
completion handshake and is excluded from this prototype.

`verify-registration-linkage.py` uses the published plugin and unmodified target
bootstrap to exercise a `NoClassDefFoundError` during suite registration. Two
selected suites must each receive one error in public SBT results and JUnit XML,
with the missing-dependency class and message retained. The failed commands
execute no bodies or DI resources. Between two fault commands, a healthy suite
executes three bodies with one paired DI resource in the same SBT process. Each
command uses a fresh target PID and removes its owned receipt directory.

```sh
python3 -B test-fixtures/host-sharing-consumer/verify-registration-linkage.py \
  --artifact-version 1.3.0-SNAPSHOT --scala-version 3.9.0 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/registration-linkage-example
```

Use a separate evidence directory for Scala 2.13.18. Publish the current JVM
runner and plugin dependency closure first. The outer process exits zero after
the harness observes two failed public `testOnly` results and healthy recovery.
This fixture covers registration linkage failures and their diagnostic text;
complete structured reporting, other failure phases, cancellation and mixed
framework completion require their separate controls.
