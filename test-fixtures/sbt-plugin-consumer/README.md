# Published SBT plugin process fixture

The driver copies the existing host-sharing fixture into independent builds,
loads the published distage plugin, and uses its framework registration and
identity arguments. Real scanned-plugin acquisition reads an owned external
UTF-8 input. Repeated stock incremental commands must rerun distage bodies and
materialize the latest input, while a separate foreign framework retains stock
cache skips. Body IDs, served resource IDs, paired lifetimes and JUnit identities
are checked after each run. Configuration filters and a custom test configuration
exercise the same public plugin settings.
SBT 2 verifies five distinct public stock suite digests before running these
cases; a missing or aliased digest fails the fixture.
The combined matrix has 47 scenarios per compiler. It edits a scanned plugin
implementation, proves that all six stock suite digests remain unchanged, and
checks that `test` and `testQuick` still rerun and use the edited implementation.
A suite-class edit must change its own digest while preserving the foreign
suite's digest. Both incremental commands then execute the expected bodies.
Additional quick runs change external input, activation and memoization, and
follow a partial selection with the complete suite set.
An application constructor failure, in process and forked, must produce exactly
one construction attempt and one error report for each of three selected suites,
without executing bodies or acquiring resources. A subsequent incremental
command in the same session must execute all five suites with fresh resources.
Public discovery/fingerprint checks in both execution modes require exactly
the five owned suites and one foreign suite, one owned framework registration,
and no execution fingerprint for the containing application.

Additional controls inspect exact IDs without body execution or acquisition,
select one DI body through framework JSON arguments, and verify the subsequent
incremental request still runs the whole suite. Disabling memoization gives
three distinct paired lifetimes. Inspection and individual selection also run
with forks and the custom configuration. Empty cases retain directory snapshots.
The selected body overrides the repository axis to dummy and supplies a matching
filter; its recorded resource ID must identify the dummy binding. A dummy filter
against the default production activation must produce one suite error and no
body or resource records. The next incremental command in the same SBT process
must execute the whole suite, both in process and with forks.
An inspection-only configuration supplies a constructor sentinel through its
Java options. Its list/plan child PIDs must differ from SBT's PID, where the
sentinel is explicitly absent.

Run after publishing the SBT 2 plugin and the runner closure:

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-matrix.py \
  --artifact-version 1.3.0-SNAPSHOT \
  --sbt-version 2.0.9 \
  --scala-version 3.9.0 2.13.18 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/sbt-plugin-example
```

These are Behavioral-Active, Effectual, Good-Communication tests of real SBT
processes. A lane deadline signals its owned process group and waits for the
launched SBT process. This
fixture does not prove the complete incremental-input model, the complete
activation/input domain, multi-project aggregation or
resource-failure/cancellation recovery.

`verify-multi-project.py` runs ten scenarios per compiler across two aggregated
modules and their Test and Integration configurations. Each configuration has
its own target directory. Full, selected, incremental and quick commands check
body identities, public results, JUnit identities, fresh worker processes and
paired memoized resources. Inspection checks module/configuration identities
without execution. Inherited user result loggers exercise configuration routing.

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-multi-project.py \
  --artifact-version 1.3.0-SNAPSHOT --scala-version 3.9.0 2.13.18 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/multi-project-capture
```

`verify-host-limits.py` runs five compatible DI suites with SBT host task limits
of one and two. It checks fifteen physical bodies and matching JUnit identities,
one shared paired resource lifetime per command, and a fresh lifetime on repeat.
The first returning suite task must observe all fifteen body files; host group
start/end records retain the actual concurrency maximum. Three bodies in each
suite wait for one another with a finite deadline; start receipts must identify
three distinct threads before they complete. The listener checks every exact
test identity and rejects duplicate or omitted events. These JVM controls do
not establish cancellation or multi-project behavior.

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-host-limits.py \
  --artifact-version 1.3.0-SNAPSHOT --sbt-version 2.0.9 \
  --scala-version 3.9.0 2.13.18 --host-threads 1 2 --fork false \
  --evidence-dir /srv/nvme/tmp/izumi-impl/host-limits-capture
```

Run `--fork true` in a separate capture for forked execution. Each body-start
receipt includes the target PID; it must match the listener's host PID in
process and differ with forks. Both modes require three overlapping bodies on
distinct threads per suite, all fifteen exact listener/XML identities and fresh
paired shared resources on repeat. The host listener window observes report
delivery; forked target task concurrency is controlled separately by SBT's
worker executor.

`verify-streaming.py` holds one body while its sibling finishes. Before releasing
that body, it requires both start frames and the sibling completion frame in the
public `Test / distageEventDirectory` channel. The default directory is
`Test / target` followed by `distage-events`; other enabled configurations use
their corresponding scoped keys. Each application run creates a UUID-named
`.jsonl` file containing schema-4 protocol envelopes, flushed after each frame.
After release, the fixture reconciles exact test identities, event sequence,
terminal outcomes and JUnit cases. It covers both in-process and forked JVM
execution; it does not establish IDE or JS/Native transport behavior.

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-streaming.py \
  --repo-root . --artifact-version 1.3.0-M5-SNAPSHOT \
  --scala-version 3.9.0 --fork false \
  --evidence-dir /srv/nvme/tmp/izumi-impl/streaming-example
```

Run separate captures for Scala 2.13.18 and `--fork true`. These are
Behavioral-Active, Effectual-GoodCommunication controls of actual SBT processes.
The existing framed memory/filesystem contracts cover the channel encoding and
failure behavior below this process boundary.

`verify-resource-failures.py` runs five DI suites of three tests, sharing one
memoized Lifecycle. Acquisition, assertion, unexpected body and shared release
failures each precede a complete incremental `test` or `testQuick` recovery
command in the same SBT session. It verifies physical IDs, paired lifetimes, process ownership, fresh
run/resource IDs, structured failure phases and exact XML outcomes. Run each
supported compiler in a fresh capture:

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-resource-failures.py \
  --repo-root . --artifact-version 1.3.0-M5-SNAPSHOT \
  --scala-version 3.9.0 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/resource-failure-example
```

Each capture runs eighteen commands across in-process and forked modes. These
Behavioral-Active, Effectual-GoodCommunication controls establish explicit
same-session incremental recovery for these failure modes; cancellation and
the complete input/caching domain remain separate acceptance requirements.

`verify-setup-cleanup.py` compares a stock custom framework and the published
distage runner in two projects in one SBT session. Normal, setup-failure,
cleanup-failure and recovery cases run with both fork settings. Callback PID,
caller loader and loaded class identity are compared with physical body receipts.
Body guards require setup before execution and cleanup after the body effects.
Both paths must preserve exact body XML and public command success/failure.

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-setup-cleanup.py \
  --repo-root . --artifact-version 1.3.0-M5-SNAPSHOT \
  --scala-version 3.9.0 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/setup-cleanup-example
```

Run Scala 2.13.18 separately. Each compiler runs twenty controls. These are
Behavioral-Active, Effectual-GoodCommunication tests. Fork callbacks run in SBT
with its host loader; test bodies run in the target process. These controls do
not establish the entire classloader-lifetime or selected-outcome reporting
contract after host setup/cleanup failures.

`verify-partial-delivery-cancellation.py` cancels a real SBT client command while
the second host test-event callback is held, after the first suite report has
completed. Serial suite tasks make that boundary deterministic. All fifteen
test bodies and their shared resource have completed before cancellation, so
the target outcome and XML retain successful test results while the SBT command
reports cancellation. The original command must stay pending until the held
callback returns. Exact start/event/end callback identities reject omissions,
duplicates and callbacks after the command response. A subsequent command in
the same session must execute fifteen bodies with fresh run/resource IDs.

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-partial-delivery-cancellation.py \
  --repo-root . --artifact-version 1.3.0-M5-SNAPSHOT \
  --scala-version 3.9.0 --execution-mode both \
  --evidence-dir /srv/nvme/tmp/izumi-impl/partial-delivery-example
```

Run Scala2.13.18 in a separate capture. Each compiler checks cancellation and
recovery in both execution modes. These are Behavioral-Active,
Effectual-GoodCommunication controls of owned suite reporting; foreign-framework
and repeated explicit-group cancellation require separate controls.

`verify-repeated-fork-cancellation.py` runs two concurrent fork groups containing
the same five owned suites. It checks thirty selected outcomes and thirty
start/event/end callbacks per command, with two distinct target processes and
two independent shared resource lifetimes. A real client cancellation holds both
finalizers before permitting release; the command must remain pending, publish
thirty explicitly cancelled selected XML cases, and recover in the same session.
The fixture also rejects callbacks after command completion.

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-repeated-fork-cancellation.py \
  --repo-root . --artifact-version 1.3.0-M5-SNAPSHOT \
  --scala-version 3.9.0 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/repeated-fork-example
```

Run Scala2.13.18 in a separate capture. These are Behavioral-Active,
Effectual-GoodCommunication regression controls; mixed foreign-framework
cancellation requires separate validation.

`verify-mixed-client-cancellation.py` completes three tests through their original
foreign framework before cancelling an active owned body in the same execution
group. It checks that the foreign body IDs, event fields and successful XML
outcomes remain unchanged, while all fifteen owned selected outcomes are
explicitly cancelled. A held owned finalizer keeps the command pending, and a
subsequent command recovers with fresh resource and run identities.

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-mixed-client-cancellation.py \
  --repo-root . --artifact-version 1.3.0-M5-SNAPSHOT \
  --scala-version 3.9.0 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/mixed-cancellation-example
```

Run Scala2.13.18 separately. Each compiler checks normal execution, cancellation
and same-session recovery in process and forked. The foreign framework precedes
the owned framework because SBT's fork runner completes each framework's tasks
before starting the next. These Behavioral-Active, Effectual-GoodCommunication
controls cover already completed foreign tests; cancellation during an active
foreign test requires separate validation.

`verify-selection-reasons.py` verifies distinct stock suite digests, warms the
foreign success cache, and exercises cached skips, negative request patterns,
configured exclusions and filters, multiple includes and ordered filter unions.
The public log must distinguish `cached-success`, `user-request` and
`user-configuration`. Each command also checks physical bodies, resource
lifetimes and XML; excluded suites execute no bodies, and admitted suites must
have no exclusion record. Exact command markers keep similarly named cases
separate.

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-selection-reasons.py \
  --repo-root . --artifact-version 1.3.0-M5-SNAPSHOT \
  --scala-version 3.9.0 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/selection-reasons-example
```

Run Scala2.13.18 separately. Each compiler checks thirteen commands in process
and thirteen with forks. These Behavioral-Active, Effectual-GoodCommunication
controls verify the named selection cases; opaque inherited predicates and the
complete extension/input inventory remain separate acceptance requirements.
