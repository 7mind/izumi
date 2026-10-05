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
task instead. Neither build uses distage. Both throwing commands fail; their
structured reporting differs:

| SDK | Control | Actual process exit | Target buffered successes | Host group / doComplete | JUnit cases / errors |
| --- | --- | --- | --- | --- | --- |
| SBT2.0.9 | normal return | 0 | 1 | Passed / Passed | 1 / 0 |
| SBT2.0.9 | task throws | 1 | 1 | absent / absent | 0 / 0 |
| SBT1.13.0 | normal return | 0 | 1 | Passed / Passed | 1 / 0 |
| SBT1.13.0 | task throws | 1 | 1 | Error / Error | 1 / 1 |

```sh
python3 -B test-fixtures/sbt-worker-receipt-race/verify-task-error.py \
  --evidence-dir /srv/nvme/tmp/izumi-impl/sbt-task-error-example
```

Driver0 requires both SBT 2 controls, including the missing SBT2
report. It does not establish product acceptance. Each control uses a fresh
build and records its inputs, target and parent process identities, classpaths,
raw output, XML and actual exit. The target receipt proves buffering by the
worker's EventHandler; it does not prove delivery to the host.

In the pinned SBT2 source, ForkTestMain.testError sends a `forkError` notification
before runTest sends its replacement one-error `testEvents` batch. React handles
`forkError` by failing the response promise immediately; mainTestTask then closes
the worker and unregisters the listener. Its normal doComplete call is skipped.
This source ordering is consistent with the measured missing report. The
capture does not contain a wire trace and does not prove that every throwing
task loses its group. SBT1's corresponding diagnostic does not abort event
processing before its replacement error batch in this control.

Tracker searches on 2026-10-04 did not identify an exact matching fork-worker
report among the returned results. [sbt/sbt#9667](https://github.com/sbt/sbt/pull/9667)
addresses an escaping LinkageError in TestRunner; the pinned fork worker has the
separate notification ordering described above. This reproduction remains an
unfiled draft report, with no SDK upgrade or private SDK replacement.

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
it runs a normal control, holds the second batch to reproduce truncated output,
and runs a normal recovery. The production guard must reject the held command
through the public `testOnly.result` boundary after the callback returns. Each
command executes exactly six bodies in a fresh worker and creates six positive
XML cases; the incomplete command returns only three cases in `Tests.Output`.
The driver checks those sets, command rejection, recovery and receipt cleanup.

```sh
python3 -B test-fixtures/sbt-worker-receipt-race/verify-plugin-delivery.py \
  --artifact-version 1.3.0-SNAPSHOT --scala-version 3.9.0 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/plugin-delivery-example
```

Repeat with `--scala-version 2.13.18` and a new evidence directory. This verifies
rejection of incomplete SDK output; it does not restore the missing batch or
close global acknowledgement, callback drain, foreign history or structured
run-error reporting. The independent upstream reproductions above continue to
run without the distage plugin.
