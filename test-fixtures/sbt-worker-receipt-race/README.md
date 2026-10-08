# SBT worker result receipt reproduction

Current drivers run SBT 2 only. Recorded SBT 1 outcomes below are historical
controls from before the 2026-10-05 support decision.

This is a draft upstream defect report and an independent process reproduction,
using only the public test-interface Framework and SBT TestsListener APIs. The
upstream reproductions have no distage dependency, plugin or private SDK
replacement. A separate published-plugin verification is described below. Do
not publish the report automatically.

The framework emits exactly one intentional Error event. A host listener either
returns immediately or holds event processing for 1,200 ms. Both callbacks must
make the test command fail. The listener records the received group status and
SBT's final doComplete status; the driver also reads the retained JUnit XML. An
explicit completion latch keeps the enclosing process alive until event receipt
finishes, allowing late XML to be inspected.

Observed on JDK21, Scala3.9.0, fresh independent builds/caches:

| SDK | Callback | Actual test command/process exit | Listener group | doComplete | JUnit errors |
| --- | --- | --- | --- | --- | --- |
| SBT2.0.9 | immediate | 1 | Error | Error | 1 |
| SBT2.0.9 | held | 0 | Error | Passed | 1 |
| SBT1.13.0 | immediate | 1 | Error | Error | 1 |
| SBT1.13.0 | held | 1 | Error | Error | 1 |

The SBT2 held case additionally says there are no tests to run. Its successful
command result contradicts both the event and XML. No hypothetical lost event is
needed to establish this defect.

Run from the repository root with a new evidence directory:

```sh
python3 -B test-fixtures/sbt-worker-receipt-race/verify-reproduction.py \
  --evidence-dir /srv/nvme/tmp/izumi-impl/sbt-worker-receipt-example
```

The current driver returns0 only when both SBT 2 controls reproduce the table,
including the SBT2 defect. This is an expected-defect reproduction, not a passing
product acceptance check. It retains exact commands, build inputs, classpath
logs, actual exits, listener markers and XML. Captures
`2d-sdk-worker-receipt-race-reproduction-second/` and the named-constant replay
`...-third/` are recorded in the status ledger.

The pinned source provides a mechanism consistent with this controlled
experiment: ForkTests.React.notifyExit completes its promise successfully on
worker exit; mainTestTask immediately snapshots resultsAcc after waiting on that
promise. Event notifications arrive on another thread. WorkerProxy's watcher can
therefore release the snapshot before the held callback updates resultsAcc.
WorkerMain handles one request, flushes its reply and exits; process exit is not
an acknowledgement that host event processing finished. See the published
[actions source JAR](https://repo1.maven.org/maven2/org/scala-sbt/actions_3/2.0.9/actions_3-2.0.9-sources.jar)
and [worker source JAR](https://repo1.maven.org/maven2/org/scala-sbt/worker/2.0.9/worker-2.0.9-sources.jar).
The source/experiment supports this race in the tested domain; it does not
establish every possible SBT reporting failure's cause.

A separate real published-distage fixture encountered the same contradiction
after earlier commands: one selected SuiteC selection error is present in XML,
while the public testOnly command succeeds with zero reported errors. A direct
CLI replay preserves the contradiction without a fixture result wrapper. Those
captures are referenced in the ledger. Production correction and the complete
runner acceptance gate remain open; this reproduction changes no SBT pin.

`verify-exit-zero.py` isolates a second incomplete-success case. Its generic
framework delivers fifteen `Success` events to the target's public EventHandler,
writing one physical receipt after each call returns. The normal control returns
from Task.execute; the death control calls Runtime.halt(0) before that return.
Both execute in a JVM distinct from SBT, with no distage dependency or plugin.

| SBT 2.0.9 control | Target Success receipts | Actual test/process exit | JUnit cases |
| --- | --- | --- | --- |
| Task returns normally | 15 | 0 | 15 |
| Target halts before task completion | 15 | 0 | 0 |

The second row must be a failed incomplete run. A zero process exit code does
not establish task or run completion. The receipts establish delivery to the
target's EventHandler; the fork worker buffers events until Task.execute returns,
so this control does not claim that the host received those callbacks. The host
doComplete result is Passed, without any group/event notification in the halt
case. The driver returns0 only when it reproduces this false success and the
matching successful normal control.

```sh
python3 -B test-fixtures/sbt-worker-receipt-race/verify-exit-zero.py \
  --evidence-dir /srv/nvme/tmp/izumi-impl/sbt-exit-zero-example
```

In the pinned source, React.notifyExit resolves the response promise on exit0
without requiring a completed JSON-RPC response. ForkTests then snapshots the
accumulated results and closes the worker channel before unregistering its
listener. Unregistering shares a monitor with a currently executing callback;
it does not establish that queued, unread IPC notifications were processed.
That distinction also matters for the separate recorded normal DI baseline
failures. Their exact historical wire ordering was not captured.

`verify-task-error.py` isolates the fork worker's task-exception path. Its generic
task emits one `Success` through the target EventHandler, records that the call
returned, then throws `LinkageError`. The normal control returns from the same
task instead. Neither build uses distage. The SBT2 throwing command fails in
both captured reporting outcomes: no group/XML, or a complete suite-error
report. Both omit `doComplete`. The SBT1 rows retain the earlier captures:

| SDK | Control | Actual process exit | Target buffered successes | Host group / doComplete | JUnit cases / errors |
| --- | --- | --- | --- | --- | --- |
| SBT2.0.9 | normal return | 0 | 1 | Passed / Passed | 1 / 0 |
| SBT2.0.9 | task throws, report absent | 1 | 1 | absent / absent | 0 / 0 |
| SBT2.0.9 | task throws, report delivered | 1 | 1 | Error / absent | 1 / 1 |
| SBT1.13.0 | normal return | 0 | 1 | Passed / Passed | 1 / 0 |
| SBT1.13.0 | task throws | 1 | 1 | Error / Error | 1 / 1 |

```sh
python3 -B test-fixtures/sbt-worker-receipt-race/verify-task-error.py \
  --evidence-dir /srv/nvme/tmp/izumi-impl/sbt-task-error-example
```

The driver requires both SBT 2 controls and requires `doComplete` to be absent
on the throwing lane. If suite reporting arrives, it must contain exactly
start, one error event, end, and one JUnit suite error retaining the original
`LinkageError`; otherwise both callbacks and XML must be absent. Unexpected
partial reporting or an arriving `doComplete` fails the control. It does not
establish product acceptance. Each control uses a fresh
build and records its inputs, target and parent process identities, classpaths,
raw output, XML and actual exit. The target receipt proves buffering by the
worker's EventHandler; it does not prove delivery to the host.

In the pinned SBT2 source, ForkTestMain.testError sends a `forkError` notification
before runTest sends its replacement one-error `testEvents` batch. React handles
`forkError` by failing the response promise immediately; mainTestTask then closes
the worker and unregisters the listener. Its normal doComplete call is skipped.
This source ordering is consistent with the missing `doComplete`. The captures
contain no wire trace establishing the delivery ordering responsible for the
two group-reporting outcomes. SBT1's corresponding diagnostic does not abort event
processing before its replacement error batch in this control.

Tracker searches on 2026-10-04 did not identify an exact matching fork-worker
report among the returned results. [sbt/sbt#9667](https://github.com/sbt/sbt/pull/9667)
addresses an escaping LinkageError in TestRunner; the pinned fork worker has the
separate notification ordering described above. This reproduction remains an
unfiled draft report, with no SDK upgrade or private SDK replacement.

`verify-foreign-task-failure.py` exercises the published distage plugin against
the generic framework from `verify-command-groups.py`. It runs normal, throwing,
and immediate recovery commands in one SBT session, in process and forked, on
Scala 2.13 and 3. SuiteA emits three successes and then throws LinkageError;
SuiteB is an independent group. Before the public task guard, each throwing
command executes SuiteA's three bodies, loses its XML, and skips SuiteB. With
the guard, both suites execute once, Output and XML retain six successes and
one SuiteA error, and the command still fails with the original cause visible.
Every fork has exited before capture, and later commands use fresh workers.

```sh
python3 -B test-fixtures/sbt-worker-receipt-race/verify-foreign-task-failure.py \
  --artifact-version 1.3.0-SNAPSHOT --scala-version 3.9.0 2.13.18 \
  --expected-task-report error \
  --evidence-dir /srv/nvme/tmp/izumi-impl/sbt-foreign-task-failure-example
```

`--expected-task-report absent` checks the preceding plugin's reproduced
report loss. Only `error` checks the corrected product behavior. The fixture
reuses the generic framework source and build contract rather than introducing
another copy of the SDK host implementation.

`verify-held-batch.py` reproduces an incomplete SDK result after the host
listener has returned. One generic framework executes two three-body suites;
its child `Runner.done` waits for the first host group only. The host listener
either returns immediately or holds the second group until the driver releases
it. The result logger records public `Tests.Output` after the second callback,
group completion and `doComplete`, then rejects an incomplete result.

| SDK | Second callback | Executed / fresh XML cases | Public SDK result cases | Actual process exit |
| --- | --- | --- | --- | --- |
| SBT2.0.9 | immediate | 6 / 6 | 6 | 0 |
| SBT2.0.9 | held | 6 / 6 | 3 | 1, exact-result guard |
| SBT1.13.0 | immediate | 6 / 6 | 6 | 0 |
| SBT1.13.0 | held | 6 / 6 | 6 | 0 |

```sh
python3 -B test-fixtures/sbt-worker-receipt-race/verify-held-batch.py \
  --evidence-dir /srv/nvme/tmp/izumi-impl/sbt-held-batch-example
```

Driver0 requires both SBT 2 controls, including the SDK2 mismatch; it is not
product acceptance. There is no distage dependency or plugin. The six target
body files use CREATE_NEW and identify a JVM distinct from SBT. Each independent
build starts with fresh reports. The frozen held observation precedes gate
release; it does not claim that the parent command finished while held.

The published-distage mixed control observes the same boundary with an unchanged
foreign framework. Registering the distage completion control before the foreign
framework gives eighteen physical/XML cases and fifteen SDK result cases on
SBT2; reversing registration gives all eighteen. Both SBT1 orders give all
eighteen. These four observed controls do not establish every possible framework
order. Distage acknowledgements cover its five owned suites; they do not
acknowledge the foreign framework's later batch.

The pinned source is consistent with the retained result snapshot: the worker
watcher calls React.notifyExit independently of the notification monitor, and
mainTestTask copies resultsAcc immediately after its promise completes.
Unregistering subsequently waits for the held callback's monitor, so later XML
and callbacks can complete without changing that earlier immutable Output.
This is an inference from source and the controlled observations, not a wire
trace of the historical race. The examined WorkerExchange path chooses IPC
from JDK support and exposes no transport setting to its ForkTests caller.
No private transport override or production correction is included.

Tracker searches on 2026-10-04 for WorkerExchange test races, notifyExit tests
and forked 2.0.9 results did not identify an exact matching report among the
returned results; they do not prove that no report exists. This remains an
unfiled upstream draft and leaves the mixed delivery/result acceptance open.

This remains an unfiled upstream report. Tracker searches on 2026-10-04 for
`fork tests exit zero EOF success incomplete`, `"ForkTests" "Passed"`
and `"System.exit(0)" test`, restricted to `site:github.com/sbt/sbt/issues`,
did not identify an exact matching report in the returned
results; they do not prove that no existing report exists. The project's host
receipt guard rejects the recorded incomplete DI results. Its production
counted acknowledgement protocol passes bounded normal, worker-death, recovery
and held owned-delivery controls. Full mixed delivery and failure/reporting
acceptance remain open.

`verify-plugin-delivery.py` exercises the published distage plugin against the
unchanged generic framework from `verify-held-batch.py`. In one SBT 2 session,
it runs a normal control, holds the second batch and runs a normal recovery.
With `--expected-held-result complete`, every command must return all six cases,
matching the six physical bodies and positive XML cases in a fresh worker. The
command-boundary startup agent keeps that worker alive through callback delivery.
The driver also checks recovery and receipt cleanup. The explicit `reject` mode
retains the oracle for the earlier guard-only implementation, which returned
three SDK cases and rejected the held command; it is historical evidence.

```sh
python3 -B test-fixtures/sbt-worker-receipt-race/verify-plugin-delivery.py \
  --artifact-version 1.3.0-SNAPSHOT --scala-version 3.9.0 \
  --expected-held-result complete \
  --evidence-dir /srv/nvme/tmp/izumi-impl/plugin-delivery-example
```

Repeat with `--scala-version 2.13.18` and a new evidence directory. The independent
upstream reproductions above continue to run without the distage plugin.

`verify-command-groups.py` checks single and serial foreign groups, overlapping
suite names, exclusion of every selected suite, Runtime.halt(0), System.exit(0/1),
and same-session recovery. A second worker's shutdown hook is held after the first
worker halts or exits with zero: the public command must wait for the held worker's exit before
returning its failure. Captures verify that workers are alive during result
delivery, dead after the command, and have separate process identities. With
`--expected-overlap-report complete`, both repeated groups execute and public
Output and the suite's single XML file retain all six outcomes. The plugin
accumulates public group results and merges the stock XML at group completion.
`rejected` checks the preceding plugin's report-loss rejection. These are bounded checks, not
the complete cancellation, history or structured-error acceptance gate.

The earlier `System.exit(0)` control reproduced a deadlock in the command
handshake. The shutdown hook starts before the
SDK sends its reply, leaving the hook waiting for the host decision and the host
waiting for that reply. The exit capture distinguishes the pinned worker's
direct System.exit(0) from its top-level main from a premature exit. Only normal
completion waits for the host decision; a premature exit publishes a failure
and proceeds so the host can reject it. System.exit(1) retains SBT's original
nonzero-exit error and cancellation of the second serial group. The status
ledger retains the failing reproduction and subsequent checks.

The agent uses the project's pinned Byte Buddy through Java instrumentation to
capture the initiating thread in JDK shutdown. It does not depend on SBT private
classes. This couples the agent to JDK shutdown implementation; installation
fails immediately if the expected methods cannot be transformed.
`verify-exit-capture.py` runs a host-packaged agent from a captured successful
group against JDK17/21/25. Its 29 cases check normal commit/abort, premature
zero/nonzero exits, halt, natural termination, competing requests, and denied
requests on JDK17/21. Each command retains its arguments, output and exit markers.

```sh
python3 -B test-fixtures/sbt-worker-receipt-race/verify-command-groups.py \
  --repo-root . --artifact-version 1.3.0-SNAPSHOT --scala-version 3.9.0 \
  --expected-overlap-report complete \
  --evidence-dir /srv/nvme/tmp/izumi-impl/command-groups-example

python3 -B test-fixtures/sbt-worker-receipt-race/verify-exit-capture.py \
  --agent-jar /srv/nvme/tmp/izumi-impl/command-groups-example/cases/single/audit/agent.jar \
  --jdk17-home "$JDK17" --jdk21-home "$JDK21" --jdk25-home "$JDK25" \
  --evidence-dir /srv/nvme/tmp/izumi-impl/exit-capture-example
```

`verify-input-composition.py` checks earlier custom input initializers through
public SBT settings. Separate stock and adapted projects run `testQuick`,
`testSelected`, and `testOnly` in one SBT2 session. Each command executes three
physical bodies and two chained custom hooks. An earlier initializer that would
throw is replaced by a later definition and must never execute. This regression
fixture detects the previous adapter's loss of custom hooks when it substituted
the SDK's default input template. It retains command arguments, frozen source
hashes, body receipts, hook order and the actual process result.

```sh
python3 -B test-fixtures/sbt-worker-receipt-race/verify-input-composition.py \
  --repo-root . --artifact-version 1.3.0-M5-SNAPSHOT \
  --plugin-version 1.3.0-M5-SNAPSHOT --scala-version 3.9.0 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/input-composition-example
```

Repeat with Scala2.13.18 and a new evidence directory. The separate plugin version
also permits testing a candidate plugin against unchanged published libraries.
These controls cover initializer composition; complete host compatibility remains
subject to the acceptance checklist.
