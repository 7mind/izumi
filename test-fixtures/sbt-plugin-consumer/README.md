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
The combined matrix has 43 scenarios per compiler. It edits a scanned plugin
implementation, proves that all six stock suite digests remain unchanged, and
checks that `test` and `testQuick` still rerun and use the edited implementation.
A suite-class edit must change its own digest while preserving the foreign
suite's digest. Both incremental commands then execute the expected bodies.
Additional quick runs change external input, activation and memoization, and
follow a partial selection with the complete suite set.

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
test identity and rejects duplicate or omitted events. These in-process JVM
controls do not establish cancellation, fork or multi-project behavior.

```sh
python3 -B test-fixtures/sbt-plugin-consumer/verify-host-limits.py \
  --artifact-version 1.3.0-SNAPSHOT --sbt-version 2.0.9 \
  --scala-version 3.9.0 2.13.18 --host-threads 1 2 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/host-limits-capture
```
