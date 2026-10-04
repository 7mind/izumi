# Distage SBT plugin

This initial binding is built for SBT 1.13.0 / Scala 2.12.21 and SBT 2.0.9 /
Scala 3.8.4. Add `io.7mind.izumi` / `sbt-distage-testkit` with `addSbtPlugin`,
enable `izumi.distage.sbt.DistageTestkitPlugin`, and put the distage runner on
the project's test classpath. The plugin registers the target framework and
supplies build, configuration and discovered-suite identities.

SBT 1 `testQuick` and SBT 2 `test` / `testQuick` conservatively rerun selected
distage suites. Each candidate gets a `DISTAGE_CACHE_DECISION` log entry with
reason `untracked-input-closure`. This policy includes plain suites using the
distage framework. User patterns, configured test options and exclusions still
apply; the log entry is a cache decision, not an execution record. Other
frameworks keep their inherited incremental filter.

For another test configuration, install
`inConfig(configuration)(Defaults.testSettings ++ distageTestSettings)`.
`distageBuildId` and `distageTargetId` can override the default identities.
The suite-set digest is a bootstrap catalogue identity, not a tracked DI input
closure and not evidence that the complete test catalogue is unchanged.

This checkpoint does not provide efficient tracked-input caching,
`distageList` / `distagePlan`, normalized per-test arguments, application-backed
host transport, or the JS/Native plugin integration required by the plan.
