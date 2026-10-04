# Published SBT plugin process fixture

The driver copies the existing host-sharing fixture into independent builds,
loads the published distage plugin, and uses its framework registration and
identity arguments. Real scanned-plugin acquisition reads an owned external
UTF-8 input. Repeated stock incremental commands must rerun distage bodies and
materialize the latest input, while a separate foreign framework retains stock
cache skips. Body IDs, served resource IDs, paired lifetimes and JUnit identities
are checked after each run. Configuration filters and a custom test configuration
exercise the same public plugin settings.

Additional controls inspect exact IDs without body execution or acquisition,
select one DI body through framework JSON arguments, and verify the subsequent
incremental request still runs the whole suite. Disabling memoization gives
three distinct paired lifetimes. Inspection and individual selection also run
with forks and the custom configuration. Empty cases retain directory snapshots.
An inspection-only configuration supplies a constructor sentinel through its
Java options. Its list/plan child PIDs must differ from SBT's PID, where the
sentinel is explicitly absent.

Run after publishing both host plugin variants and the runner closure:

```sh
python3 test-fixtures/sbt-plugin-consumer/verify-matrix.py \
  --artifact-version 1.3.0-SNAPSHOT \
  --sbt-version 2.0.9 1.13.0 \
  --scala-version 3.9.0 2.13.18 2.12.21 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/sbt-plugin-example
```

These are Behavioral-Active, Effectual, Good-Communication tests of real SBT
processes. A lane deadline signals its owned process group and waits for the
launched SBT process. This
fixture does not prove the complete incremental-input model, distinct stock
suite digests, the complete activation/input domain, multi-project aggregation or
failure/cancellation recovery.
