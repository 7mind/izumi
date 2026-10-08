# Published runner coverage fixture

This fixture runs two test modules against known executed and unexecuted
branches. It copies the repository's complete assertion fixtures into an
instrumented library and adds a small assertion success/failure witness.

```sh
python3 -B test-fixtures/coverage-consumer/verify.py \
  --repo-root "$PWD" --artifact-version 1.3.0-M5-target-policy-SNAPSHOT \
  --production-host-version 1.3.0-M5-target-abort-host-SNAPSHOT \
  --scala-version 3.9.0 2.13.18 --platform jvm js native \
  --evidence-dir /srv/nvme/tmp/izumi-impl/coverage-example
```

Publish the matching base runner/assertion artifacts and production SBT plugins
first. Use a fresh evidence directory. JVM runs in process and forked; JS and
Native coverage use Scala 2.13 only. A selection with no supported combination
fails. The fixture uses the production Scoverage compiler dependency helper to
keep compiler artifacts on JVM while preserving target runtime dependencies.
Its Compile/Test macro classpaths use the private JVM coverage runtime; target
execution retains the JS/Native runtime.

Each lane runs an ordinary baseline, enables coverage, executes both modules,
generates per-module and aggregate reports, and preserves their XML before
`coverageOff` and a clean normal publication. The audit checks physical bodies,
the known 50% branch witnesses, assertion execution, fork process identities,
published class/TASTy/NIR/SJSIR payloads, and absence of instrumentation and
coverage dependencies from normal artifacts.

Compiler instrumentation differs: Scala 3 records the small assertion method;
Scala 2 records its caller. Scala 3 skips the largest assertion fixture method
because it exceeds the compiler's instrumentation threshold. These are
Behavioral-Active, Effectual, Good-Communication checks; they do not establish
coverage of every macro-generated instruction or the entire repository.
