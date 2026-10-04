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

`Test / distageList` lists resolved test identities and effective settings.
`Test / distagePlan` includes the selected dependency plans. Both run the common
application on the configuration's test classpath using SBT's public runner,
respect its fork setting, and print a `DISTAGE_INSPECTION` JSON response. They do
not provision resources or execute test bodies. Planning failures fail the
inspection command and remain in its response.

Ordinary framework arguments after `--` and inspection task arguments use the
same request syntax:

- `--suite-id <logical-suite-id>` and `--test-id <JSON>` select a union of suites
  and tests. Repeat them for multiple identities.
- Test JSON has `target`, `suite`, `path` (an array of segments), and `variant`
  (a string or null). Obtain these values from `distageList`; display names are
  not test identities.
- `--axis <JSON>` overrides activation, and `--axis-filter <JSON>` filters it.
  Each JSON object has `axis` and `value` strings. Repeat for different axes.
- `--memoization inherit|enabled|disabled` chooses the memoization policy.

For example, `testOnly *MySpec -- --memoization disabled` runs the selected
suite with memoization disabled. Use compact JSON and encode spaces inside
strings as `\u0020`: SBT's test-command parser splits framework arguments on
whitespace even inside quoted JSON. Quote the JSON, escaping its double quotes
and backslashes inside an outer double-quoted string. For example, the JSON
string `"equal display name"` becomes `"equal\u0020display\u0020name"` before SBT
quoting. The inspection task parser also accepts that representation.
Identities are supplied by
the plugin; duplicate options, unknown options and malformed identities reject.
`RequestArguments.parse` and `render` in the portable protocol expose the same
normalized `RunRequest` accepted by StandaloneLauncher's JSON command frames.

This checkpoint does not provide efficient tracked-input caching, complete
streaming/IDE host integration, or the JS/Native plugin integration required by
the plan.
