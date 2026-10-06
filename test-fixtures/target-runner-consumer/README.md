# Published JS/Native target-runner fixture

The fixture loads the production target bootstrap through Scala.js1.22.0 and
Scala Native0.5.12, using ordinary unannotated suites. By default its host
projection is test infrastructure. With `--production-host-version VERSION`,
it loads the published JS/Native companion SBT plugins instead; the remaining
host probe only interrupts an ordinary suite task through the public SPI.
Every lane executes five suites, interrupts the host task
while target callbacks are pending, checks a failed cancellation command, then
runs successfully again in the same SBT session.

```sh
python3 -B test-fixtures/target-runner-consumer/verify.py \
  --repo-root "$PWD" --artifact-version 1.3.0-M5-target-framework-SNAPSHOT \
  --scala-version 3.9.0 2.13.18 --host-threads 2 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/target-runner-example
```

Run after publishing the matching runner artifacts locally, using a fresh
evidence directory. Repeat with `--host-threads 1` for the serial host control.
The driver verifies physical body identities, complete framed streams, fresh
run identities, exact per-suite XML and cancellation errors. It freezes fixture
and publication hashes and rejects incomplete SDK runs.

For production host checks, publish `sbt-distage-testkit`,
`sbt-distage-testkit-js`, and `sbt-distage-testkit-native` with the supplied
version and add `--production-host-version VERSION` to the command.

These are Behavioral-Active, Effectual, Good-Communication process checks.
The held callbacks are plain Futures; this fixture does not establish DI effect
interruption, Lifecycle finalization, complete platform host policy or browser
transport support.
