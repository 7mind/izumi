# Published JVM host sharing fixture

This Linux process fixture measures the target bootstrap under SBT 1.13.0 and
2.0.9. Two plain suites and three SpecIdentity suites each declare three tests
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
  --sbt-version 2.0.9 1.13.0 \
  --scala-version 3.9.0 2.13.18 2.12.21 \
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
  --sbt-version 2.0.9 1.13.0 \
  --scala-version 3.9.0 2.13.18 2.12.21 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/counted-forks-example
```

Publish the current JVM protocol, base runner, both SBT plugins and their
dependency closure first. Each run requires a new evidence directory. This
fixture verifies owned fork completion and same-process command recovery;
held delivery, SDK task-error replacement, mixed frameworks, comprehensive
history, cancellation and cleanup faults require separate controls.
