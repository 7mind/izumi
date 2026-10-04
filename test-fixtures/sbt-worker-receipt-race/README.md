# SBT worker result receipt reproduction

This is a draft upstream defect report and an independent process reproduction,
using only the public test-interface Framework and SBT TestsListener APIs. It has
no distage dependency, plugin or private SDK replacement. Do not publish the
report automatically.

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

The driver returns0 only when all four observed controls reproduce the table,
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
