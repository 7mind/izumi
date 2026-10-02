# Native testkit implementation status

Branch: `wip/distage-test-runner-and-scala-native`. Baseline inspected on
2026-10-02: `aefd8fea0`. No implementation status ledger or live build process
existed at that baseline; the eight local commits above the tracking branch were
planning changes. No pushes are authorized.

This ledger follows the fixed [acceptance checklist](20261001-distage-native-testkit-acceptance.md).
Partial sub-step evidence does not establish completion of its parent step or
any final evaluation point. All final items must be checked again on the final
head. The spike reports are design evidence, not implementation verification.

## Item state

| Item | State | Evaluation-point evidence |
| --- | --- | --- |
| L1 | in progress | Part-1 verification below; full gate remains outstanding. |
| L2 | in progress | Part-1 verification below; full gate remains outstanding. |
| L3 | in progress | Part-1 verification below; full gate remains outstanding. |
| L4 | not started | No evaluation point passed yet. |
| L5 | not started | No evaluation point passed yet. |
| L6 | in progress | Part-1 verification below; full gate remains outstanding. |
| G.1 | in progress | Plain-core public-boundary fixtures pass below; runner-host fixtures remain outstanding. |
| 1a.1 | in progress | No evaluation point passed yet. |
| 1a.2 | in progress | No evaluation point passed yet. |
| 1a.3 | in progress | No evaluation point passed yet. |
| 1a.4 | in progress | No evaluation point passed yet. |
| 1a.5 | waiting on released zio-interop-cats and zio-interop-tracer Native artifacts | No evaluation point passed yet. |
| 1a.6 | in progress | No evaluation point passed yet. |
| 1a.7 | in progress | No evaluation point passed yet. |
| 1a.8 | in progress | No evaluation point passed yet. |
| 1a.9 | in progress | No evaluation point passed yet. |
| 1a.10 | in progress | No evaluation point passed yet. |
| 1a.11 | in progress | No evaluation point passed yet. |
| 1b.1 | in progress | Plain-core Native build, execution, and publication checkpoint below; final recheck outstanding. |
| 1b.2 | in progress | Plain-core strict Scala 3.9 checkpoint below; final recheck outstanding. |
| 1b.3 | in progress | Cleaned published-artifact consumer checkpoint below; final recheck outstanding. |
| 1b.4 | in progress | No evaluation point passed yet. |
| 1c.1 | in progress | Current plain-core checkpoint: all nine baseline targets pass; final recheck outstanding. |
| 1c.2 | in progress | Current plain-core checkpoint: six additional range-mode runs pass; final recheck outstanding. |
| 1c.3 | in progress | Resolved-graph review and all nine published POMs pass below; final recheck outstanding. |
| 1c.4 | in progress | Independent portable oracle and external review probes pass below; final recheck outstanding. |
| 1c.5 | in progress | Current plain-core semantics and diagnostic checkpoint below; final recheck outstanding. |
| 1d.1 | not started | No evaluation point passed yet. |
| 1d.2 | not started | No evaluation point passed yet. |
| 1d.3 | not started | No evaluation point passed yet. |
| 1d.4 | not started | No evaluation point passed yet. |
| 1d.5 | not started | No evaluation point passed yet. |
| 2a.1 | in progress | Scala 3.9 verification below; gate remains outstanding. |
| 2a.2 | in progress | Scala 3.9 verification below; gate remains outstanding. |
| 2a.3 | in progress | Scala 3.9 verification below; gate remains outstanding. |
| 2a.4 | in progress | Scala 3.9 verification below; gate remains outstanding. |
| 2b.1 | not started | No evaluation point passed yet. |
| 2b.2 | not started | No evaluation point passed yet. |
| 2b.3 | not started | No evaluation point passed yet. |
| 2b.4 | not started | No evaluation point passed yet. |
| 2b.5 | not started | No evaluation point passed yet. |
| 2b.6 | not started | No evaluation point passed yet. |
| 2b.7 | not started | No evaluation point passed yet. |
| 2b.8 | not started | No evaluation point passed yet. |
| 2b.9 | not started | No evaluation point passed yet. |
| 2b.10 | not started | No evaluation point passed yet. |
| 2b.11 | not started | No evaluation point passed yet. |
| 2c.1 | not started | No evaluation point passed yet. |
| 2c.2 | not started | No evaluation point passed yet. |
| 2c.3 | not started | No evaluation point passed yet. |
| 2c.4 | not started | No evaluation point passed yet. |
| 2c.5 | not started | No evaluation point passed yet. |
| 2c.6 | not started | No evaluation point passed yet. |
| 2c.7 | not started | No evaluation point passed yet. |
| 2c.8 | not started | No evaluation point passed yet. |
| 2c.9 | not started | No evaluation point passed yet. |
| 2d.1 | not started | No evaluation point passed yet. |
| 2d.2 | not started | No evaluation point passed yet. |
| 2d.3 | not started | No evaluation point passed yet. |
| 2d.4 | not started | No evaluation point passed yet. |
| 2d.5 | not started | No evaluation point passed yet. |
| 2d.6 | not started | No evaluation point passed yet. |
| 2d.7 | not started | No evaluation point passed yet. |
| 2d.8 | not started | No evaluation point passed yet. |
| 2d.9 | not started | No evaluation point passed yet. |
| 2d.10 | not started | No evaluation point passed yet. |
| 2d.11 | not started | No evaluation point passed yet. |
| 2d.12 | not started | No evaluation point passed yet. |
| 2d.13 | not started | No evaluation point passed yet. |
| 2d.14 | not started | No evaluation point passed yet. |
| 2d.15 | not started | No evaluation point passed yet. |
| 2d.16 | not started | No evaluation point passed yet. |
| 2d.17 | not started | No evaluation point passed yet. |
| 2d.18 | not started | No evaluation point passed yet. |
| 2d.19 | not started | No evaluation point passed yet. |
| 2d.20 | not started | No evaluation point passed yet. |
| 2d.21 | not started | No evaluation point passed yet. |
| 2d.22 | not started | No evaluation point passed yet. |
| 2d.23 | not started | No evaluation point passed yet. |
| 2e.1 | not started | No evaluation point passed yet. |
| 2e.2 | not started | No evaluation point passed yet. |
| 2e.3 | not started | No evaluation point passed yet. |
| 2e.4 | not started | No evaluation point passed yet. |
| 2e.5 | not started | No evaluation point passed yet. |
| 2e.6 | not started | No evaluation point passed yet. |
| 2e.7 | not started | No evaluation point passed yet. |
| 2e.8 | not started | No evaluation point passed yet. |
| 3.1 | not started | No evaluation point passed yet. |
| 3.2 | not started | No evaluation point passed yet. |
| 4.1 | not started | No evaluation point passed yet. |
| 4.2 | not started | No evaluation point passed yet. |
| 4.3 | not started | No evaluation point passed yet. |
| 4.4 | not started | No evaluation point passed yet. |
| 4.5 | not started | No evaluation point passed yet. |
| 4.6 | not started | No evaluation point passed yet. |
| 5.1 | not started | No evaluation point passed yet. |
| 5.2 | not started | No evaluation point passed yet. |
| 5.3 | not started | No evaluation point passed yet. |
| 5.4 | not started | No evaluation point passed yet. |
| 5.5 | not started | No evaluation point passed yet. |
| 5.6 | not started | No evaluation point passed yet. |
| 5.7 | not started | No evaluation point passed yet. |
| 5.8 | not started | No evaluation point passed yet. |
| 5.9 | not started | No evaluation point passed yet. |
| O.1 | not started | No evaluation point passed yet. |
| O.2 | not started | No evaluation point passed yet. |
| O.3 | not started | No evaluation point passed yet. |
| O.4 | in progress | No evaluation point passed yet. |
| O.5 | in progress | No evaluation point passed yet. |
| O.6 | in progress | No evaluation point passed yet. |
| O.7 | in progress | No evaluation point passed yet. |
| O.8 | in progress | No evaluation point passed yet. |
| O.9 | in progress | No evaluation point passed yet. |
| O.10 | in progress | No evaluation point passed yet. |
| O.11 | not started | No evaluation point passed yet. |
| O.12 | not started | No evaluation point passed yet. |
| O.13 | not started | No evaluation point passed yet. |
| O.14 | not started | No evaluation point passed yet. |
| O.15 | not started | No evaluation point passed yet. |
| O.16 | not started | No evaluation point passed yet. |
| O.17 | not started | No evaluation point passed yet. |
| O.18 | not started | No evaluation point passed yet. |
| O.19 | not started | No evaluation point passed yet. |
| O.20 | not started | No evaluation point passed yet. |
| O.21 | not started | No evaluation point passed yet. |
| O.22 | not started | No evaluation point passed yet. |
| O.23 | not started | No evaluation point passed yet. |
| O.24 | not started | No evaluation point passed yet. |
| O.25 | not started | No evaluation point passed yet. |
| O.26 | in progress | No evaluation point passed yet. |
| O.27 | in progress | No evaluation point passed yet. |
| O.28 | not started | No evaluation point passed yet. |
| O.29 | not started | No evaluation point passed yet. |
| O.30 | not started | No evaluation point passed yet. |
| O.31 | not started | No evaluation point passed yet. |
| O.32 | not started | No evaluation point passed yet. |
| O.33 | not started | No evaluation point passed yet. |
| O.34 | not started | No evaluation point passed yet. |
| O.35 | not started | No evaluation point passed yet. |
| O.36 | in progress | Scala 3.9 verification below; gate remains outstanding. |
| O.37 | in progress | No evaluation point passed yet. |
| O.38 | not started | No evaluation point passed yet. |

## 2026-10-02: step 1a part 1, in progress

Scope: Native 0.5.12 for basics, functional, collections, literals, language,
platform, functoid, and json-circe; Cats Effect 3.7.1; toolchain and CI generation.
Native orphans, BIO, logstage, and distage still require the released ZIO interop
artifacts. This restriction is transitional; it does not satisfy 1a.2 or L6.

Commands and captures run from the repository root:

- `python3` with `urllib.request.urlopen` on the two required
  `https://repo1.maven.org/maven2/dev/zio/{zio-interop-cats,zio-interop-tracer}_native0.5_3/maven-metadata.xml`
  URLs: HTTP 404 for both. Captures: `/srv/nvme/tmp/izumi-impl/metadata/`.
  Cats Effect JVM metadata: release 3.7.1. scalac-compat-annotation metadata:
  release 0.1.5. This records dependency availability only.
- `direnv exec . sh -c 'export JAVA_HOME="$JDK21"; exec bash sbtgen.sc --js --native'`:
  exit 0, generated eight Native fundamentals projects. Capture:
  `target/native-testkit-evidence/1a-generate-initial.log`.
- Reproduction: extract the original `gen` platform-dispatch Bash block from
  `.mdl/defs/actions.md`, run with `PLATFORM=invalid`, require nonzero exit.
  Observed message `Unknown platform: invalid` with exit 0; oracle failed as
  expected. Capture: `target/native-testkit-evidence/1a-unknown-platform-before.log`.
  After correction, the same dispatch check accepts all six declared platforms
  with their exact flags and rejects invalid with exit 1. Capture:
  `target/native-testkit-evidence/1a-generator-dispatch.log`.
- Reproduction: `direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G "fundamentals-platformNative/clean; fundamentals-platformNative/compile"'`:
  exit 1, duplicate `IzPlatform`, missing `__AbstractIzPlatformPlatformSpecific`,
  missing SHA-256/file platform members, 18 compilation errors. Capture:
  `target/native-testkit-evidence/1a-platform-before.log`. The stale Native
  definitions are replaced with current platform boundaries; the shared platform
  reports `ScalaPlatform.Native` on that target.
- `direnv exec . sh -c 'command -v clang; clang --version; command -v llvm-config; command -v ld.lld'`:
  clang 21.1.2, LLVM and lld on PATH. Capture:
  `target/native-testkit-evidence/1a-toolchain.log`.

The Native checks use existing shared suites and a Native-only public platform
check (Behavioral/Active/Blackbox/Group, specified origin). Clean outputs precede
compilation after replacing the stale files, per scala-native/scala-native#5084.
No Native suite or lane is marked passed until its command terminates and the
captured test reports are checked.

Requirement-enumeration commit: `d30020eb07853800972a4aee72eb695b9fe6a92e`.
Part-1 implementation commit: `09f9cefd3607821ea3c9a00bc03e9d39f708310a`.
Requirement-enumeration reviewer: `requirements_audit` (read-only), completed
2026-10-02. Its 37 proposed items are included, plus typed in-process models
(O.38). Adapter requirements stay transitional and are superseded by 5.5.
Step-1a part-1 implementation reviewer: `requirements_audit` (read-only), completed
2026-10-02. Findings: normalize legacy timezone IDs, strengthen O.36 to every
new Scala 3 artifact, cover empty/binary/multiblock SHA inputs, document the
Native filesystem surface, and repair the ledger table. All are addressed in
this substep; this review does not establish completion of step 1a.

### Additional reproductions and corrections

- Cats Effect 3.7.1 before the annotation dependency:
  `direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G "++2.13.18; project fundamentals-bioJVM; set libraryDependencies ~= (_.map(m => if (m.organization == \"org.typelevel\" && m.name.startsWith(\"cats-effect\")) m.withRevision(\"3.7.1\") else m)); compile"'`.
  Exit 1, missing `org.typelevel.scalaccompat.annotation.package.unused` at
  `CatsConversions.scala:103`. Capture `1a-ce371-before-corrected.log` in
  `target/native-testkit-evidence/`. The first attempt (`1a-ce371-before.log`)
  failed on SBT identifier syntax and is not defect evidence.
- `fundamentals-native/clean; fundamentals-native/Test/compile; fundamentals-native/testFull`
  on 3.7.4 first linked the platform suite after adding the time/crypto
  implementations but failed three tests: compiler Java home differs from Native's
  absent Java home (two cases), and the compiler's wall-clock timezone differs
  from the Native default. Capture `1a-native3-213.log` (exit 1).
  The timestamp check now compares in the recorded compilation timezone; Java
  home checks assert that the compiler property was captured on JS/Native.
- The next clean run failed only on `Unknown time-zone ID: Europe/Dublin`,
  establishing that scala-java-time needs its optional tzdb for the timestamp
  check. Capture `1a-native-all-scala.log` (exit 1). The Native platform test
  classpath now includes scala-java-time-tzdb 2.6.0. Consumers needing named
  timezones are told to add that database; production artifacts do not bundle it.
- Reviewer identified raw `user.timezone=EST` as another valid JVM input.
  Own reproduction with JDK 21 `jshell -s` sets that property, evaluates
  `LocalDateTime.now()`, `ZoneId.systemDefault()`, and `ZoneId.of(raw)`.
  The first two succeed; the third throws `Unknown time-zone ID: EST`.
  `ZoneId.of(raw, ZoneId.SHORT_IDS)` succeeds as `-05:00`; the test uses that
  normalization. Capture `1a-timezone-alias-repro.log`.

### Candidate crypto dependency: minimal reproduction and draft upstream report

Tracker search: https://github.com/lolgab/scala-native-crypto/issues (2026-10-02);
no matching open report observed. No issue was filed. The isolated reproduction
uses this `Main.scala`:

```scala
//> using scala 3.7.4
//> using platform native
//> using nativeVersion 0.5.12
//> using dep com.github.lolgab::scala-native-crypto::0.4.0
object Main {
  def main(args: Array[String]): Unit = println("NO_CRYPTO_USED")
}
```

Command:
`direnv exec . sh -c 'exec scala-cli run /srv/nvme/tmp/izumi-impl/crypto-link-repro/Main.scala --server=false --java-home "$JDK21"'`.
Exit 1; `stack_st_x509_ops.c.o` has unresolved `OPENSSL_sk_num`,
`OPENSSL_sk_value`, and `OPENSSL_sk_free`. Capture:
`target/native-testkit-evidence/1a-crypto-minimal-repro.log`.

Draft title: "Native 0.5.12 linking fails when crypto 0.4.0 is present but unused".
Expected: the program links and prints `NO_CRYPTO_USED`, with OpenSSL installed.
Observed: the dependency's C object is linked, while its OpenSSL symbols remain
unresolved. A possible explanation is that unused Scala extern declarations do
not retain the library's link annotation; this is an inference, not a proven
upstream root cause. The production port uses a direct OpenSSL SHA-256 boundary
and does not depend on this candidate artifact.

The SHA-256 vectors in NativePlatformTest were independently generated with
Python hashlib for bytes `i % 256`, lengths 0, 1, 55, 56, 63, 64, 65, 1000.
They cover empty/binary inputs, padding transitions, and multiple blocks.

### Part-1 verification, 2026-10-02

All commands below terminated with exit 0. Logs are under
`target/native-testkit-evidence/`; this verifies the eight independent modules,
not the full step-1a lanes or L6.

- Native: `direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G "fundamentals-native/clean; fundamentals-native/Test/compile; fundamentals-native/testFull; ++2.13.18; fundamentals-native/clean; fundamentals-native/Test/compile; fundamentals-native/testFull; ++2.12.21; fundamentals-native/clean; fundamentals-native/Test/compile; fundamentals-native/testFull"'`.
  Log: `1a-native-verified.log`. Scala 3.7.4: 194 tests; Scala 2.13.18 and
  2.12.21: 189 each. XML reports have zero failures/errors. The five derivation
  tests run on Native Scala 3; Scala 2 exclusion is explicitly documented per
  1a.7. Each platform run includes 153 platform checks, including Native identity,
  unavailable JVM introspection, and eight independent SHA-256 vectors.
- JVM: the same command form with `fundamentals-jvm/Test/compile` and
  `fundamentals-jvm/testFull` for 3.7.4, 2.13.18, and 2.12.21.
  Log: `1a-fundamentals-jvm.log`. Respectively 612, 613, 613 tests, zero failures.
- JS: the same command form with `fundamentals-js/Test/compile` and
  `fundamentals-js/testFull` on all three versions, after `direnv exec . npm ci`.
  Logs: `1a-fundamentals-js.log`, `1a-npm-ci.log`. Respectively 269, 270, 270
  tests, zero failures.
- After the timezone alias normalization, JVM
  `fundamentals-platformJVM/testOnly izumi.fundamentals.platform.build.test.BuildAttributesMacroTest`
  passed four tests on each version (`1a-jvm-timestamp-final.log`), and JS
  `fundamentals-platformJS/Test/compile` passed on each version
  (`1a-js-timestamp-final.log`). The clean Native run above includes that change.
- Local publication: `direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G "fundamentals-native/publishLocal; ++2.13.18; fundamentals-native/publishLocal; ++2.12.21; fundamentals-native/publishLocal"'`.
  Log: `1a-native-publish-local.log`. Python `zipfile` inspected all 24 Ivy-local
  artifacts under `~/.ivy2/local/io.7mind.izumi/`: one binary jar containing NIR
  and one POM per module/version. No Native legacy adapter coordinate exists.
  This does not satisfy publication of the remaining modules in L6.
- Regeneration with the committed `--js --native` flags: exit 0, SHA-256
  digests of `build.sbt`, `project/plugins.sbt`, and `project/build.properties`
  unchanged (`1a-generator-idempotence.log`). L5 awaits the committed-head check.
- `git diff --check`: exit 0. A byte-prefix comparison against acceptance at
  `aefd8fea0` confirms all original items are preserved. O.36 is strengthened
  from the requirement-enumeration commit to include all new Scala 3 artifacts.

O.26 audit: Native `IzFiles` uses only `FsGet`, matching the portable JS boundary.
The additional JVM filesystem traits remain absent and are documented; no
implementation is silently substituted. SHA-256 uses
OpenSSL directly after the candidate dependency reproduction above. Native
classpath/JMX return empty lists because those JVM mechanisms do not exist.
The remaining distage plugin-loader audit belongs to part 2 and is not complete.

L1/L2/L3 remain in progress only as partial fundamentals evidence; full-repository
CI-equivalent lane commands have not passed. No final or step gate is marked done.

## 2026-10-02: step 2a, in progress

Independent of the interop release. Compiler target is 3.9.0; the source dialect
remains 3.7 to preserve existing source behavior. Scala 3 option selection now
uses the major version; PureConfig selects every Scala 3 compiler. The SBT 2
plugin retains its separately generated 3.8.4 baseline and no izumi dependencies.
No new Scala 3 artifacts are released before this gate (O.36).

- Reproduction before edits: `direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G "++3.9.0!; show distage-extension-configJVM/libraryDependencies; show distage-frameworkJVM/scalacOptions; distage-extension-configJVM/compile"'`.
  Exit 1 (`2a-before.log`). The dependency listing omits PureConfig, options omit
  kind-projector and max-inlines, and BIO compilation reports 448 errors,
  beginning with unsupported underscore type-lambda syntax. That failure
  demonstrates the omitted flags; it prevents reaching the missing-PureConfig
  compilation failure. No source patch is justified by those parser errors.
- Maven metadata on 2026-10-02 lists 3.9.0 as the latest stable Scala 3 version;
  no newer stable 3.9.x or Scala Next lane exists. Captured at
  `/srv/nvme/tmp/izumi-impl/metadata/scala3-compiler.txt`.
- [scala/scala3#27209](https://github.com/scala/scala3/issues/27209) remains open
  (API capture `scala3-27209.txt` in the same directory). The configured backend
  parallelism is 1, the plan's explicit mitigation. Existing spike results are
  its provenance; no claim of a complete upstream correction is made.
- First generation succeeded, but build loading failed on an unbraced generated
  `if` expression in settings varargs (`2a-jvm.log`). Added braces in the generator
  input and regenerated (`2a-generate-final.log`, exit 0). This intermediate
  build-loading failure is not acceptance evidence.

### Actual Native CI commands for part 1

Detached worktree `/srv/nvme/tmp/izumi-impl/native-ci-part1` is at `09f9cefd3`.
`direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'export PATH="/srv/nvme/tmp/izumi-impl/native-ci-tools:$PATH"; exec mdl -u platform:native-nojvm -u java_version:21 -u scala_version:<3|2.13|2.12> :gen :test --without-nix --verbose --simple-log'`.
`--without-nix` uses the already loaded dev shell. Native tests use no Docker;
that PATH contains an explicit empty Docker CLI view for the legacy bulk cleanup,
so unrelated host containers cannot be removed. Any other Docker command fails.

The first run (`1a-native-ci-3.log`, exit 0) compiled clean outputs but SBT history
skipped unchanged suites: only 14 platform checks ran. It is not evidence that
all shared suites executed. Fresh runs use a worktree-only `native-ci-cache.sbt`
setting `Global / localCacheDirectory := file("/srv/nvme/tmp/izumi-impl/native-ci-cache-part1")`.
This isolates persistent task results and test history; it changes no production
source. All three fresh commands exited 0; logs are `1a-native-ci-fresh-{3,2.13,2.12}.log`.
Observed test totals are respectively 194, 189, 189, with zero failures and
`NativePlatformTest` executed in each. This closes the actual CI-command check
for part 1; the full step waits for the other Native modules.

### Scala 3.9 evidence so far

- `direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G "fundamentals-native/clean; fundamentals-native/Test/compile; fundamentals-native/testFull"'`:
  exit 0, 194 Native tests, zero failures (`2a-native.log`).
- `izumi-js/Test/compile` then `izumi-js/testFull` in separate batch SBT JVMs:
  both exit 0; full JS run executes 858 tests with zero failures
  (`2a-js-compile.log`, `2a-js-test.log`).
- JVM command with library/option listings, config compilation,
  `izumi-jvm/Test/compile; izumi-jvm/testFull`: config compilation succeeds,
  flags/dependencies are present, but test compilation exits 1 in the legacy
  adapter, with `AssertionError: failure to resolve inner class`
  `javax.swing.RepaintManager$PaintManager`. Stack includes compiler
  `ImportSuggestions` and `ClassfileParser` (`2a-jvm-final.log`). No test success
  claimed. The cause and a minimal public reproduction are under investigation.
- `izumi-jvm/publishLocal`: exit 0 (`2a-publish-local.log`).
- Separate build under `test-fixtures/compiler-consumer`:
  `direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'exec sbt --server -java-home "$JDK21" -batch -J-Xmx6G -Dizumi.fixture.scala-version=3.9.0 -Dizumi.fixture.version=1.3.0-SNAPSHOT "checks/runMain izumi.fixtures.compiler.CompilerConsumer 9 0"'`:
  exit 0, `COMPILER_CONSUMER_OK release=3(9,0) good=true missing=false`
  (`2a-compiler-consumer-server.log`). It expands the published plan-check macro
  twice, including the compiler-context branch for `onlyWarn`, and the published
  ScalaRelease macro. Test module and macro producer compile separately.
  Initial invocations without `--server` failed to start a thin client and are
  not macro evidence (`2a-compiler-consumer.log`).

### Scala 3.9 import-suggestion compiler defect

Existing upstream reports: [scala/scala3#26622](https://github.com/scala/scala3/issues/26622)
(regression since 3.9.0-RC1; fix assigned to 3.10) and
[scala/scala3#20438](https://github.com/scala/scala3/issues/20438)
(Swing classfile parsing on Java 17+). Tracker API search captured in
`/srv/nvme/tmp/izumi-impl/metadata/scala3-paintmanager-issues.txt`.
No issue was filed. This is an external compiler defect, not missing project
source or an izumi classpath mutation.

Minimal independent SBT build in
`/srv/nvme/tmp/izumi-impl/import-suggestions-repro`: SBT 2.0.9, Scala 3.9.0,
`scalacOptions ++= Seq("-release:17", "-explain", "-Ybackend-parallelism", "1")`,
and this single source:

```scala
object Main {
  val errors = scala.compiletime.testing.typeCheckErrors("import javax.swing.*; summon[Ordering[JPanel]]")
  def main(args: Array[String]): Unit = {
    require(errors.nonEmpty)
    println("TYPECHECK_ERRORS_OK")
  }
}
```

`direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'exec sbt --server -java-home "$JDK21" -batch -J-Xmx6G "clean; runMain Main"'`:
exit 1, the same Swing `AssertionError` from `ImportSuggestions`
(`2a-import-suggestions-given-repro.log`). Undefined-variable probes alone
succeeded and are not reproductions (`2a-import-suggestions-minimal-server.log`,
`2a-import-suggestions-swing-repro.log`). An unmanaged-jar experiment failed
on an SBT 2 File/HashedVirtualFileRef setting type mismatch and is not evidence.

Controls in the same build: `++3.7.4!; clean; runMain Main; ++3.9.0!; set scalacOptions += "-Ximport-suggestion-timeout:0"; clean; runMain Main`:
exit 0, both programs print `TYPECHECK_ERRORS_OK`
(`2a-import-suggestions-controls.log`). Scala 3.7.4 logs the classfile exception
but recovers; 3.9.0 with import suggestions disabled does not traverse that path.
The [pinned compiler source](https://github.com/scala/scala3/blob/3.9.0/compiler/src/dotty/tools/dotc/typer/ImportSuggestions.scala)
returns no suggestions when its time budget is zero. The build applies this
flag only on 3.9.0. Residual limitation: missing-implicit/extension diagnostics
omit suggested imports on that compiler. Type checking and the captured primary
errors remain enabled; no test is removed. A released fix can replace this
mitigation after verification under the fixed compiler.

### Final compiler-option reruns

After the import-suggestion mitigation, `izumi-jvm/Test/compile` succeeds.
`izumi-jvm/testFull` then exits 1: 26 Docker integration tests fail with HTTP 500,
`making volume mountpoint .../src/test/resources/sql: mkdir /home/pavel/work:
permission denied` (`2a-jvm-mitigated.log`). The bound service is Podman through
`DOCKER_HOST=unix:///run/podman-llm/podman.sock`, with storage under another service
user's home. This is an observed fixture-mount accessibility failure, not evidence
of a Scala compiler regression. No exclusion or production correction is applied.
Worker verification is being prepared; full JVM gate remains in progress.

`project docs; makeSite` through batch SBT/JDK21: exit 0 (`2a-site.log`), mdoc
reports zero errors. This checks the Scala 3.9 documentation module but does not
substitute for the exact mdl L4 command.
Final JS and Native runs with the mitigation are captured in `2a-js-final.log`
and `2a-native-final.log`; Native exits 0 with 194 successful tests. JS exits 0 with 858 successes and 19 expected cancellations. JS's previous run reports 858 succeeded and 19 canceled. The expected canceled
cases are: skip/assume and deliberately unavailable integration checks in the legacy
runner fixtures. Those cases are explicit fixture outcomes, not omitted suites.

Compiler-upgrade substep summary: all current Scala 3 sources, including the
ScalaTest adapter's JVM tests, compile. Final JVM run records 1535 successes,
26 fixture-mount failures, 19 expected cancellations. Final JS/Native runs exit
0 (858/194 successes), and the documentation module's makeSite exits 0.
The Scala-CLI generator itself now compiles on 3.9.0; generated files are
regenerated with `--js --native`. The step remains in progress until the full
JVM run, reviewer audit, and gate evidence are complete.

The provided Ubuntu worker is being prepared for the container tests. Docker
was absent before installation; batch `apt-get install -y docker.io` exited 0
(`2a-worker-docker-setup.log`). No host Podman mounts, permissions, or unrelated
containers were changed. The worker's Nix profile is present but requires a
login shell to appear on PATH. Code transfer and run results remain pending.

Read-only compiler-upgrade substep reviewer: `requirements_audit`, 2026-10-02.
No concrete implementation defect found. It independently inspected 28 generated
option blocks, verified the SBT2 helper's published POM selects Scala 3.8.4 and
has no izumi dependencies, and reconciled final test outcomes. Its full-step
verification gaps remain tracked above. Generator compilation on 3.9.0 and
subsequent idempotence both exit 0 (`2a-generator-compiler39.log`,
`2a-generator-idempotence.log`): all three generated files are byte-identical
before/after the latter command. No full step or final gate marked done.

Compiler-upgrade implementation commit: `3005b1aa53da2fb461a090795b4a401ef8f973c1`.
After that commit, `git diff --exit-code build.sbt project/plugins.sbt
project/build.properties` exits 0. This is substep evidence for L5, not its final
evaluation point.

### Ubuntu worker verification

The fresh task checkout `/home/ubuntu/izumi-native-testkit-3005b1aa5` on
`ubuntu@llm-ubuntu-0.pgtr.7mind.io` was cloned from a local Git bundle. Its HEAD
is the compiler-upgrade commit above. The bundle contains the branch history
but not tags; sbt-git reports that it cannot describe a tag. No code was changed
in the worker checkout. SSH uses a task-specific known-hosts file under scratch.
Batch Docker/direnv installation and cloning exit 0 (`2a-worker-clone.log`).
Nix development environment realization exits 0 (`2a-worker-devshell.log`).
Initial direnv-only launches did not load the environment correctly; an
initial explicitly supplied JDK path was absent. Those launch logs are not
compilation or test evidence. The corrected launch uses the actual `$JDK21`
exported by the flake in the worker shell.

Command: SSH login shell, `NIX_CONFIG='experimental-features = nix-command
flakes' nix develop --command bash /home/ubuntu/worker-jvm.sh`; the script checks
the exact HEAD and executes `direnv exec . sbt -java-home "$JDK21" -batch
-J-Xmx6G 'izumi-jvm/Test/compile; izumi-jvm/testFull'`.
First full run exits 1 (`2a-worker-jvm-final.log`): all 18 test groups complete,
1575 successes, one failure, 19 expected cancellations. All SQL fixture mounts
and the previously failing container acquisition checks work on this worker.
The remaining failure is `DockerPullWithPlatformTestZIO`: inspection reports
an empty architecture instead of `riscv64`.

Reproduction (`2a-worker-image-inspect.log`): with fresh Docker 29.1.3's
containerd image store, `docker pull --platform linux/riscv64
library/hello-world:latest` succeeds, but its v1.44 image-inspection response
has `Architecture:""` and `Os:""`. A v1.52 inspection with the explicit platform
returns `riscv64` and `linux`. This is a daemon/API representation difference,
not evidence of a compiler defect. [Docker's documentation](https://docs.docker.com/engine/storage/containerd/)
describes the changed default and the reversible storage-backend switch.

Worker runtime configuration is now `containerd-snapshotter:false`, selecting
the supported legacy `overlay2` store. Before restarting, the daemon config was
absent and all ten containers were verified to carry this task's single
`distage.jvmrun` label; only those labeled scratch containers were removed.
The previous image-store data remains on disk. The control image inspection
now returns `riscv64 linux` (`2a-worker-legacy-docker.log`, exit 0).
The full unchanged-code rerun is in progress; no failed suite is excluded and
no Docker production code is changed. Host Podman containers remain untouched.

That unchanged-code rerun exits 0 (`2a-worker-jvm-legacy.log`): 18 completed test
groups, 1576 successes, zero failures, and 19 expected cancellations. Its command
is the exact worker command above, with the same HEAD. This establishes the
full Scala 3.9 JVM test run on JDK21 at the compiler-upgrade substep. L1's complete
JDK/Scala CI matrix and later heads remain outstanding.

## 2026-10-02: steps 1b–1c, in progress

The plain `fundamentals-assertions` module, its source/observation model, both
compiler implementations, portable renderer, and independent behavioral
fixtures are under verification. Source conventions and rendering policy are
specified in [the assertion contract](20261002-assertion-source-diagnostics.md).
No step or final gate is complete.

At this boundary, Python `urllib.request` reads both required Native interop
metadata URLs again: HTTP 404 for both. Captures:
`/srv/nvme/tmp/izumi-impl/metadata/{zio-interop-cats,zio-interop-tracer}-1b-boundary.txt`.
Independent plain-assertion work continues.

Initial SBT task definitions failed on the SBT 2 `TestResult` type, input-task
dependency syntax, and missing cache codec; these are captured in the
`1c-jvm-*.log`, `1c-scala2-jvm.log`, and `1c-cross-initial.log` files. The
fixture task explicitly executes its entry point and does not cache a successful
runtime result. A concurrent launch failed on the SBT bootstrap socket
(`1c-scala2-jvm-initial.log`), before compilation; subsequent root builds run
sequentially.

`direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G
"fundamentals-assertionsJVM/test"'` on 3.9.0 exits 0
(`1c-jvm-execution.log`): `ASSERTION_FIXTURES_OK checks=54 positions=range`.
This verifies macro semantics and diagnostics on that target only; the full
Scala/platform and range-position matrices remain under verification.

### Compiler-upgrade CI verification on the worker

On the isolated Ubuntu Docker worker at `3005b1aa53da2fb461a090795b4a401ef8f973c1`,
the exact Scala 3 JVM CI command passes under JDK 17, 21, and 25:

```sh
direnv exec . mdl -u platform:jvm -u java_version:<17|21|25> -u scala_version:3 :gen :test --without-nix --verbose --simple-log
```

The parent process enters the repository's Nix development shell before running
this command. Each lane uses its own fresh `Global / localCacheDirectory` in the
detached `/home/ubuntu/izumi-ci-3005b1aa5` worktree. Tags are fetched from a local
bundle before generation. Scripts: `/srv/nvme/tmp/izumi-impl/worker-ci-3.sh` and
`worker-ci-3-jdks.sh`. Captures: `target/native-testkit-evidence/2a-worker-ci-3.log`
(JDK21), and `2a-worker-ci-3-jdks.log` (JDK17, then JDK25); both processes exit 0.
Each lane completes 18 test groups with 1576 successes, zero failures, and 19
expected cancellations. Counts are summed from the 18 ScalaTest result records;
each lane also ends with `Execution completed successfully`. This supplies
Scala 3 L1 substep evidence at that commit; Scala 2 coverage lanes and evaluation
points on later heads remain outstanding.

### Plain-assertion reproductions and corrections

The read-only review found six defects. Reproductions preceded corrections:

- Explicit Scala 3 context evaluated before the condition: the added independent
  ordering fixture fails with `Condition is evaluated before its explicit context`
  (`1c-context-order-before.log`, exit 1). The context parameter is now inline,
  and emitted code evaluates it after the condition; a throwing condition does
  not evaluate the context.
- Scala 2 omitted evaluation of a trait receiver: the receiver fixture fails
  with `Assertion receiver evaluates once before the condition`
  (`1c-receiver-before.log`, exit 1). Emitted code now evaluates `c.prefix.tree`
  once before the condition, preserving receiver exceptions.
- Lexical normalization removed roots: the independent path oracle fails for
  Windows drive parents, absolute parents, and a relative `.` source root
  (`1c-source-root-oracle-before.log`, exit 1). Normalization now preserves
  POSIX, drive, and UNC roots, and unresolved relative parents.
- Scala 3 inline expansion recorded another file's spans under the caller's
  identity: the new separate-file fixture fails its opaque-call requirement
  (`1c-inline-before.log`, exit 1). Only a quote's `Inlined(None, Nil, body)`
  wrapper is now decomposed; inline helper calls stay opaque at their caller.
  The fixture also checks observation ranges and text against the recorded
  calling expression.
- Strict compiler settings reject the private macro accessor with E192:
  `Compile / scalacOptions ++= Seq("-WunstableInlineAccessors", "-Wconf:any:error",
  "-Wconf:cat=deprecation:warning")`, then `fundamentals-assertionsJVM/clean;
  fundamentals-assertionsJVM/compile` fails twice (`1c-strict-before.log`, exit 1).
  `@scala.annotation.publicInBinary` supplies a stable binary access path while
  retaining package visibility in source. The identical compile command then
  exits 0 (`1c-strict-after.log`); the separate public SourceFile API deprecation
  remains a warning.
- A valid `RenderLimits(8,16,1,128,Int.MaxValue)` caused tab expansion to allocate
  beyond the total bound: `RenderProbe.scala` under JVM `-Xmx64m` prints
  `BOUNDED_RENDER_THROW=java.lang.OutOfMemoryError:Requested array size exceeds VM limit`
  (`1c-render-bound-before.log`). Tabs and pointers now allocate only within the
  remaining output budget. The portable regression fixture uses the maximal tab
  width and checks message length against the total limit.

Other captured failures identify the Scala 2 typed-tree instrumentation defect
(`1c-matrix-first.log`: compiler assertion during `superaccessors`) and partial
synthesized ranges on Scala 2.12 (`1c-scala212-source-repro.log`). Fresh comparison
trees retain the resolved method symbol. Range/text recording trusts an actual
range on the macro's enclosing position as well as the expression tree, marking
untrusted synthesized ranges unavailable. The initial compiler-settings heuristic
fails the 2.13 disabled-range fixture (`1c-range-matrix.log`, exit 1), because
`c.compilerSettings` omits false Boolean options. Six separate scratch consumer
compiles on 2.12/2.13, each with default, true, and false range options, confirm
that the enclosing position preserves the range mode while expression trees may
retain synthesized ranges. Captures and public compiler-source provenance:
`/srv/nvme/tmp/izumi-impl/assertion-review/range-settings/`.
No external compiler defect is inferred from those implementation defects.

### Plain-assertion baseline matrix

The command below exits 0 on the current uncommitted implementation:

```sh
direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G "fundamentals-assertionsJVM/testFull; fundamentals-assertionsJS/testFull; fundamentals-assertionsJS/testFull; fundamentals-assertionsNative/clean; fundamentals-assertionsNative/testFull; ++2.13.18; fundamentals-assertionsJVM/testFull; fundamentals-assertionsJS/testFull; fundamentals-assertionsNative/clean; fundamentals-assertionsNative/testFull; ++2.12.21; fundamentals-assertionsJVM/testFull; fundamentals-assertionsJS/testFull; fundamentals-assertionsNative/clean; fundamentals-assertionsNative/testFull"'
```

Capture: `target/native-testkit-evidence/1c-matrix-final-review.log`. Every
3.9.0/2.13.18 target prints `ASSERTION_FIXTURES_OK checks=73 mode=range positions=range`;
every 2.12.21 target prints `checks=70 mode=point positions=point`. The repeated
JS task prints its execution marker twice, establishing that it executes twice
instead of reusing a cached successful runtime result. Earlier baseline captures
are retained as intermediate evidence, not substituted for this rerun.

The read-only review inspected all nine dependency graphs and found no
ScalaTest/Scalactic or higher izumi module. Published-artifact and additional
range-mode checks remain in progress. No 1b/1c step or final item is marked done.

At the consumer boundary, Maven metadata again reports Scala 3.9.0 as its latest
stable compiler; no newer 3.9.x patch or stable Scala Next lane exists. Native
ZIO interop metadata still returns HTTP 404 for both required artifacts.
Captures: `/srv/nvme/tmp/izumi-impl/metadata/*consumer-boundary.{xml,txt}`.

### Plain-core publication checkpoint

Additional review reproductions (`assertion-review/deep-inline/root-bounds.log`)
show that drive-relative roots `C:`/`C:.` stripped an absolute drive prefix,
`1:` was incorrectly treated as a drive letter, and excerpt/value truncation
introduced unpaired UTF-16 surrogates. Root comparison now preserves the
absolute/relative kind and uses the same alphabetic-drive policy as normalization.
Truncation and tab expansion now preserve complete valid surrogate pairs.
The independent portable fixtures cover all three output bounds and those path
cases. These captures precede the later prefix-preservation correction below.

Commands/scripts and observed results:

- `/srv/nvme/tmp/izumi-impl/1c-final-matrix.sh` runs strict Scala 3
  compile-and-execute tasks on JVM, JS, and Native, repeats the JS task, and
  publishes all three artifacts. Every target passes 80 checks with exact ranges,
  and the repeated JS invocation prints twice. Capture: `1c-published-matrix.log`.
  The subsequent Scala 2 continuation exits 1 because the session-local setting
  guard read the root Scala version and retained a Scala 3-only compiler flag.
  This is a verification-script failure, before Scala 2 producer compilation.
- `bash /srv/nvme/tmp/izumi-impl/1c-final-scala2-matrix.sh` exits 0
  (`1c-published-scala2-matrix.log`). It runs the 2.13.18 and 2.12.21 baseline
  fixtures and `publishLocal` on JVM/JS/Native, then explicitly adds
  `Test / scalacOptions += "-Yrangepos"` for all three 2.12 targets, and switches
  to 2.13 with `Test / scalacOptions ~= (_.filterNot(_ == "-Yrangepos") :+
  "-Yrangepos:false")`. It prints the selected compiler options before each
  special range mode. All 12 invocations pass: 80 checks with ranges on default
  2.13 and range-enabled 2.12, 77 checks with explicit point-only diagnostics on
  default 2.12 and range-disabled 2.13. Native outputs are cleaned before each
  mode. Together the two scripts verify the nine baseline targets and six
  additional range-mode targets on the corrected producer.
- From `test-fixtures/assertion-consumer`,
  `direnv exec ../.. sh -c 'exec sbt --server -java-home "$JDK21" -batch -J-Xmx6G
  -Dizumi.fixture.scala-version=3.9.0 -Dizumi.fixture.version=1.3.0-SNAPSHOT
  "consumerJVM/runMain izumi.fixtures.assertions.PublishedAssertionConsumer;
  consumerJS/run; consumerNative/run"'` exits 0 (`1b-published-consumer-3.9-platform.log`).
  Each target prints `PUBLISHED_ASSERTION_CONSUMER_OK`, checking separately
  expanded macros, evaluation counts, a skipped branch, and a usable message.
  The build shares no producer class directory and uses the published artifacts.
  Initial fixture wiring fails before expansion on `%%%` (E008), and then on a
  doubled explicit platform suffix; captures `1b-published-consumer-3.9.log` and
  `1b-published-consumer-3.9-resolved.log`. The correction uses SBT 2's `%%`
  platform-aware declaration, as documented by the
  [Scala Center announcement](https://github.com/scala/scala-lang/blob/main/blog/_posts/2026-06-29-sbt2.md#using-cross-published-libraries).
- Python `zipfile` and `xml.etree.ElementTree` inspect all nine published binary
  jars and POMs (`1b-published-artifact-manifest.log`): all have classfiles,
  Scala 3 has TASTy, all three JS artifacts have JS IR, and all three Native
  artifacts have NIR. None of the nine POMs depends on ScalaTest, Scalactic,
  ScalaTestplus, or another izumi artifact. The module has no izumi dependency;
  Scala 2 reflect is provided. This corroborates the review's resolved-graph
  check, rather than substituting POM contents for that check.
- Regenerate with `direnv exec . sh -c 'export JAVA_HOME="$JDK21"; exec bash
  sbtgen.sc --js --native'` (`1b-generator-idempotence.log`, exit 0). SHA-256 hashes
  of `build.sbt`, `project/plugins.sbt`, and `project/build.properties` are
  unchanged across generation. `git diff --check` exits 0.

Earlier strict-fixture checking also exposed non-Unit statement warnings
(`1c-strict-matrix.log`, exit 1). Explicit discards and the independent oracle's
`expectFailure` method make the corrected fixtures compile under strict warnings
on Scala 3 and under Scala 2.12's binding rules. Intermediate captures are
retained; only the final source checks above support this substep.

This verified substep adds the plain core and its consumer fixture. Effects and
the complete parent-step CI gates are still outstanding. The parent steps and
final evaluation points remain in progress.

### Prefix-preservation correction and rerun

The last rendering review found a separate defect: with the compiled excerpt
`🐒abc` and only one UTF-16 unit of remaining total budget, tab expansion skipped
the leading two-unit codepoint and displayed `a` at the pointer origin. The
strengthened public rendering fixture fails first (`1c-prefix-bound-before.log`,
exit 1: `Insufficient Unicode output budget never skips into a later source position`).
Expansion now stops when the next complete codepoint cannot fit. It does not
advance to later source characters. This supersedes the renderer results in the
publication-checkpoint captures above.

`bash /srv/nvme/tmp/izumi-impl/1c-final-scala3-matrix.sh` exits 0
(`1c-final-prefix-scala3.log`): strict compile-and-run on all three targets,
81 checks with ranges, repeated JS execution, and three updated local artifacts.
`bash /srv/nvme/tmp/izumi-impl/1c-final-scala2-matrix.sh` exits 0
(`1c-final-prefix-scala2.log`): six baseline runs, six additional range-mode runs,
and six updated local artifacts. Trusted-range modes pass 81 checks; point-only
modes pass 78. These two scripts verify all 15 required compiler/platform/range
modes on the corrected core. Their exact commands are retained in the scripts.

### Stable Scala 3 receiver correction

The final review independently reproduces omitted stable receiver evaluation on
Scala 3: lazy and module initialization are absent, and a field access through a
null holder is omitted. Equivalent Scala 2 consumers retain those effects. The
portable regression fails before the correction with `Lazy receiver initializes
before condition evaluation` (`1c-stable-receiver-before.log`, exit 1). Scala 3
inline expansion omits a stable prefix when the body does not reference `this`.
Both public overloads now pass a quote of `this` into the macro, which explicitly
evaluates it before the recorder and condition. The narrow JVM rerun exits 0 and
passes 86 checks (`1c-stable-receiver-after.log`).

The first strict matrix passes JVM and stops at the new JS null-field fixture:
the receiver access throws `UndefinedBehaviorError`, while the fixture catches
only `NullPointerException`. The captured excerpt is retained in
`1c-receiver-js-fixture-failure.txt`; the complete initial log was replaced by
the rerun, as that capture explicitly records. Generated JS retains the null
access before the condition. This is the documented default
[Scala.js fast-linking semantics](https://www.scala-js.org/doc/semantics.html),
not a failure of the receiver correction. The fixture now compares the thrown
class against direct field access in the same runtime and verifies that the
condition did not execute; production behavior is unchanged by that fixture
correction.

`bash /srv/nvme/tmp/izumi-impl/1c-final-scala3-matrix.sh` then exits 0
(`1c-final-receiver-scala3.log`): strict compilation, 86 checks on each target,
repeated JS execution, a clean Native link, and three updated local artifacts.
The read-only reviewer independently recompiles the current producer and consumer
with strict Scala 3 options. Lazy/module/null-field controls, explicit context
order, thrown receiver behavior, subclass `this`, all nine deep-inline cases,
root identities, Unicode bounds, and maximal-tab checks pass. Captures:
`/srv/nvme/tmp/izumi-impl/assertion-review/final-current/receiver-corrected/`.
Its review reports no concrete residual plain-core defect; this is review
evidence, separate from the portable runtime and publication checks.

`bash /srv/nvme/tmp/izumi-impl/1c-final-scala2-matrix.sh` exits 0
(`1c-final-receiver-scala2.log`): all six baseline and six additional range-mode
runs pass, with six updated local artifacts. All trusted-range modes pass 86
checks; point-only modes pass 83. Together the scripts verify all 15
compiler/platform/range modes on the current core. The extra five checks cover
lazy and module receivers plus preservation of a failing stable field access.

From `test-fixtures/assertion-consumer`, the following exits 0 against the newly
published 3.9.0 artifacts, after cleaning all consumer outputs:

```sh
direnv exec ../.. sh -c 'exec sbt --server -java-home "$JDK21" -batch -J-Xmx6G -Dizumi.fixture.scala-version=3.9.0 -Dizumi.fixture.version=1.3.0-SNAPSHOT "consumerJVM/clean; consumerJS/clean; consumerNative/clean; consumerJVM/runMain izumi.fixtures.assertions.PublishedAssertionConsumer; consumerJS/run; consumerNative/run"'
```

Capture: `1b-published-consumer-receiver-final.log`. Each actual target prints
`PUBLISHED_ASSERTION_CONSUMER_OK`, including the additional lazy receiver
initialization check. Python `zipfile` and `ElementTree` inspection again verifies
all nine published binary jars and POMs, including TASTy/JS IR/NIR and dependency
exclusions (`1b-published-artifact-receiver-manifest.log`, exit 0). The generated
build inputs have not changed since the idempotent generation checkpoint.
This commit records the verified plain-core substep. Complete repository CI,
effect adapters, and all final-head evaluations remain outstanding; no parent
step is claimed complete.
