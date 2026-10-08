This SBT2 consumer preserves `JUnitXmlRegressionTest`'s test name and four
parallel-duration checks. Its package and reporter migration was approved by
the owner. The `parallel` project runs four real `SpecIdentity` bodies through
the production distage SBT plugin and its JUnit listener. A bounded start gate
requires all four bodies to execute concurrently; each then sleeps two seconds.
The `checks` project reads that generated XML and checks the original full test
names and minimum durations. Its test task depends on the parallel project's
test task, so an absent or failing body cannot leave a passing check.

Publish `fundamentals-assertionsJVM`, `distage-test-runnerJVM`,
`distage-testkit-runnerJVM`, and `sbt-distage-testkit` under one normal artifact
version. Publish the plugin and its protocol on pinnedScala3.8.4 after the
library publications; the consumer usesScala2.13/3.9. Then run:

```sh
python3 test-fixtures/junit-regression-consumer/verify.py \
  --repo /absolute/path/to/izumi \
  --output /absolute/path/to/new-capture-directory \
  --artifact-version VERSION
```

The driver runs Scala2.13.18 and Scala3.9.0 with SBT2.0.9/JDK21, records producer
exit codes and frozen inputs, and reconciles the one migrated regression case
and four auxiliary real-body cases per compiler. The plugin retains its pinned
Scala3.8.4 compiler. Reports, commands, resolved classpaths, and measured durations
remain in the capture directory. The consumer has no ScalaTest dependency.
