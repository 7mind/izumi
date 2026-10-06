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
Add `--logical-suite-alias` to check overridden logical IDs while retaining
ordinary class names in SBT reports.

The companion failure driver checks an attributed failure in SuiteE's third
body, or malformed SDK runner arguments followed by same-session recovery:

```sh
python3 -B test-fixtures/target-runner-consumer/verify-failures.py \
  --repo-root "$PWD" --artifact-version 1.3.0-M5-target-attribution-SNAPSHOT \
  --production-host-version 1.3.0-M5-target-attribution-SNAPSHOT \
  --scala-version 3.9.0 2.13.18 --host-threads 2 --logical-suite-alias \
  --scenario body --evidence-dir /srv/nvme/tmp/izumi-impl/target-body-example
```

Use `--scenario launch` with a fresh directory for the initialization failure.
The body scenario requires actual SBT exit 1, exactly one failed body, and its
matching logical protocol ID and class-owner XML entry. The launch scenario
requires five suite errors, no target bodies for the failed command, and a
successful fresh application after disarming the malformed option.

`verify-inspection.py` checks the production `Test / distageList` and
`Test / distagePlan` input tasks. Use the same required arguments as the failure
driver, omit `--scenario`, and supply matching runtime/plugin versions. It
compares full, suite-only, and individual-test selections, effective memoization
overrides, unknown-suite rejection, absence of body/resource activity during
inspection, and a successful ordinary test command afterward.

These are Behavioral-Active, Effectual, Good-Communication process checks.
The held callbacks are plain Futures; this fixture does not establish DI effect
interruption, Lifecycle finalization, complete platform host policy or browser
transport support.


`verify-policy.py` runs eighteen stock-task histories in one SBT session per
platform/compiler. It uses the published production plugins and matching runner
artifacts. The cases cover full/selected/wildcard/individual execution,
`test` and `testQuick` after partial runs, configured filters and exclusions,
empty selections, policy reset, serial suite scheduling, and host limits two
then one. Every executed case checks physical test identities, XML, complete
SDK streams with fresh run IDs, callback lifetime and setup/cleanup ordering.

```sh
python3 -B test-fixtures/target-runner-consumer/verify-policy.py \
  --repo-root "$PWD" --artifact-version 1.3.0-M5-target-policy-SNAPSHOT \
  --production-host-version 1.3.0-M5-target-policy-SNAPSHOT \
  --scala-version 3.9.0 2.13.18 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/target-policy-example
```

This batch uses plain Future suites. DI/configuration invalidation, scanned
plugin histories, mixed frameworks and additional configurations use
separate controls.


`verify-configurations.py` checks two aggregated modules per target, each with
independent `Test` and `Integration` configurations. Custom configurations use
`inConfig(Integration)(Defaults.testSettings ++ distageJsTestSettings)` or
`distageNativeTestSettings` from the corresponding companion's `autoImport`.
These settings include the pinned SDK's test settings and the distage host
contract. They must precede fixture source-directory and listener overrides.

```sh
python3 -B test-fixtures/target-runner-consumer/verify-configurations.py \
  --repo-root "$PWD" --artifact-version 1.3.0-M5-target-policy-SNAPSHOT \
  --production-host-version 1.3.0-M5-target-config-SNAPSHOT \
  --scala-version 3.9.0 2.13.18 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/target-configurations-example
```

The driver checks aggregation, complete and selected runs, quick/incremental
execution, one explicit test, eight list/plan requests across the four module
configurations, and later execution. XML ownership and protocol target IDs must
agree; each module/configuration owns a distinct application and fresh run ID.
Scala Native0.5.12 exposes its settings through
`ScalaNativePluginInternal.scalaNativeTestSettings`; the companion uses that
pinned SDK implementation. The SDK source is available at
[ScalaNativePluginInternal0.5.12](https://github.com/scala-native/scala-native/blob/v0.5.12/sbt-scala-native/src/main/scala/scala/scalanative/sbtplugin/ScalaNativePluginInternal.scala).

`verify-mixed.py` runs eleven histories with five runner suites and three
ScalaCheck1.19.0 properties. It checks that mixed and foreign-only selections
execute each property once, that stock incremental caching remains effective
for ScalaCheck, and that owned suites rerun after partial selections. Exclusions,
serial scheduling and host limits one/two are covered. Physical bodies, complete
owned event streams and exact XML identities must agree. Stock SBT uses separate
synthetic child groups for the ScalaCheck property XML, while aggregating their
result counts under the parent suite.

```sh
python3 -B test-fixtures/target-runner-consumer/verify-mixed.py \
  --repo-root "$PWD" --artifact-version 1.3.0-M5-target-policy-SNAPSHOT \
  --production-host-version 1.3.0-M5-target-mixed-SNAPSHOT \
  --scala-version 3.9.0 2.13.18 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/target-mixed-example
```

`verify-di.py` runs sixteen histories per target/compiler with plain, Identity,
Cats Effect and ZIO environment suites. The owner factory uses production
`StaticPluginLoader.scanCompileTime("candidate.plugins")` in a dependency
project. Histories change a private scanned implementation, compiled
configuration and an untracked external file, then exercise `test` and
`testQuick`. They also cover partial selections, activation, disabled
memoization and host limits one/two.

```sh
python3 -B test-fixtures/target-runner-consumer/verify-di.py \
  --repo-root "$PWD" --artifact-version 1.3.0-M5-target-abort-SNAPSHOT \
  --production-host-version 1.3.0-M5-target-mixed-fork-SNAPSHOT \
  --scala-version 3.9.0 2.13.18 \
  --evidence-dir /srv/nvme/tmp/izumi-impl/target-di-example
```

The complete batch checks 64 command histories, 1,060 physical/XML/protocol
outcomes and 168 resource lifetimes. The driver checks fresh resources and run
IDs, sharing within each effect, configuration values, one plugin construction
per command, no acquisition during discovery, and conservative cache decisions
without compilation for external file changes. Publish the higher runner with
its transitive dependencies and the three production host plugins first.
These are Behavioral-Active, Effectual, Good-Communication checks; DI
cancellation and deliberate finalization failure require separate controls.

`verify-di-failures.py` uses the same required arguments and five histories per
target/compiler: baseline, Cats body failure, incremental recovery, shared
resource release failure, and quick recovery. It requires the two deliberate
test-task failures, then reconciles all selected terminal identities, physical
bodies, XML statuses, run-level finalization errors and fresh resources. A
controller exit zero requires that capture audit to pass.
