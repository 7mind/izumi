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
| L3 | in progress | Part-1 and full Native CI checkpoint verification below; parent-step and final evaluation remain outstanding. |
| L4 | not started | No evaluation point passed yet. |
| L5 | not started | No evaluation point passed yet. |
| L6 | in progress | Part-1 verification below; full gate remains outstanding. |
| G.1 | in progress | Plain-core public-boundary fixtures pass below; runner-host fixtures remain outstanding. |
| 1a.1 | in progress | No evaluation point passed yet. |
| 1a.2 | in progress | No evaluation point passed yet. |
| 1a.3 | in progress | No evaluation point passed yet. |
| 1a.4 | in progress | No evaluation point passed yet. |
| 1a.5 | in progress | Released 23.1.0.14 artifacts and bounded Native BIO build/runtime/publication now verified below; higher Native graph and final evaluation remain outstanding. |
| 1a.6 | in progress | No evaluation point passed yet. |
| 1a.7 | in progress | No evaluation point passed yet. |
| 1a.8 | in progress | Bounded core checkpoint 732228776 verifies the Native 0b DI/configuration and four-body parallel memoized Lifecycle checks on all three compilers below; parent-step evaluation remains outstanding. |
| 1a.9 | in progress | All three full Native-only CI lanes pass with the serial-link policy below; parent-step and final evaluation remain outstanding. |
| 1a.10 | in progress | No evaluation point passed yet. |
| 1a.11 | in progress | Bounded logstage checkpoint below passes all nine producer lanes and Native published shutdown/drain controls; final-head evaluation remains outstanding. |
| 1b.1 | in progress | Plain-core Native build, execution, and publication checkpoint below; final recheck outstanding. |
| 1b.2 | in progress | Plain-core strict Scala 3.9 checkpoint below; final recheck outstanding. |
| 1b.3 | in progress | Cleaned published-artifact consumer checkpoint below; final recheck outstanding. |
| 1b.4 | in progress | No evaluation point passed yet. |
| 1c.1 | in progress | Current plain-core checkpoint: all nine baseline targets pass; final recheck outstanding. |
| 1c.2 | in progress | Current plain-core checkpoint: six additional range-mode runs pass; final recheck outstanding. |
| 1c.3 | in progress | Resolved-graph review and all nine published POMs pass below; final recheck outstanding. |
| 1c.4 | in progress | Independent portable oracle and external review probes pass below; final recheck outstanding. |
| 1c.5 | in progress | Current plain-core semantics and diagnostic checkpoint below; final recheck outstanding. |
| 1d.1 | in progress | Cats matrix, nine BIO runtime lanes and published JVM/JS/Native consumers below; final evaluation outstanding. |
| 1d.2 | in progress | Cats matrix, nine BIO runtime lanes and published JVM/JS/Native consumers below; final evaluation outstanding. |
| 1d.3 | in progress | Actual BIO defects and typed-error preservation verified on all nine lanes and published consumers below; final evaluation outstanding. |
| 1d.4 | in progress | Legacy display checkpoint passes on six JVM/JS lanes below; parent-step evaluation outstanding. |
| 1d.5 | in progress | Explicit suspension boundary and runtime adapters below; final recheck outstanding. |
| 2a.1 | in progress | Scala 3.9 verification below; gate remains outstanding. |
| 2a.2 | in progress | Scala 3.9 verification below; gate remains outstanding. |
| 2a.3 | in progress | Scala 3.9 verification below; gate remains outstanding. |
| 2a.4 | in progress | Scala 3.9 verification below; gate remains outstanding. |
| 2b.1 | in progress | Base runner checkpoint: nine artifact POMs and 18 resolved Compile/Test classpaths satisfy the fundamentals/protocol bound below; final evaluation outstanding. |
| 2b.2 | in progress | Current generated/observed graphs match: 109 nodes, 213 scoped records, no cycle; final evaluation outstanding. |
| 2b.3 | in progress | Five fundamentals test projects run all 42 lanes; 51 actual publishLocal requests create no artifacts below; final evaluation outstanding. |
| 2b.4 | in progress | All 42 moved lanes preserve the original 3,219 JUnit cases below; step-2b evaluation remains outstanding. |
| 2b.5 | in progress | Plain WordSpec sync/Future bodies pass nine producer and nine published-consumer lanes below; final evaluation outstanding. |
| 2b.6 | in progress | Owned higher provider and all four spec entry points now pass nine JVM/JS/Native producer lanes below; common host integration and final evaluation remain outstanding. |
| 2b.7 | in progress | Resource-free plain registration and raw DI/four-spec discovery pass nine lanes below; final evaluation outstanding. |
| 2b.8 | in progress | Atomic plain and higher registration pass nine JVM/JS/Native lanes below, alongside delegating bootstrap and forwarding/direct synchronous cached custom-plugin controls. Opaque/handoff reconstruction ownership defects, complete custom-hook audit and final evaluation outstanding. |
| 2b.9 | in progress | Duplicate plain and distage path/suite/test IDs reject in all nine JVM/JS/Native lanes below; final evaluation outstanding. |
| 2b.10 | in progress | Existing pure plain and retained autoset/three-effect distage suites pass import-only fixtures below; complete compatibility inventory and final evaluation outstanding. |
| 2b.11 | in progress | Plain factories and raw DI finalization gates pass below, including transport/finalizer failures; complete front-end and final evaluation outstanding. |
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
| O.2 | in progress | Effect APIs have explicit requested return types; runtime checkpoints below. |
| O.3 | in progress | Explicit suspension capability; Cats/BIO law checkpoints below. |
| O.4 | in progress | No evaluation point passed yet. |
| O.5 | in progress | No evaluation point passed yet. |
| O.6 | in progress | No evaluation point passed yet. |
| O.7 | in progress | No evaluation point passed yet. |
| O.8 | in progress | No evaluation point passed yet. |
| O.9 | in progress | No evaluation point passed yet. |
| O.10 | in progress | No evaluation point passed yet. |
| O.11 | in progress | Separate Cats/BIO artifacts above their runtimes; dependency verification outstanding. |
| O.12 | in progress | Portable protocol checkpoint passes all nine producer lanes, twelve published consumers and four isolated-loader exchanges; real transports and final evaluation remain outstanding. |
| O.13 | in progress | Four spec entry points run on the base provider contract in six JVM/JS lanes below; common plugin/framework/IDE integration and final evaluation outstanding. |
| O.14 | in progress | Five test projects retain original packages, source variants and supported platforms below; final evaluation outstanding. |
| O.15 | not started | No evaluation point passed yet. |
| O.16 | not started | No evaluation point passed yet. |
| O.17 | in progress | Declarative registration and arbitrary-constructor side-effect boundary documented; no-resource front-end discovery verified below; final evaluation outstanding. |
| O.18 | in progress | Owned/default/delegating bootstrap and forwarding/direct synchronous custom-plugin controls pass below; opaque/handoff reconstruction ownership defects, complete custom-hook audit and final evaluation outstanding. |
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
| O.33 | in progress | Unit-to-Assertion bridge resides in the existing separate legacy artifact. |
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

## 2026-10-02: committed plain-core CI checkpoint

Plain-core commit: `88dea42eb8606cd4bdd692f258e22c4d546e3fb5`. Before any
effect changes, regenerate with `--js --native` (`1b-committed-generator.log`,
exit 0), then `git diff --exit-code build.sbt project/plugins.sbt
project/build.properties` exits 0 (`1b-committed-generator-diff.log`). This is an
L5 checkpoint on that commit, separate from the final-head evaluation.

`bash /srv/nvme/tmp/izumi-impl/local-ci-core-platforms.sh` exits 0 on a detached
worktree at that commit. It runs the exact L2/L3 `mdl :gen` lanes on JDK 21:
`js-nojvm` uses `:test` on Scala 3 and `:coverage` on Scala 2; `native-nojvm`
uses `:test` on all three versions. Each lane has a fresh SBT task cache and
removed target outputs. An isolated direnv data directory allows the worktree
without writing the sandbox's read-only home state. A Docker wrapper supplies
an empty container view only for the lane's cleanup; host containers remain
untouched. Capture: `1b-core-ci-platforms.log`.

| Platform | Scala | ScalaTest succeeded/failed/canceled | Plain-core checks |
| --- | --- | --- | --- |
| JS | 3.9.0 | 858/0/19 | 86, ranges |
| JS | 2.13.18 | 826/0/19 | 86, ranges |
| JS | 2.12.21 | 825/0/20 | 83, point-only |
| Native | 3.9.0 | 194/0/0 | 86, ranges |
| Native | 2.13.18 | 189/0/0 | 86, ranges |
| Native | 2.12.21 | 189/0/0 | 83, point-only |

The count check parses 13 ScalaTest result groups per JS lane and four per Native
lane, and separately requires the plain-core execution marker and successful
terminal lane record. Assertion entry-point checks are not counted as ScalaTest
tests. The full nine-lane JVM matrix subsequently exits 0 on the isolated
Docker worker against the same fixed commit. The exact command is
`bash /home/ubuntu/worker-ci-core-launch.sh` through the worker's login shell;
the launcher enters the Nix development shell and runs the generated JVM
`mdl :gen :test` (Scala 3) or `:gen :coverage` (Scala 2) lane in a detached
worktree. Each lane removes its target outputs and has its own fresh SBT task
cache. The cleanup checks every container's task-specific label before acting.
Captures: `1b-core-worker-ci-jvm.log`, with the independently parsed counts in
`1b-core-worker-ci-counts.log`.

| JVM Scala | JDKs | ScalaTest succeeded/failed/canceled per lane | Plain-core checks per lane |
| --- | --- | --- | --- |
| 3.9.0 | 17, 21, 25 | 1576/0/19 | 86, ranges |
| 2.13.18 | 17, 21, 25 | 1544/0/19 | 86, ranges |
| 2.12.21 | 17, 21, 25 | 1542/0/20 | 83, point-only |

The count check requires 18 ScalaTest result groups, the plain-core execution
marker, and the successful terminal record for each of the nine lanes. It prints
`JVM_CI_MATRIX_OK lanes=9`. Together with the six local platform lanes above,
this is a complete L1–L3 checkpoint on `88dea42eb`, before the effect changes;
the final-head evaluations remain outstanding.

## 2026-10-02: step 1d, in progress

`AssertionSuspension1` and `AssertionSuspension2` explicitly require deferred,
per-execution checks and effect failure/defect handling. `assert1` returns
`F[Unit]`; `assert2` returns `F[Nothing, Unit]`. Their condition, context, and
recorder are inside suspension; receiver and capability resolution are at effect
construction. The plain core depends on neither runtime and exposes no QuasiIO
guarantee. Separate Cats and BIO artifacts delegate to `Sync.delay` and `IO2.sync`.
The legacy artifact contains the temporary Unit-to-ScalaTest assertion bridge.

First checks and captured wiring failures:

- `1d-core-api-compile.log`, exit 0: existing JVM plain-core fixtures pass after
  the API additions on all three compilers (86 trusted-range/83 point checks).
- `1d-adapter-first.log`: Cats JVM passes 12 public-boundary checks, then BIO
  test compilation fails for missing `izumi.reflect.Tag`. The ZIO dependency
  excludes reflect, and optional parent dependencies do not supply it. The BIO
  fixture now declares the pinned reflect artifact in Test scope.
- `1d-scala3-first-matrix.log`: Cats JVM/JS/Native each pass 12 checks; actual
  BIO JVM passes 13, including defect and unchanged typed-error checks. JS linking
  then fails for missing `java.time.Instant`/`Duration`, before execution. The
  BIO fixture now declares the same pinned scala-java-time support as the
  existing BIO tests, in its JS and pending Native Test scopes. The corrected
  JS rerun is outstanding; this initial matrix is not claimed successful.

The effect fixtures use independent exceptions and counters at the public macro
and runtime boundary. Actual IO/ZIO repeat and concurrent executions compare
distinct failures, recorded operand values, branch observations, and preservation
of earlier diagnostics. Their construction, condition/context exception, and
return-type checks do not rely on the assertions under test to verify outcomes.

At this boundary the two required Native interop metadata URLs still return
HTTP 404. Scala compiler metadata's release tag is `3.10.0-RC3`, a prerelease;
parsing stable version entries confirms 3.9.0 is the latest stable, with no newer
3.9.x or stable Scala Next consumer lane. Captures:
`/srv/nvme/tmp/izumi-impl/metadata/*1d-boundary.{xml,txt}`. Native BIO remains
unavailable in the committed dependency graph pending the named released
artifacts; all independent work continues.

The corrected BIO JS runtime exits 0 with 13 checks
(`1d-bio-js-bridge-first.log`), then the bridge fixture's additional status
predicate fails. Instrumentation (`1d-bridge-instrumented.log`, exit 1) records
`status=true evaluations=1 failures=1 events=Vector(TestStarting, TestFailed)`.
Existing `DistageScalatestReporter.endSuite` and registry completion call
`setCompleted`, without calling `setFailed` for a body failure. The fixture now
checks the reported `TestFailed`, exact body count, preserved throwable,
diagnostic, and source; it prints the observed legacy status explicitly.
This preserves the existing adapter's status behavior and does not repair that
separate retirement concern. Read-only review confirms that the fixed 1d.4
display boundary does not require an aggregate-status correction.

The corrected public legacy runner/reporter invocation exits 0 on both JVM and
JS (`1d-bridge-jvm-js.log`), each printing
`SCALATEST_ASSERTION_BRIDGE_OK executed=1 failed=1 legacyStatus=true`. The JS
command sets `Test / mainClass` to the fixture, enables its main initializer,
disables the test initializer, and runs `distage-testkit-scalatestJS/Test/run`;
these settings are session-local. The fixture suite has a constructor argument
and is instantiated explicitly, so the intentional failure is not part of
ordinary framework discovery.

Independent read-only effect probes pass on all three compilers: construction
order receiver/evidence/suspend, deferred condition/context, thrown receiver,
repeat/concurrent independent diagnostics and operand values, and caller source
attribution. All nine Scala 3 inline cases also pass through `assert1`.
Captures: `/srv/nvme/tmp/izumi-impl/assertion-review/effect-review/`.
These probes supplement actual IO/ZIO runtime checks; they do not substitute for
the Scala 2 runtime matrix or the published effect consumers, which remain
outstanding at this checkpoint.

### Scala 2 runtime and bridge matrix

`bash /srv/nvme/tmp/izumi-impl/1d-scala2-matrix.sh` exits 0
(`1d-scala2-matrix.log`). On each of 2.13.18 and 2.12.21, Cats executes on
JVM/JS/Native and passes 12 checks, BIO executes on JVM/JS and passes 13 checks,
and the legacy bridge executes on JVM/JS with exactly one reported failure.
Native outputs are cleaned before linking. Alongside the Scala 3 captures,
this verifies nine Cats lanes, six BIO lanes, and six legacy bridge lanes.
Native BIO checks still await the released interop artifacts.

`1d-strict-publish-scala3.log` runs strict Scala 3 options on the eight new/core
artifact projects. All three plain-core runs pass 86 checks, all three Cats runs
pass 12, and both BIO runs pass 13. The script then exits 1 at a nonexistent
`fundamentalsJVM` aggregate name; no publication result is claimed from that
suffix. The corrected explicit 14-project publication command in
`/srv/nvme/tmp/izumi-impl/1d-publish-scala3.sh` exits 0
(`1d-publish-scala3.log`). It publishes all eight Scala 3 assertion variants and
six JavaScript parent dependencies, from the 3.9.0 producer. The first cleaned
five-target consumer command (`1d-published-consumers-first.log`, exit 1)
executes the three plain/unary consumers and BIO JVM successfully, then fails
to resolve the separately unpublished `fundamentals-functional_sjs1_3` artifact.
Publishing `fundamentals-functionalJS/publishLocal` exits 0
(`1d-functional-js-publish.log`). This corrects the local publication closure;
no consumer source change was needed.

The corrected standalone build command cleans all five targets, then runs
`consumerJVM/runMain izumi.fixtures.assertions.PublishedAssertionConsumer`,
`consumerJS/run`, `consumerNative/run`,
`bioConsumerJVM/runMain izumi.fixtures.assertions.PublishedBIOAssertionConsumer`,
and `bioConsumerJS/run`, with required properties
`-Dizumi.fixture.scala-version=3.9.0 -Dizumi.fixture.version=1.3.0-SNAPSHOT`.
It exits 0 (`1d-published-consumers-final.log`). Each of the three plain/unary
targets prints `PUBLISHED_ASSERTION_CONSUMER_OK plain=true unary=true`; each of
the two BIO targets prints
`PUBLISHED_BIO_ASSERTION_CONSUMER_OK binary=true defects=true`. Producer and
consumer compilation directories are separate. These public-boundary checks
verify suspended construction and distinct repeat failures through the
published adapters, in addition to the existing plain diagnostic checks.

`bash /srv/nvme/tmp/izumi-impl/1c-final-scala2-matrix.sh` exits 0 after the
effect additions (`1d-plain-range-regression-scala2.log`). It rechecks the plain
macro across JVM/JS/Native: 2.13 defaults and 2.12 with `-Yrangepos` each pass
86 checks per target; 2.12 defaults and 2.13 with `-Yrangepos:false` each pass
83 point/missing-range checks per target. This supplies the twelve Scala 2
regression lanes after the shared macro expansion gained effect suspension;
the three strict Scala 3 plain runs above complete the fifteen-mode matrix.

`bash /srv/nvme/tmp/izumi-impl/1d-publish-scala2-and-graphs.sh` exits 0
(`1d-publish-scala2-and-graphs.log`). It publishes the eight core/Cats/BIO
variants on each Scala 2 version, then exports both Compile and Test resolved
dependency classpaths for all eight variants on all three compilers. The
24-variant artifact checker exits 0 (`1d-artifact-manifest.log`), verifying each
local jar/POM, platform IR, Scala 3 TASTy, and the absence of ScalaTest/Scalactic
or an izumi dependency above fundamentals. It prints
`ASSERTION_ARTIFACT_MANIFEST_OK artifacts=24 native=6 js=9 jvm=9`.
The resolved-classpath checker exits 0 (`1d-dependency-graph-check.log`),
requiring all 24 variants and rejecting those dependencies in both scopes.
Its initial parser expected literal `target/out` paths and failed at its
nonempty-module invariant; SBT actually abbreviates these paths as `${OUT}`.
The corrected checker accepts either observed path representation and retains
the invariant. No dependency-graph claim came from the failed parser.

Regenerating with `JAVA_HOME="$JDK21" bash sbtgen.sc --js --native` exits 0
(`1d-generator.log`). SHA-256 comparison of all three generated files is
identical before and after (`1d-generator-idempotence.log`); `git diff --check`
exits 0. Native BIO remains outstanding; this checkpoint completes the
independent effect-adapter work, not the parent step or final-head gates.

The read-only requirements reviewer independently checks the 24 fresh jars/POMs,
48 Compile/Test classpaths, runtime and published-consumer markers, position-mode
regressions, and the updated ledger. It reports no unresolved implementation
finding within this independent checkpoint. It confirms that Scala 2 reflect
remains Provided and BIO fixture runtime dependencies remain Test-only. Native
BIO and parent/final evaluations are explicitly excluded from the completion
claim. The local commit containing this checkpoint is identified in the next
ledger update; it is never pushed.

## 2026-10-02: effect checkpoint commit and step 2b start

Verified effect checkpoint commit:
`c34b65cd45bbd83c1b0f0b72368ec243a62e39f6`. After committing, the exact
`--js --native` regeneration exits 0 (`1d-committed-generator.log`), and
`git diff --exit-code build.sbt project/plugins.sbt project/build.properties`
exits 0 (`1d-committed-generator-diff.log`). This is an L5 checkpoint on that
commit. No push or history rewrite occurred.

Step 2b proceeds independently of Native interop:

1. Add the portable protocol, compiled with 3.8.4 for its Scala 3 variants and
   with the repository's two Scala 2 versions, without izumi dependencies.
   Verify its wire fixtures on JVM/JS/Native and its published consumer boundary.
2. Add the plain WordSpec front end and base runner. Verify synchronous/Future
   execution, declarative discovery, stable path IDs, duplicates, and independent
   repeated/concurrent session factories and finalization through public fixtures.
3. Move fundamentals suites to unpublished test-only projects, retaining the
   existing JS/Native execution during the transition, and plug the preserved
   distage engine/front ends into the same runner contract. Verify the project
   graph, suite counts, dependency boundaries, and the import-only compatibility
   fixtures before claiming the parent step.

The generated protocol projects use Scala 3.8.4 explicitly on JVM/JS/Native,
alongside 2.13.18 and 2.12.21. They have no izumi project dependency and use the
repository's pinned Circe core/parser. Portable named IDs retain target, suite,
path segments and variant; the data model includes catalogue identity, effective
settings, overrides, structured failures and correlated events. Schema 1 uses
compact JSON frames on an explicit channel; actual channel transports remain
later work. Locations document missing columns explicitly rather than inventing
them. Frames reject raw line separators, unknown versions/kinds, malformed
identities and oversized input.

The first `distage-test-protocolJVM/testFull` exits 0 on the observed compiler
3.8.4 (`2b-protocol-jvm-first.log`), passing 42 wire checks. A strengthened
producer-boundary reproduction then exits 1 for the expected invariant:
`Producer must reject an invalid explicit selection before emitting a protocol
frame` (`2b-protocol-producer-invariant-before.log`). Encoding had no schema
validation while decoding did. Encoding now uses the same schema decoder to
validate its generated payload before returning the frame, so malformed
internal records cannot be emitted as valid protocol messages.

The first corrected producer matrix exits 1 after its JVM run passes 47 checks:
the JS round-trip of `Long.MaxValue` fails (`2b-protocol-scala3-matrix.log`).
The instrumented JS reproduction also exits 1 and captures the decoded value
as `Left(ProtocolDecodeError(Long))`
(`2b-protocol-js-long-diagnosis.log`). Circe's documented native-JavaScript
number parsing cannot preserve arbitrary 64-bit integers; this is the limitation
tracked in [circe/circe#393](https://github.com/circe/circe/issues/393) and the
[parsing documentation](https://circe.io/circe/parsing.html).
The protocol now represents durations and sequence numbers as decimal strings,
preserving the full supported integer domain rather than narrowing the fixture.
The unchanged boundary-value round-trip passes on JVM/JS/Native, each with 47
checks and the identical golden schema frame
(`2b-protocol-scala3-corrected.log`, exit 0). That command also publishes all
three protocol variants from the 3.8.4 compiler and exports their resolved
Compile dependency classpaths. Scala 2 and independent consumers are running;
neither is claimed complete yet.

`bash /srv/nvme/tmp/izumi-impl/2b-protocol-scala2-matrix.sh` subsequently exits 0
(`2b-protocol-scala2-matrix.log`). Both Scala 2 versions run JVM/JS/Native, each
passing 47 checks and emitting the same golden frame; Native outputs are cleaned
before linking. The command publishes all six Scala 2 variants and exports their
Compile classpaths. Together with the corrected Scala 3 producer matrix, all
nine protocol lanes now pass.

The first independent 3.8.4 consumer exits 1 during build-definition compilation
(`2b-protocol-published-consumer-first.log`), before any consumer source runs:
the SBT 2 classloader fixture's `.files` classpath extension requires an explicit
`xsbti.FileConverter`. Inspection of the pinned
[SBT 2.0.9 source](https://github.com/sbt/sbt/blob/v2.0.9/main/src/main/scala/sbt/Defaults.scala)
confirms the public `fileConverter` key and the extension's `Seq[Path]` result.
The fixture supplies that public key as a given and uses path strings; it calls
no SBT private API. The corrected standalone runtime and classloader checks are
outstanding at this point.

That corrected consumer executes its JVM golden/request checks, then exits 1
with `ClassNotFoundException` for the JVM-only classloader fixture
(`2b-protocol-published-consumer-corrected.log`). The source-directory inspection
exits 0 (`2b-protocol-consumer-jvm-source-dirs.log`): CrossType.Pure's JVM base is
`consumer/.jvm`, and the configured extra directory incorrectly points below
that base. Its source list contains only the shared consumer. The corrected
directory is the base's parent followed by `src/main/scala-jvm`, where the actual
driver source resides. A fresh four-compiler standalone matrix is running;
the prior partial command is not claimed successful.

The corrected `2b-protocol-consumer-matrix.sh` exits 0
(`2b-protocol-published-consumer-matrix.log`). Actual observed compiler values
are 3.8.4, 3.9.0, 2.13.18 and 2.12.21. Each lane cleans and runs all three
published consumers and executes the JVM classloader exchange: twelve
`PUBLISHED_PROTOCOL_CONSUMER_OK schema=1` and four
`PROTOCOL_CLASSLOADER_CONSUMER_OK isolated=2 exchange=String` markers. This
checks the SBT 2 baseline consuming the 3.8.4 artifact, the testkit's 3.9 baseline,
and both Scala 2 coordinates without sharing producer classes.

A strengthened success/failure invariant reproduction exits 1 for the expected
`Successful test must not carry a failure` predicate
(`2b-protocol-success-invariant-before.log`). The schema had accepted a
successful status paired with a structured failure. Its result decoder now
rejects that contradictory state; the shared producer validation uses the
same check. The producer/consumer matrices are rechecked after this last
semantic correction before the protocol substep is committed.

Strict producer compilation initially exits 1 before tests or publication
(`2b-protocol-final-producer-scala3.log`). Scala 3.8.4's source-3.7 migration
warning rejects an explicit argument to an implicit parameter. Making the
private message codec implicit and allowing ordinary inference preserves the
shared Scala 2 syntax; no compiler warning suppression was added.

Independent review reproduces recursive decoding/encoding stack overflow below
the frame limit. The exact frozen-artifact compile/runtime replay is
`bash /srv/nvme/tmp/izumi-impl/protocol-review/replay-nesting-reproduction.sh`;
its adjacent `.log` exits 0 while the diagnostic probe captures
`StackOverflowError` from both paths. The probe's printed depth 512 means 512
cause edges around a leaf (root-inclusive depth 513), and its raw frame is
52,914 characters. Current-source `current-invariants-before.log` reproduces
the same failure and locates the decoder recursion in `ProtocolCodec`/Circe.
The JVM boundary fixture independently exits 1 on accepting root-inclusive
depth 33 (`2b-protocol-depth-bound-before.log`).

The correction bounds JSON containers at 128 before parsing and failure causes
at root-inclusive depth 32 during encoding/decoding. Both reject excess nesting
explicitly without truncation or an exception-catching fallback. Fixtures
exercise both boundaries, one level beyond each boundary, root-inclusive depth
512, and escaped quotes/backslashes with hundreds of quoted braces. The fixed
strict 3.8.4 producer command
`bash /srv/nvme/tmp/izumi-impl/2b-protocol-final-producers.sh` exits 0
(`2b-protocol-final-producer-scala3-corrected.log`): 60 checks on each platform,
identical golden frames, all three local publications, and resolved Compile/Test
graphs. Native is cleaned before linking.

Review also reproduces skipped-with-failure aggregate success. The fail-first
JVM fixture exits 1 on that exact predicate
(`2b-protocol-skipped-invariant-before.log`). The result codec now rejects a
skipped status carrying a failure, and `RunOutcome.successful` requires no
result-level failure even for directly constructed records. Skipping without a
failure remains a non-failing result. The 60-check fixture includes this
postcondition. Scala 2 republication and independent consumers are still
pending at this recording point.

The first final Scala 2 command runs all three 2.13.18 lanes successfully, then
exits 1 before 2.12 execution (`2b-protocol-final-producer-scala2.log`): that
compiler cannot parse escaped quotes within two interpolated fixture literals.
Triple-quoted literals preserve the same input frames. The fresh command
`bash /srv/nvme/tmp/izumi-impl/2b-protocol-final-scala2-matrix.sh` exits 0
(`2b-protocol-final-producer-scala2-corrected.log`): all six lanes pass 60
checks, publish their artifacts and export Compile/Test classpaths. Native
outputs are cleaned per compiler.

The read-only reviewer independently compiles the current protocol and passes
85 additional JVM checks (`bash
/srv/nvme/tmp/izumi-impl/protocol-review/replay-bounded-protocol-review.sh`,
adjacent `bounded-protocol-review.log`). Root-inclusive depths 1/31/32
round-trip through rejection, phase-failure and completion messages;
33/63/64/512/2048 reject. JSON depths through 128 pass and 129/1024 reject;
52 quote/backslash parity cases preserve quoted braces. Succeeded/skipped
results carrying a failure reject. This is independent evidence in addition
to the portable fixture, not a replacement for its platform matrix.

The strict Scala 3 producer is rerun after the shared fixture literal correction:
`bash /srv/nvme/tmp/izumi-impl/2b-protocol-final-producers.sh`, exit 0,
`2b-protocol-final-producer-scala3-final.log`. Each actual 3.8.4 JVM/JS/Native
lane passes 60 checks with the same golden frame and publishes locally.
Together with `2b-protocol-final-producer-scala2-corrected.log`, this is the
final source's nine-lane producer checkpoint.

The strengthened standalone consumers run against those publications:
`bash /srv/nvme/tmp/izumi-impl/2b-protocol-consumer-matrix.sh`, exit 0,
`2b-protocol-published-consumer-final.log`. Observed compilers are 3.8.4, 3.9.0,
2.13.18 and 2.12.21. Each cleans its JVM/JS/Native outputs and checks the common
wire frame, structured request, depth-32 round-trip, depth-33 rejection and
skipped-with-failure policy. There are twelve
`PUBLISHED_PROTOCOL_CONSUMER_OK schema=1 boundaries=verified` markers and four
`PROTOCOL_CLASSLOADER_CONSUMER_OK isolated=2 exchange=String` markers.

`python3 /srv/nvme/tmp/izumi-impl/2b-protocol-artifacts-and-graphs.py` exits 0
(`2b-protocol-artifacts-and-graphs.log`), inspecting nine published jars/POMs
and 18 resolved Compile/Test classpaths (251 entries). Jar CRCs, package contents,
JSIR/NIR and Scala 3 TASTy pass. There is no izumi, ScalaTest, Scalactic or
scalatestplus dependency, and no 3.9 language dependency in a 3.8.4 producer.
The reviewer separately inspects the nine jars/POMs and observes Scala 3.8.4
TASTy headers for all three Scala 3 artifacts.

Regeneration with `--js --native` exits 0 and is SHA-256 idempotent for all three
generated files; `git diff --check` exits 0
(`2b-protocol-generator-final.log`, `2b-protocol-generator-idempotence.log`).
The boundary metadata check at 2026-10-02 05:39:56 UTC still receives HTTP 404
for both Native interop metadata URLs
(`2b-protocol-interop-release-boundary.log`). Independent runner work continues.

This checkpoint does not finish step 2b or O.12's final evaluation. Real process
transports remain outstanding. Review also identifies a future assertion adapter
requirement: the current wire diagnostic's location does not preserve the
fundamentals relative/absolute/virtual identity and range/point/unavailable span
variants. Preserve these distinctions when implementing that adapter; do not
claim transported structured diagnostic fidelity from this codec checkpoint.

The required read-only review closes with no unresolved concrete defect in this
codec-only checkpoint. Its final log inspection confirms all twelve consumer
and four classloader markers and all four observed compiler lanes. The reviewer
also reruns its 85-check boundary probe against the fresh published JVM jar
(`bounded-published-protocol-review.log`, exit 0), rather than only scratch
producer classes. The real-transport and diagnostic-adapter limitations above
remain outstanding; no parent step or final evaluation is marked done.


## Step 2b: base plain-runner implementation in progress (2026-10-02)

The verified protocol checkpoint is committed locally as
`6c331dc6e27b9eeb74008e644c0d6a3a4cb20790`. No push was performed.

The base runner is a separate artifact depending only on fundamentals assertions
and the protocol. Its plain front end retains `should`/`must`/`can`/`in` and
supports synchronous and `Future` bodies. A registration context owns typed
execution-provider instances; a run session owns factory invocation,
registration, cancellation and event sequencing. Execution plans are scoped to
the session that produced them, and terminal IDs are checked against selection.
Provider completion means resource finalization has completed; the controlled
fixture gate checks this without sleeps.

The first generated build exits 0 (`2b-base-runner-generator-first.log`), and
`distage-test-runnerJVM/compile` exits 0 on 3.9.0
(`2b-base-runner-compile-first.log`). No behavioral acceptance is claimed from
compilation. JVM behavioral fixtures are running. Test-only fundamentals
projects, the distage provider/front end, transports, rich structured assertion
mapping and host integrations remain outstanding.

The first JVM behavioral command exits 1 at the assertion-report oracle
(`2b-base-runner-jvm-first.log`). An instrumented replay also exits 1
(`2b-base-runner-assertion-diagnosis.log`) and records the actual failure tree:
Scala Future's `ExecutionException("Boxed Exception")` retains the original
`AssertionFailure` as its cause, with the source/expression/value diagnostic
message intact. This agrees with the official
[Scala Future exception semantics](https://docs.scala-lang.org/overviews/core/futures.html#exceptions).
The original fixture incorrectly required AssertionFailure to be the root
exception. Its corrected oracle examines the retained cause tree; production
exception reporting was not changed or arbitrarily unwrapped. Structured
assertion wire mapping remains explicitly outstanding.

With the corrected causal-tree oracle, JVM execution reaches and fails the
adversarial selection predicate (`2b-base-runner-selection-before.log`, exit 1):
a provider reintroduces an unselected test from its registrations. Resolution
now validates returned IDs against the selected provider test set. The first
three-platform run then exits 0 on actual Scala 3.9.0
(`2b-base-runner-scala3-first-matrix.log`), passing 27 checks on JVM/JS/Native.
This does not include later provider-boundary corrections.

Read-only review independently reproduces seven additional boundaries in
`/srv/nvme/tmp/izumi-impl/base-runner-review/runner-boundaries-before.log` and
`runner-ownership-before.log`; adjacent `replay-runner-boundaries.sh` and
`replay-runner-ownership.sh` retain exact commands and the frozen pre-correction
runner jar. Observations: resolver exceptions escape synchronously; discovery
accepts another registered suite's test identity; a negative-duration successful
result cannot encode; unselected events reach the sink; providers exchange
result IDs while aggregate completeness passes; provider `Finished` precedes a
controlled finalizer; and observed `PhaseFailed` is lost from successful outcome.

Corrections restrict provider event capability to test starts/completions and
phase failures, with session lifecycle and run correlation owned by the session.
Each execution plan has its own event/result validation and reconciliation.
Invalid payloads and identities fail as transport errors before forwarding;
reported phase failures survive provider summaries. Schema payload validation
is shared with the codec without imposing frame size on in-process catalogues.
Discovery checks each test's owning suite, and resolver exceptions become
Selection failures. Independent repros and portable regressions are being
rerun; these corrections are not yet claimed fully verified.

The strengthened portable boundary fixture initially exits 1 before execution
because its helper parameter shadows `ExecutionPlan.execute`
(`2b-base-runner-failure-multiplicity-before.log`). Renaming the parameter and
removing the unnecessary forwarding method corrects that compile-time type
error; the multiplicity reproduction is rerunning separately.

The corrected first JVM fixture passes 27 checks
(`2b-base-runner-provider-corrected-first.log`, exit 0). The independent current
source probe passes 15 further boundary assertions
(`base-runner-review/runner-corrected-boundaries.log`), including resolver/suite
ownership, wire-valid invalid-duration rejection, unselected reports/results,
retained phase failures and finalization ordering. The negative capability
compile independently exits 1 with E007 for both provider `RunEvent.Finished`
and forged-run `TestStarted` arguments (`replay-provider-capability-rejection.sh`,
`provider-capability-rejection.log`). Providers now cannot express those signals.

Both the portable fixture and independent probes then reproduce structural
failure deduplication. The JVM fixture exits 1 on lost occurrence multiplicity
(`2b-base-runner-failure-multiplicity-repro.log`). Independent observed counts:
one provider returning two equal failures produces outcome1/event1; two reports
plus two returned failures also produce 1/1; two distinct providers returning
one equal failure each produce outcome2/event1. Occurrence-based reconciliation
now consumes only matching reported occurrences, preserves every additional
returned occurrence, and forwards each actual report. The corrected platform
matrix is running; this correction is not claimed verified yet.

The occurrence correction passes 46 checks on each actual Scala 3.9.0 platform
(`2b-base-runner-provider-scala3-corrected.log`, exit 0). Independent review
then reproduces mutable execution-plan ownership and invalid run identities
(`runner-plan-mutation-before.log`, exact `replay-runner-plan-mutation.sh`):
mutating two plan getters after planning swaps ownership and reports success;
empty run identity invokes one body and forwards seven invalid events.
Execution-plan tests are now immutable vals, and the session retains the
original resolved per-plan snapshot for reporting. Both execution entry points
reject empty run identities with a failed Future before discovery/planning or
reporting. Schema-only validation versus channel budget is documented and has
portable boundary fixtures.

The first strict final matrix exits 1 before test execution/publication
(`2b-base-and-protocol-strict-scala3-first.log`): `Map.put` returns an unused
Option under fatal warnings. `Map.update` expresses the intended Unit-returning
mutation. A focused strict JVM run is verifying the remaining sources before
retrying the matrix. No warning suppression was added.

The focused strict JVM command exits 1 before execution
(`2b-base-runner-strict-jvm-second.log`), finding three bare `in ()` calls whose
public method was not declared infix and one discarded discovery result. The
four DSL methods are now declared infix, preserving the requested operator
syntax, and the intentional discovery result is explicitly discarded. The
strict six-variant runner/protocol matrix is rerunning with both Compile/Test
fatal warnings; previous warning failures remain captured.

The next strict run passes JVM's 47 checks, then exits 1 before JS execution
because `setTimeout` returns an unused handle
(`2b-base-and-protocol-strict-scala3-corrected.log`). Explicitly discarding that
handle corrects the fixture warning. The six-variant rerun then exits 0
(`2b-base-and-protocol-strict-scala3-final.log`), with 47 runner checks on each
3.9.0 platform and 64 protocol checks on each 3.8.4 platform. It publishes all
six variants and captures their Compile/Test classpaths. Subsequent Scala 2
findings require rerunning the runner sources before the checkpoint is final.

The first independent published-runner consumer on 3.9.0 exits 0 on JVM, JS
and Native (`2b-base-runner-consumer-scala3-first.log`), expanding both public
registration and assertion macros and round-tripping every emitted event.
Its three markers report `bodies=2 positions=known`; discovery runs neither
body. The published Scala 2 matrix remains outstanding.

The first Scala 2 matrix exits 1 on 2.13.18 at a generated equality check for
the nested Registration case class (`2b-base-and-protocol-final-scala2.log`).
Registration does not use equality, so a private class with explicit fields
replaces the case class. The deprecated `Position.isDefined` check also becomes
an explicit comparison with `NoPosition`. The next replay compiles production
sources, then exits 1 at a fixture's ambiguous enclosing versus inherited
ExecutionContext (`2b-base-and-protocol-final-scala2-corrected.log`). Async and
plain front-end compatibility is being tested before changing that API.

Independent source review passes 18 corrected boundary assertions
(`base-runner-review/runner-corrected-boundaries-final.log`) and 131 snapshot,
run-ID and occurrence-count checks (`runner-snapshot-multiplicity-after.log`).
The 16 report/return count pairs retain multiplicities from zero through three;
mixed providers retain six equal failure occurrences. Both empty-ID execution
entry points reject before events/bodies; the request entry point also rejects
before factories/planning. Compile probes reject mutable plan getters (E164)
and provider completion/forged run events (E007). Exact adjacent replay scripts
and frozen pre-correction jars retain these independent observations. Platform
and publication results remain separate from the JVM source review.

The new portable constructor-context repro exits 1 for the expected failure
(`2b-base-runner-constructor-context-before.log`): an async suite retaining its
execution context during construction fails before registration. Independent
frozen-artifact probes also reproduce plain-suite implicit ambiguity (the
identical ScalaTest AnyWordSpec control compiles) and rejection of existing
async context overrides because the old getter is final
(`base-runner-review/ec-compatibility-before.log`, exact
`replay-ec-compatibility-before.py`).

Plain AnyWordSpec no longer supplies an implicit execution context. AsyncWordSpec
provides a public overridable implicit getter, backed by a per-suite forwarding
context. Constructors can retain that context without allocating an executor;
scheduling requires registration and delegates to the borrowed session executor.
The stored context is published through a volatile Option. The corrected async
fixture is outside the enclosing implicit scope and uses both its captured
context and the default implicit continuation context.

The first strict replay of that fixture fails before execution because an
explicit implicit argument requires `using` on source 3.7
(`2b-base-and-protocol-constructor-context-using-before.log`). Its corrected
replay exits 0 (`2b-base-and-protocol-strict-scala3-final-source.log`): 47 runner
checks on each 3.9.0 target, 64 protocol checks on each 3.8.4 target, and all six
local publications/classpaths. The Scala 2 replay also exits 0
(`2b-base-and-protocol-final-scala2-final-source.log`): the same 47/64 checks
on all six 2.13.18/2.12.21 targets and twelve publications/classpaths. The
final fixture timeout/thread-count constants and published compatibility
consumers are being checked separately before committing.

`python3 /srv/nvme/tmp/izumi-impl/2b-base-runner-artifacts-and-graphs.py`
exits 0 (`2b-base-runner-artifacts-and-graphs.log`). It checks 18 published
jars/POMs and 36 Compile/Test classpaths (546 entries), preserving the protocol's
3.8.4 boundary and the runner's assertions/protocol dependency bound. CRCs,
JSIR/NIR/TASTy and fixture/package exclusion pass; Scala 2 macro reflection is
Provided. No graph contains ScalaTest, Scalactic or scalatestplus.

`bash /srv/nvme/tmp/izumi-impl/2b-base-runner-runtime-final.sh` exits 0
(`2b-base-runner-runtime-final.log`), rerunning the final fixtures with fatal
Test warnings on 3.9.0/2.13.18/2.12.21, each JVM/JS/Native: nine markers,
47 checks each. Native outputs are cleaned before each compiler lane. The host
reports `nproc=48`; fixture executors deliberately use four named worker threads
and named 30-second completion/shutdown deadlines.

`bash /srv/nvme/tmp/izumi-impl/2b-base-runner-consumer-matrix.sh` exits 0
(`2b-base-runner-published-consumer-final.log`), cleaning independent JVM/JS/
Native outputs for 3.9.0, 2.13.18 and 2.12.21. Nine markers report
`bodies=4 positions=known context=constructor+override`. These consumers expand
published registration/assertion macros and execute a synchronous body, a plain
Future using its caller's implicit context, an async Future using a context
retained at construction, and an async context override. Discovery executes
none of them. Each event round-trips through the published protocol; all nine
resolved consumer classpaths are also captured.

The protocol consumer matrix reruns against the new publications:
`bash /srv/nvme/tmp/izumi-impl/2b-protocol-consumer-matrix.sh` exits 0
(`2b-base-protocol-published-consumer-final.log`), with twelve consumer markers
and four isolated-loader String exchanges on actual 3.8.4/3.9.0/2.13.18/2.12.21.

The step-boundary release check at 2026-10-02 07:05:32 UTC still receives
HTTP 404 for both required Native interop metadata URLs
(`2b-base-runner-interop-release-boundary.log`). Independent JVM bootstrap,
test-only project and distage-provider work remains and continues.

Final regeneration with `JAVA_HOME="$JDK21" bash sbtgen.sc --js --native`
through `direnv exec .` exits 0 (`2b-base-runner-generator-final.log`). All
three generated files retain their SHA-256 digests
(`2b-base-runner-generator-idempotence.log`), and `git diff --check` exits 0.
No generated file was hand-edited.

Independent EC review passes the same 16 runtime checks against fresh Scala 3
source classes, fresh Scala 2.13 source classes and the published Scala 3 jar
(`base-runner-review/ec-compatibility-after.log`,
`ec-published-and-scala213.log`; adjacent `replay-ec-compatibility-after.py` and
`replay-ec-published-and-scala213.py`). The checks cover trait constructor
capture, a stable per-suite delegate, zero discovery dispatch, pre-registration
execute/reportFailure rejection, forwarding to each session's borrowed context,
rebinding rejection, override scheduling and plain caller-context usage.
The corrected pinned ScalaTest Async control fails for the enclosing-context
ambiguity too (`async-outer-scalatest-control-corrected.log`), so that fixture
ambiguity is not claimed to be a library regression.

The required read-only review directly reruns the artifact/graph verifier and
reads all final producer/runtime/consumer markers. It closes this plain-runner
checkpoint without an unresolved concrete defect. This checkpoint does not
finish step 2b or any final evaluation: unpublished fundamentals test projects,
the framework bootstrap, distage execution provider/front end and session-owned
environments, structured assertion wire fidelity, real transports and later
host integrations remain outstanding.

## Step 2b: JVM framework bootstrap in progress (2026-10-02)

The plain-runner checkpoint is committed locally as
`527ced5dc74154982c065ff9ac78cf77e15c89c5`; the working tree is clean after
that commit. No push was performed.

Next sub-step: implement the target-side JVM test-classpath bootstrap, with one
session per `Runner.tasks` selected group and ordinary per-suite task handlers.
Verify first-task launch without an all-task barrier, concurrent task execution,
handler lifetime, failed test/run status and resource/executor release. Then use
the verified bootstrap for unpublished fundamentals test projects. The host
plugins and JS/Native framework adapters remain later work under their fixed
evaluation points.

The published Maven metadata for `org.scala-sbt:test-interface` reports release
1.0. Official v1.0 Runner/Task sources are downloaded to
`/srv/nvme/tmp/izumi-impl/test-interface-docs/` and read: a Runner may receive
multiple task groups; `done` makes it spent, prohibits subsequent task requests,
and must await logging/reporting completion. This production implementation will
use the public interface, not copy the measured spike's implementation.

The first bootstrap generator exits 1 because the new version was added to
project Versions but omitted from the generator's own V aliases
(`2b-bootstrap-generator-first.log`). A prematurely started compile consequently
exits 1 at the missing `sbt.testing` import
(`2b-bootstrap-compile-first.log`); this is not evidence about framework behavior.
Adding the required alias corrects generation
(`2b-bootstrap-generator-corrected.log`, exit 0). The new production SDK
dependency is JVM-only; JS/Native sources retain their existing dependency graph.

The initial bootstrap accepts explicit catalogue identity flags and ordinary
suite selectors. Other selectors reject explicitly while the application/plugin
selection integration is pending. Per-task projections buffer inactive suites
and serialize active handler calls. Session completion waits for its owned
executor to terminate, and Runner.done waits for active task lifetimes. These
are intended postconditions; the first strict JVM fixtures are still running.

The first strict fixture command compiles production sources, then exits 1
because its parallel-test Await result is intentionally discarded without an
explicit discard (`2b-bootstrap-jvm-fixtures-first.log`). The corrected fixture
then reaches behavioral execution and exits 1 at its assertion-message check
(`2b-bootstrap-jvm-fixtures-corrected-first.log`). An instrumented replay
(`2b-bootstrap-assertion-projection-before.log`) observes a ProjectedFailure
with no Java cause, while the retained protocol Failure has the original
AssertionFailure beneath Future's Boxed Exception. Projection now builds the
Throwable cause/suppressed tree from the validated protocol tree; the oracle
checks that tree rather than requiring the assertion at its root.

Independent review reproduces two handler boundaries against frozen pre-fix
source (`bootstrap-review/attach-before.log`, `live-handler-before.log`, exact
`replay-bootstrap-boundaries-before.py`). A buffered handler exception leaves
the handler attached after its task returns, permitting a second callback. A
live handler exception ends its task and shuts down the executor while another
test owns a pending resource: acquired1/released0, followed by
RejectedExecutionException when its release gate opens. Callback errors are now
retained and disable further calls to the failed handler, while engine
completion/finalization continue. The task rethrows the original callback error
after that completion; the invocation records Transport failures for SDK
delivery errors. Attachment is enclosed by cleanup, and cleanup clears rejected
buffers without masking the original exception.

A separate Java static-initializer reproduction observes
ExceptionInInitializerError on the first task, NoClassDefFoundError on the
second, and zero SDK error events (`launch-2-before.log`, exact
`replay-bootstrap-launch-before.py`). Reflection-boundary LinkageErrors now
become Discovery failures, so the selected group's common launch result is
memoized. The observed initializer count is one; no repeated initializer
execution is claimed.

The independent healthy concurrency probe passes four fresh suite instances,
50 actual bodies and 50 callbacks in two concurrent groups
(`concurrency-1-before.log`). Its entry counter precedes the handler lock and
observes maximum overlap1. The repository fixture now adopts that independent
entry counter; its earlier inside-lock flag could not detect overlapping caller
threads. Corrected boundary/runtime verification remains in progress.

Further fail-first replays against frozen intermediate source reproduce host
caller interruption and SDK InterruptedException/LinkageError boundaries
(`interruption-1-before.log`, `boundary-5-after-1.log`,
`callback-interrupted-after-1.log`, `callback-linkage-after-1.log`, with adjacent
exact replay scripts). Host interruption returns before a pending resource is
released and executor shutdown prevents its finalizer. An SDK LinkageError
escapes the Future callback and leaves invocation completion pending. Completion
now uses a once-only Promise; per-caller interruption requests cancellation,
continues waiting for finalization/executor termination, then propagates the
original interruption and restores its flag. SDK callback InterruptedException
and LinkageError are retained at the delivery boundary and propagated after
resource release. Fatal VM failures are not converted into domain successes.

The follower-task interruption reproduction also fails against intermediate
source (`waiting-interrupt-1-after-1.log`): the follower returns before resource
release. Its initial scratch compile tokenization error is preserved separately
and is not claimed as the runtime reproduction. Against frozen after-2 source,
ten independent replays exit 0 (`boundary-1..10-after-2.log`, exact
`replay-bootstrap-boundaries-after-2.py` and command JSON). Root inspected the
actual logs: owner and follower interruptions await released1; Runtime,
InterruptedException and LinkageError callback probes retain the original
exception and await released1; launch errors yield two suite error events;
handler attachment clears on return; 50 concurrent events reach handlers with
maximum overlap1.

A buffered callback error observed after engine completion initially leaves the
memoized group successful and a third suite reporting only Success
(`late-callback-1-after-1.log`, exit 1). Each result read now merges current SDK
delivery failures into the cached engine outcome without relaunching it. The
replay observes successful=false/failures1 and a later Success+Error projection
(`boundary-8-after-2.log`, exit 0). The repository SDK fixture adds this regression
and passes 22 checks on 3.9.0/2.13.18/2.12.21
(`2b-bootstrap-jvm-late-callback-corrected.log`,
`2b-bootstrap-jvm-scala2-first.log`). Those runs precede the final cancellation
correction below and are not the final-source verification.

A cleanup-phase interruption reproduction correctly drains the executor worker
and restores interruption, but initially loses the cancellation after forming
the engine outcome (`shutdown-interrupt-1-after-2.log`, exit 1: released1,
cancelled=false/successful=true). Result reads now preserve invocation
cancellation observed during cleanup as well. The strict Scala 3 final-source
verification and independent corrected cleanup replay are in progress.

The bootstrap fixtures are Behavioral-Active Blackbox-Group checks through the
public SDK. JVM `testFull` now runs them after the 47-check base fixture has
released its executor. An attempted combined strict Scala 3/Scala 2 command
retained `-Wunused:all` after switching compilers; it exits 1 on 2.12's invalid
option (`2b-bootstrap-jvm-testFull-regression.log`). Separate compiler-appropriate
commands replace that invalid check; no production correction is inferred from
the compiler-option failure.

An independent published consumer in `test-fixtures/framework-consumer` compares
per-body CREATE_NEW execution records with exact JUnit suite/test identities.
The first SBT 2 fixture build rejects omitted PathFinder.get parentheses
(`2b-bootstrap-host-sbt2-scala3-first.log`). After that correction, it reaches
15 actual failed bodies because its preparation used SBT 2's relocated target
while the explicitly supplied audit path used the fixture's ordinary target
(`2b-bootstrap-host-sbt2-scala3-build-corrected.log`, XML NoSuchFileException).
Preparation, verification and fork arguments now use the same required audit
path; the corrected 15-body/15-report run exits 0
(`2b-bootstrap-host-sbt2-scala3-audit-corrected.log`). These are harness failures,
not bootstrap defects.

`python3 test-fixtures/framework-consumer/verify-matrix.py --artifact-version
1.3.0-SNAPSHOT --sbt-version 1.13.0 2.0.9 --scala-version 3.9.0 2.13.18 2.12.21
--evidence-dir target/native-testkit-evidence` exits 0 on the intermediate
published jars (`2b-bootstrap-host-matrix-first.log`): six lanes, eight cases
each. Cases verify five suites/15 bodies under default scheduling, two explicit
suites/six bodies, the repeated selected request, serial scheduling, one wildcard
suite/three bodies, a host task limit of one, forked partial groups and forked
complete groups. Exact actual/report sets agree in all 48 cases. Only the target
framework is registered. These Behavioral-Active Effectual Good-Communication
checks do not establish DI sharing, plugin invalidation, streaming, or the full
step-2d matrix. Final-source publication and host replay remain pending.

## Step 2b: verified JVM bootstrap checkpoint (2026-10-02)

The final cleanup replay against frozen current source exits 0
(`bootstrap-review/shutdown-interrupt-0-after-3.log`, exact
`compile-bootstrap-after-3.sh` and
`replay-bootstrap-shutdown-interrupt-after-3.py`): the task does not return before
release, released1, original InterruptedException, caller flag restored,
cancelled=true/successful=false, later Success+Error. Root reads the actual log.
The read-only reviewer closes all reproduced source defects and finds no
remaining concrete bootstrap defect; final artifact/ledger review follows below.

Final producer commands, each through `direnv exec . sbt -java-home "$JDK21"
-batch -J-Xmx6G`, exit 0:

- Scala 3.9.0 JVM: session-set `LocalProject("distage-test-runnerJVM") / Compile
  / scalacOptions` and `Test / scalacOptions` add `-Wunused:all`/`-Werror`, then
  `distage-test-runnerJVM/testFull; distage-test-runnerJVM/publishLocal; show
  distage-test-runnerJVM/Compile/dependencyClasspath; show
  distage-test-runnerJVM/Test/dependencyClasspath`
  (`2b-bootstrap-jvm-scala3-final-source.log`). It passes base47 and bootstrap22.
- `bash /srv/nvme/tmp/izumi-impl/2b-bootstrap-final-scala2.sh` runs
  `++2.13.18` and `++2.12.21`, each platform's runner `testFull`, JVM
  `publishLocal`, and each Compile/Test dependency classpath
  (`2b-bootstrap-final-scala2.log`). All six base47 runs and both bootstrap22
  runs pass. The script records exact compiler/project commands.
- Scala 3.9.0 `distage-test-runnerJS/testFull` and
  `distage-test-runnerNative/testFull`, with each compiler version and
  Compile/Test dependency classpath shown
  (`2b-bootstrap-final-scala3-portable.log`): both base47 runs pass.

All three final JVM jars are published before the final independent host replay.
The same `verify-matrix.py` command above uses `--evidence-dir
target/native-testkit-evidence/bootstrap-host-final`; its aggregate log is
`2b-bootstrap-host-matrix-final.log`, and the six adjacent lane logs preserve
each exact host result. Exit 0, six lanes, 48 independently checked cases,
486 actual body records and 486 matching reported test cases. The target
bootstrap alone serves the exercised in-process and forked plain-suite cases;
this does not close the broader 2d.20 matrix or justify host substitution.

`python3 /srv/nvme/tmp/izumi-impl/2b-bootstrap-artifacts-and-graphs.py` exits 0
(`2b-bootstrap-artifacts-and-graphs.log`): nine runner artifacts, 18 fresh
Compile/Test classpaths, 301 entries, six JVM SDK classpaths. All POM izumi
dependencies remain exactly assertions/protocol; there are no ScalaTest,
Scalactic or distage-engine dependencies. `org.scala-sbt:test-interface:1.0`
and bootstrap classes occur only on JVM. Every bootstrap class byte matches
the corresponding final compiled class in its published jar, all jar CRCs pass,
and no fixture class is published. JS IR/Native NIR remain present. The first
verifier wrongly expected an explicit POM compile scope; Maven omits it for this
default dependency (`2b-bootstrap-artifacts-and-graphs-first.log`). Its second
iteration wrongly required the runner's own classes on Compile dependency
classpaths (`2b-bootstrap-artifacts-and-graphs-membership-before.log`). These
oracle errors are corrected against the observed POM/classpaths, not by changing
production artifacts.

Final `JAVA_HOME="$JDK21" bash sbtgen.sc --js --native` through `direnv exec .`
exits 0 (`2b-bootstrap-generator-final.log`). All three generated files retain
their pre-regeneration SHA-256 digests
(`2b-bootstrap-generator-idempotence.log`); no generated file was hand-edited.
`git diff --check` exits 0. The 2026-10-02 08:17:00 UTC release-boundary check
still observes HTTP 404 for both required Native interop metadata URLs
(`2b-bootstrap-interop-release-boundary.log`). Independent test-only project and
distage-provider work remains and continues.

This is a local JVM bootstrap sub-step, not completion of step 2b, step 2d or a
final evaluation point. Unpublished fundamentals test projects, the higher
execution provider/front end, rich assertion wire mapping, real transports and
the full host integration gates remain outstanding.

## Step 2b: unpublished fundamentals test projects in progress (2026-10-02)

The verified JVM bootstrap checkpoint is committed locally as
`58a50c3bcfadfed62643bd5bef2d26a35de90990`; the working tree is clean immediately
after that commit. No push was performed. The final read-only audit independently
reruns artifact/graph verification, reads the final producer/host results and
generated-file hashes, and reports no discrepancy. Its direct cleanup probe
against the final published Scala 3 jar passes
(`bootstrap-review/published-final-cleanup-runtime.log`, exact adjacent command
JSON): released1 before return, original interruption/flag, cancelled group,
later Success+Error. Root reads that log too.

Before relocating sources, `bash
/srv/nvme/tmp/izumi-impl/2b-fundamentals-test-projects-baseline.sh` exits 0
(`2b-fundamentals-test-projects-baseline.log`). It shows every project's compiler
and `Test/definedTests`, then runs `testFull` for platform, bio, collections,
json-circe and language on 3.9.0/2.13.18/2.12.21 and every supported platform:
42 project runs, all tests passed. BIO has JVM/JS lanes while released Native
interop remains absent. `2b-fundamentals-test-projects-baseline-snapshot.py`
captures 42 exact JUnit suite/test/status sets and 73 source/resource SHA-256
digests (`2b-fundamentals-test-projects-baseline-snapshot.log`), with original
XMLs under `/srv/nvme/tmp/izumi-impl/fundamentals-test-projects-before/`.
The initial snapshot guessed `target/out/js`; the actual JS directory is
`target/out/sjs1`, which the corrected snapshot uses. These are reported sets,
not claimed independent body counters for the pre-existing suites.

The five source/resource trees move to unpublished `fundamentals-*-test`
projects. Their test dependencies and Circe derivation source selection move
with them; resource macro filesystem strings point to the moved files. Two
existing consumers of platform test helpers now depend on the platform test
project while retaining their production dependency. A small unpublished
`fundamentals-test-support` artifact centralizes the staged front-end choice:
JVM extends the base AnyWordSpec, JS/Native extend ScalaTest until step 2e.
Eligible collections and language suites change only their import line. Other
facilities remain on the legacy framework during the staged retirement.
New JVM test projects register both frameworks and scope each framework's
arguments explicitly, including the bootstrap's required identities.

The initial moved collections compile exits 1 at
`implicitly[Factory[String, NEList[String]]] ne null`
(`2b-fundamentals-test-projects-jvm-first.log`). The initial hypothesis was that
assertion instrumentation lost an adaptation. Minimal controls rule it out:
plain Scala and Predef.assert reject the same Factory `ne` expression, and so
does Assert.assert (`assertion-universal-reference/*-before.log`, exact command
JSON). A ScalaTest control compiles and its typed tree resolves
`convertToEqualizer[Factory[...]](value) ne null`
(`scalatest-before.log`). This suite uses an inherited implicit Scalactic
facility, rather than only WordSpec/assertion primitives, and is retained on the
legacy framework for step 5. Its test body is unchanged; no assertion macro
correction is made from the rejected hypothesis.

The moved platform macro compile separately exits 1 with
NoClassDefFoundError/ClassNotFoundException for `io.github.classgraph.ClassGraph`
(`2b-fundamentals-test-projects-other-jvm-first.log`). Its former provided compiler
dependency is not exported transitively into the new test project. Adding that
same pinned ClassGraph dependency in provided scope restores the compiler
boundary. Regeneration exits 0 (`2b-fundamentals-test-projects-generator-first.log`,
`2b-fundamentals-test-projects-generator-classpath-corrected.log`). Corrected
JVM execution then exits 0
(`2b-fundamentals-test-projects-jvm-classification-corrected.log`): platform
156, BIO 415, collections 33, json-circe 7 and language 1 cases on Scala 3.9.0.
Collections executes 26 cases through the base runner and seven through the
retained legacy suite; language executes its one case through the base runner.

The independent ScalaTest runtime control also calls the inherited assertion
with a null Factory and exits 0, printing
`SCALATEST_NULL_FACTORY_WRAPPER_PASSED`
(`/srv/nvme/tmp/izumi-impl/assertion-universal-reference/scalatest-null-control.log`,
exact adjacent command JSON). Its `ne null` compares the converted Equalizer
wrapper rather than the Factory. This is an additional retirement inventory
facility and a reproduced ineffective non-null check; it is not copied into
the new assertion API. The retained suite remains byte-identical here, with
replacement assigned to step 5.
The same expression appears in the retained BIO ErrorAccumulatingOpsTest;
`2b-fundamentals-test-projects-scalactic-facility-inventory.log` captures both
source occurrences and their legacy base classes. The plan's descriptive
retirement inventory adds these two confirmed consumers; no gate or owner
decision changes.

`bash /srv/nvme/tmp/izumi-impl/2b-fundamentals-test-projects-final-matrix.sh`
exits 0 (`2b-fundamentals-test-projects-matrix-first.log`). On
3.9.0/2.13.18/2.12.21 it checks the old production projects' `Test/definedTests`,
the new projects' compiler, `publish/skip`, definitions and Test classpaths,
runs every moved test project's `testFull`, and compiles the two JVM DI
consumers of platform helpers: 42 moved test runs and six consumer compiles.
Both old and new Native project outputs are cleaned before each Native run,
as required for moved/deleted sources. JS/Native retain ScalaTest here; only
the six eligible JVM suites use the base framework.

`python3 /srv/nvme/tmp/izumi-impl/2b-fundamentals-test-projects-report-audit.py`
exits 0 (`2b-fundamentals-test-projects-report-audit-first.log`), comparing all
42 saved pre-move reports with the new reports by suite name and every case's
class, name and status. All 3,219 cases match exactly. This includes Scala 2's
extra BIO suite and its two Native Circe cases, compared against their own
baselines rather than Scala 3's set. Existing report counts are not promoted
to independent execution-counter evidence.

`python3 /srv/nvme/tmp/izumi-impl/2b-fundamentals-test-projects-source-audit.py`
exits 0 (`2b-fundamentals-test-projects-source-audit.log`). It compares each
of the 73 original file hashes with commit
`58a50c3bcfadfed62643bd5bef2d26a35de90990`, requires every old path absent and
every new path present with no extra test files, and verifies 66 byte-identical
files, six import-only changes, and one resource-path-only change. Its captured
unified diffs preserve the six compatibility suites for the final 2b.10 check.
The first oracle expected 11 path occurrences; the original source has nine
(`2b-fundamentals-test-projects-source-audit-oracle-first.log`). Correcting
that measured count changes no production/test source.

`bash /srv/nvme/tmp/izumi-impl/2b-fundamentals-test-projects-consumers-and-publication.sh`
exits 0 (`2b-fundamentals-test-projects-consumers-and-publication-first.log`).
It requests `testFull` on distage-core and extension-plugins, JVM/JS, on all
three compilers: twelve requests with nine nonempty test lanes. The observed
summaries are core JVM/JS 400/335 on Scala 3, 366/302 on 2.13, and 365/301 on
2.12; plugin JVM runs seven on each compiler, and its JS lanes have no tests.
In total 2,090 succeed, zero fail and two cancel. Both cancellations are the
unchanged FunctoidTest constant-type case's explicit Scala >= 2.13 `assume`
precondition on 2.12, one per platform. The script then invokes `publishLocal`
for the seventeen supported new project variants on each compiler: 51 actual
publication requests. A pre-request local-Ivy directory snapshot is empty;
`2b-fundamentals-test-projects-publication-audit.py` exits 0 afterwards
(`2b-fundamentals-test-projects-publication-audit.log`), verifying no new helper
or test-project artifact directory exists.

`2b-fundamentals-test-projects-matrix-audit.py` exits 0
(`2b-fundamentals-test-projects-matrix-audit.log`): 42 empty old-project
definition results, 51 true skip flags, fourteen compiler observations per
compiler, and 42 resolved new Test classpaths. Each contains its production
module and test support, with the base runner only on JVM and no higher distage
module. It also reconciles the consumer summaries above.

Plain `show buildDependencies` prints only an object identity, not the graph.
The pinned SBT 2.0.9 binary's inspected BuildDependencies API exposes
`classpath` (`2b-fundamentals-test-projects-build-dependencies-api.log`).
`2b-fundamentals-test-projects-observed-graph.sh` exits 0
(`2b-fundamentals-test-projects-observed-graph-first.log`), extracting every
actual project and scoped dependency through a session-only setting.
`2b-fundamentals-test-projects-graph-audit.py` exits 0
(`2b-fundamentals-test-projects-graph-audit.log`): 107 nodes, 208 distinct
scoped dependency records, 206 endpoint pairs, no cycle, fourteen test project
variants and three helper variants, and preserved lower-layer bounds.

The first graph oracle confuses records with endpoint pairs
(`2b-fundamentals-test-projects-graph-audit-oracle-first.log`). The two extra
records are unchanged distage-framework JVM/JS -> extension-plugins mappings
with distinct Test configurations. Its next over-strong assumption requires
every inbound edge to a new project to start in Test
(`2b-fundamentals-test-projects-graph-audit-docs-assumption-first.log`), whereas
the existing unpublished microsite intentionally depends on all artifacts for
documentation. Those six JVM Compile mappings sit above the runner layers and
do not violate the fixed acyclicity or lower-layer requirements. The corrected
oracle validates exactly that documentation aggregation and requires all other
inbound new-project mappings to start in Test. No production code, plan or
acceptance item changes to accommodate either oracle correction.

Final regeneration with `direnv exec . sh -c 'export JAVA_HOME="$JDK21";
exec bash sbtgen.sc --js --native'` exits 0
(`2b-fundamentals-test-projects-generator-final.log`). SHA-256 comparisons
verify all three generated outputs are unchanged
(`2b-fundamentals-test-projects-generator-idempotence.log`). The checkpoint's
09:21 UTC Maven metadata check still returns HTTP 404 for both released Native
interop artifacts (`2b-fundamentals-test-projects-interop-release-boundary.log`);
independent higher-layer work continues.

The required read-only reviewer independently reruns the 73-file/source and
42-report comparisons, observes publication and generator postconditions, and
compares every static generated dependency record with the actual SBT graph.
Its initial count of 206 refers to endpoint pairs; its reconciliation confirms
the 208 scoped records and the two unchanged pairs with multiple configurations.
It finds no unresolved checkpoint defect and verifies that this ledger does not
claim parent-step or final completion. `git diff --check` passes. This section
records the verified fundamentals-test-project checkpoint in the local commit
containing it; the exact hash is recorded at the next checkpoint. No complete
step-2b or final evaluation is claimed here.

## Step 2b: verified session-owned environment construction checkpoint

The preceding verified test-project checkpoint is local commit
`59534b6d5709b66cb327531cd5dd58983cacd1fa` (2026-10-02). The working tree is
observed clean after that commit. Nothing is pushed.

Before changing environment construction, `bash
/srv/nvme/tmp/izumi-impl/2b-session-environment-legacy-repro.sh` compiles a
minimal independent-owner control against the current core, then exits 1 for
the expected runtime assertion (`2b-session-environment-legacy-before.log`):
`sameEnvironment=true firstLoads=2 secondLoads=0 secondDefaultRetained=false`.
Both new owner objects receive the first environment/default module because
the legacy companion caches omit the loader/default-module identities and
outlive the owners. This demonstrates why a new session cannot use that path;
it does not schedule a correction to the released legacy runner.

The implementation separates stateless environment construction into
TestEnvironmentFactory and adds SessionTestEnvironment with an instance-owned
cache keyed by explicit configuration, loader, roles, merge strategy, effect
tag and default module. Legacy DistageTestEnv delegates construction while
retaining its cache policy; its lazy default-module callback remains after
plugin loading to preserve that ordering. Pure reflective bootstrap definitions
move to the factory companion, and TestPlanner uses those definitions without
initializing the legacy cache companion. This is preparation for the higher
execution provider. Items 2b.6/7/8/11 and O.18 remain outstanding; their complete
provider, discovery and resource-ownership boundaries are not established by this
environment-only checkpoint. No parent-step or final completion is claimed.

The required read-only reviewer finds a defect in the first cache key:
PluginLoader and PluginMergeStrategy use value equality even though they are
executable collaborators. Its independent `replay-loader-identity.py` and
`replay-merge-identity.py` under
`/srv/nvme/tmp/izumi-impl/session-environment-review/` compile successfully against
a frozen copy of the published first implementation, then each exits 1:
distinct equal-valued collaborators produce `sameEnvironment=true`, zero calls
to the second collaborator and `secondDefinitions=false`. Adjacent
`*-identity-before-{commands.json,compile.log,runtime.log}` preserve those
commands and failures. Before changing the key, the expanded common fixture
also compiles and fails at the expected equal-loader runtime assertion with
`direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G
"distage-testkit-coreJVM/testFull"'`
(`2b-session-environment-equal-collaborators-before.log`, exit 1).

The correction wraps just these two fields in typed reference-identity keys,
using `eq` and `System.identityHashCode`. Configuration, roles, effect tag and
module retain their value-domain equality. The fixture now requires each
distinct equal-valued loader/strategy to retain its own definitions, receive two
initial calls, and receive no further call on an identical repeated request.
Both the actual production loader and a manual in-memory loader run the same
contract. This covers static explicit modules, not classpath package scanning.

Three separate final SBT batches exit 0 on 2026-10-02. Each uses
`direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G "$1"'
sh '<commands>'`, with these commands in order for JVM and then JS:
`distage-testkit-core<platform>/testFull;
distage-testkit-scalatest<platform>/testFull;
distage-testkit-core<platform>/publishLocal;
show distage-testkit-core<platform>/Compile/dependencyClasspath;
show distage-testkit-core<platform>/Test/dependencyClasspath`.
The Scala 3 batch first sets
`LocalProject("distage-testkit-coreJVM") / scalacOptions += "-Wunused:all"`;
the Scala 2 batches first use `++2.13.18` and `++2.12.21` respectively. Their
full captured outputs are `2b-session-environment-{scala3,scala213,scala212}-final.log`.
Each JVM/JS lane passes 26 synchronous contract checks and 64 concurrent requests
per production/dummy adapter, with an owned-executor termination or asynchronous
JS completion marker. Totals: 156 contract checks and 768 requests. No bound
fixture provider executes during environment construction. The unchanged legacy
suite passes 345 JVM and 137 JS tests per compiler: 1,446 successes, zero failures,
and 19 cancellations per lane (114 total).

`python /srv/nvme/tmp/izumi-impl/2b-session-environment-final-matrix-audit.py`
exits 0 (`2b-session-environment-final-matrix-audit.log` and summary JSON), checking
all six contract/concurrency terminals, legacy outcomes and all thirty completed
commands. The earlier single all-compiler batch is incomplete, not passing
evidence: it stops emitting output after the Scala 2.13 JS fixture. A captured
JVM thread dump shows Coursier computing dependency resolution for over eight
minutes; the exact task JVM is terminated with SIGTERM (exit 143). The capture is
`/srv/nvme/tmp/izumi-impl/session-environment-first-matrix-thread-dump.log` and
its SBT output is `2b-session-environment-matrix-first.log`. Fresh independent
compiler batches complete without dependency-policy changes. This observation
does not establish a third-party defect.

The reviewer independently replays both published probes with
`python /srv/nvme/tmp/izumi-impl/session-environment-review/replay-loader-identity-after.py`
and the corresponding merge script. Both compile and runtime exits are 0;
`sameEnvironment=false`, two second-collaborator calls, and
`secondDefinitions=true`. Before controls remain frozen. The fresh Scala 3
published jar has 187 classes, all byte-identical to current compiled output.
`audit-six-artifacts.py` under that review directory exits 0 and checks all six
published POMs/jars plus 1,779 class/TASTy/JSIR entries and exact entry sets
against compiled outputs. No fixture, ScalaTest dependency, or unpublished
test/helper output leaks into the artifacts or any of the twelve actual final
classpath displays. Production POMs retain exactly their prior framework Izumi
dependency; no new production dependency is introduced. The generated diff adds
fixture entry settings and changes no interproject dependency edge, so the
preceding observed acyclicity evidence remains applicable.

Rerunning `bash /srv/nvme/tmp/izumi-impl/2b-session-environment-legacy-repro.sh`
exits 1 after successful compilation for the same expected assertion and exact
`sameEnvironment=true firstLoads=2 secondLoads=0 secondDefaultRetained=false`
marker (`2b-session-environment-legacy-after.log`). This control verifies that
the released legacy global-cache policy remains unchanged; it does not establish
legacy session isolation. The new owner contracts establish their separate
instance-owned path.

Final `direnv exec . sh -c 'export JAVA_HOME="$JDK21";
exec bash sbtgen.sc --js --native'` exits 0
(`2b-session-environment-generator-final.log`), and SHA-256 comparisons prove all
three generated outputs unchanged (`2b-session-environment-generator-idempotence.log`).
The 10:25 UTC Maven metadata check still observes HTTP 404 for both Native interop
artifact families (`2b-session-environment-interop-release-boundary.log`). Native
core/provider coverage remains pending that release; independent higher-layer
work continues. The required read-only final reviewer confirms the failure
controls, corrected source, six-lane outcomes, artifact/classpath postconditions,
legacy control and generator evidence. Its ledger consistency finding is
resolved by retaining the individual table states and describing the parent
items as outstanding. No unresolved checkpoint defect is observed.
`git diff --check` passes. This section records the verified environment
checkpoint in the local commit containing it; its exact hash is recorded at the
next checkpoint. No complete step-2b or final evaluation is claimed here.

## Step 2b: verified session-owned plugin loading checkpoint

The preceding environment-construction checkpoint is local commit
`ea8ff5c05dcf2199a2c77375ba000695f6989e34` (2026-10-02). The working tree is
observed clean after that commit. Nothing is pushed.

`bash /srv/nvme/tmp/izumi-impl/2b-session-plugin-loader-legacy-repro.sh` adds only
scratch test sources to the JVM core through a session setting, compiles them
successfully, scans exactly its one plugin class, and exits 1 for the expected
ownership assertion (`2b-session-plugin-loader-legacy-before.log`):
`sameCachedPlugin=true sameUncachedPlugin=false`. Two fresh default-loader
objects reuse the same plugin instance through the production loader's global
package cache. Uncached scans demonstrate that fresh instances can be produced.
This is evidence against using that global cache for the new session path, not
a scheduled change to the legacy loader's public cache policy.

SessionPluginLoader keeps an instance-owned cache of full PluginConfig requests
and explicitly delegates cache misses with `cachePackages=false`. Thus the new
owner stores results locally instead of entering the delegate's global package
cache. Uncached requests always delegate. Other query fields and definition
modules are preserved; failed loads propagate their original exception and do
not populate the cache. This class is preparation for the higher provider and
does not by itself integrate the new path into that provider.

The shared fixture adds the same eight static-module cache checks against the
actual default loader and the manual in-memory loader. JVM-only checks perform
actual classpath scans and distinguish the scanned plugin objects across owners;
JS claims no package scanning. Strict Scala 3 JVM/JS compilation and the first
contract runs exit 0 (`2b-session-plugin-loader-scala3-first.log`), with 45 JVM
and 42 JS checks, unchanged environment concurrent checks and explicit completion
markers. Direct concurrent plugin-load checks are then added to ensure the
plugin cache itself suppresses duplicate loads within each owner without sharing
results between owners. Parent items 2b.6/7/8/11 and O.18 remain outstanding;
no parent-step or final completion is claimed.

The final six-lane command batch uses
`direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G "$1"' sh
'<commands>'`, with `distage-testkit-core<platform>/testFull;
distage-testkit-core<platform>/publishLocal;
show distage-testkit-core<platform>/Compile/dependencyClasspath;
show distage-testkit-core<platform>/Test/dependencyClasspath` for JVM, then JS,
first on the default Scala 3.9.0, then after `++2.13.18`, then `++2.12.21`.
It exits 0 (`2b-session-plugin-loader-matrix-final.log`): 45 JVM and 42 JS checks
per compiler (261 total), 768 environment requests and 768 direct plugin requests,
all six core publications, twelve classpath displays, and each platform's executor
or asynchronous completion marker. `python
/srv/nvme/tmp/izumi-impl/2b-session-plugin-loader-matrix-audit.py` exits 0
(`2b-session-plugin-loader-matrix-audit.log` and summary JSON), checking every
terminal and all twenty-four completed commands.

The final strict replay sets `LocalProject("distage-testkit-coreJVM") /
scalacOptions += "-Wunused:all"`, runs its `testFull` and `publishLocal`, and
repeats those settings/actions for core JS. All four actions succeed with the
same 45/42 check terminals and both concurrency pairs. The same batch then adds
the original scratch probe directory to core JVM Test, compiles it successfully,
and runs PluginOwnerCacheRepro. Its final exit 1 is the expected unchanged legacy
assertion/marker, not a fixture or publication failure
(`2b-session-plugin-loader-strict-final-legacy-after.log`). No existing legacy
runtime implementation or production dependency changes in this checkpoint;
the preceding six legacy regression lanes retain their recorded provenance.

The required read-only reviewer creates a separate real-scan consumer under
`/srv/nvme/tmp/izumi-impl/session-plugin-loader-review/`. Its initial compile
omits PluginDef's required `-Yretain-trees` option and stops at that explicit
precondition; the harness failure and exact commands remain in
`published-scan-without-retain-trees-*`. The corrected compile/runtime both exit
0. The consumer warms the legacy package cache, then verifies that the new owner
bypasses that object; it varies all six request fields, verifies exact normalized
forwarding, package exclusions, original error/retry and suspended providers,
and completes 64 actual scanning requests across two owners before confirming
executor termination. Frozen pre-strict evidence remains distinct from the
final jar/replay.

`python /srv/nvme/tmp/izumi-impl/session-plugin-loader-review/replay-all-published-scan.py`
also compiles and runs with exit 0 against the final published core and nineteen
frozen published Izumi dependency jars, with no producer class directory on its
runtime classpath. Exact commands, frozen hashes and results are in that
directory's `all-published-scan-commands.json`, `published-dependencies.json`, and
`all-published-scan-{compile,runtime}.log`. The final core jar SHA-256 is
`bbd84a36ed1f147a7dfb1f453f8d0af1dae2d5c1d10fd9a3bce3585f4859d9b7`.
The reviewer reruns `audit-six-artifacts.py` there with exit 0
(`artifact-audit-final.log`): six POMs/jars, twelve actual classpath blocks and
1,790 exact class/TASTy/JSIR entries match compiled outputs. SessionPluginLoader
is present; fixture classes, ScalaTest dependencies and unpublished test/helper
outputs do not leak. The graph and generated dependency edges are unchanged.

Final `direnv exec . sh -c 'export JAVA_HOME="$JDK21";
exec bash sbtgen.sc --js --native'` exits 0
(`2b-session-plugin-loader-generator-final.log`), with all three generated
outputs SHA-identical (`2b-session-plugin-loader-generator-idempotence.log`).
The 10:51 UTC Maven metadata check still observes HTTP 404 for both released
Native interop artifact families (`2b-session-plugin-loader-interop-release-boundary.log`).
Native core/provider coverage remains pending that release; independent
higher-provider work continues. The required read-only final reviewer confirms
the ledger/docs scope, generator hashes, artifact audit and external-boundary
control, and observes no unresolved checkpoint defect. `git diff --check`
passes. This section records the verified plugin-loading checkpoint in its
containing local commit; its exact hash is recorded at the next checkpoint.
No complete step-2b or final evaluation is claimed here.

## Step 2b: plugin-cache memoization and immutable request correction

The preceding plugin-loading checkpoint is local commit
`f4bb6ddb1d7b3f04cc503585f765d44e7f812618`. Its recorded checks did not cover
memoization across distinct complete plugin requests. A subsequent actual-engine
review disproves the assumption that disabling the delegate's package cache
preserves that behavior. The historical checkpoint above retains its provenance;
the correction described here supersedes its full-request-only cache policy.
All new captures in this section are under `/srv/nvme/tmp/izumi-impl/`, unless
otherwise stated. Earlier captures under `target/native-testkit-evidence/`
remain distinct.

Frozen published before controls in `session-plugin-cache-memoization-review/`
compile successfully and run the actual TestkitRunnerModule with two different
suites rooted in one scanned Lifecycle resource. The legacy loader shares one
acquisition/release for identical requests and requests differing only in merges,
overrides or debug. The preceding session owner instead acquires/releases twice
for those three variations, despite both bodies succeeding. Their runtime exits
are 1 for the expected sharing assertion. The permanent fixture reproduces this
before correction (`2b-session-plugin-memoization-before.log`): compilation
succeeds, then the merges sharing assertion fails with two acquisitions and two
releases. No correction existed at that failing evaluation point.

The package-domain before controls in
`session-plugin-cache-package-domain-review/` additionally establish shared
definitions across subset, overlapping and reordered enabled packages. Parent
and child package names, changed unrelated exclusions and reordered exclusions
already have distinct legacy boundaries. The correction retains those boundaries;
it neither folds ancestor packages together nor sorts exclusions.

SessionPluginLoader now creates an instance-owned PluginPackageCache and gives it
to a required loader-construction factory. Its complete-request cache remains
separate. The JVM default factory constructs a private owned implementation;
normal cached Classgraph scans reuse definitions by exact package name, whitelist
and exclusion sequence. The legacy default still uses its original global
MurmurHash-derived string key. Uncached requests always delegate, and original
request flags and module definitions reach the ordinary unary load method. This
unreleased session API is preparation for the execution provider; it does not
change the existing suite hook into a cache-factory requirement.

Two intermediate corrections are rejected by observed controls and removed.
Adding a separate cache-aware load overload bypasses existing unary load and
protected scan overrides, both directly and through map: four corrected-approach
controls fail after six baseline controls pass, in
`session-plugin-cache-dispatch-review/{before,after}/`. Changing the public
default loader's primary constructor also fails ordinary
`make[PluginLoader].from[PluginLoaderDefaultImpl]` wiring with a missing
PluginPackageCache instance (`2b-session-plugin-constructor-before.log`), after
successful compilation. The final source retains the zero-argument primary
constructor, unary virtual dispatch and protected scan hook. PluginLoader's
interface and map implementation have no final diff.

Scala 2.12 source-frozen probes then reproduce mutable-sequence retention defects
at three boundaries. Both whitelist/exclusion keys miss after caller mutation
(`session-plugin-cache-mutable-key-review/`, two owned failures versus passing
legacy controls). All four complete-request fields miss, and retained merge or
override payloads change (`session-plugin-cache-full-mutable-key-review/`, four
failures). Application and bootstrap configuration keys likewise miss for all
four fields (`session-environment-mutable-config-domain-review/`, eight mutable
failures versus eight immutable controls). Each failed runtime follows successful
probe compilation and fails for the recorded cache-reuse assertion.

PluginPackageCache snapshots its key sequences into immutable vectors.
PluginConfig.snapshot centralizes snapshots of enabled packages, disabled
packages, merges and overrides. Both SessionPluginLoader and
SessionTestEnvironment use it; the environment owner snapshots both application
and bootstrap configs before its key and the actual factory call. Frozen after
replays pass the two package controls, four full-request controls, and all sixteen
environment controls. The final shared-policy replay is
`session-environment-mutable-config-domain-after-review/`: every environment
case creates once, loads twice and returns the same instance; every loader case
loads once, returns the same result and retains original overlay payloads. Its
source-manifest and exact commands distinguish source-shadow Scala 2.12 evidence
from published-artifact evidence. Permanent fixtures cover all fourteen sequence
boundaries on each supported JVM/JS compiler lane.

The final producer matrix uses separate fresh SBT processes through
`direnv exec . sh -c 'exec sbt -java-home "$JDK21" -batch -J-Xmx6G
"<commands>"'`. Each compiler runs the following JVM actions, then the same
actions with the JS project IDs:
`distage-testkit-coreJVM/testFull;
distage-extension-pluginsJVM/testFull;
distage-testkit-scalatestJVM/testFull;
distage-extension-pluginsJVM/publishLocal;
distage-testkit-coreJVM/publishLocal;
show distage-testkit-coreJVM/Compile/fullClasspath;
show distage-testkit-coreJVM/Test/fullClasspath;
show distage-extension-pluginsJVM/Compile/fullClasspath;
show distage-extension-pluginsJVM/Test/fullClasspath`.
Scala 2 processes first select `++2.13.18` or `++2.12.21`. Scala 3 separately
adds `-Wunused:all` to both core and plugins via LocalProject settings on each
platform. Those Scala 3 settings never enter a Scala 2 process.

All three final batches exit 0:
`2b-session-plugin-memoization-complete-{scala3,scala213,scala212}-final.log`.
Each compiler passes 77 JVM and 59 JS checks. The eleven JVM actual-engine
memoization cases execute 22 bodies, acquire 14 resources and release all 14.
The sharing and distinct-scan controls have their expected counts; independent
owners remain distinct. Totals are 408 checks, 66 memoization bodies, 42 matched
acquisitions/releases, 768 environment requests and 768 direct plugin requests.
All executor/asynchronous completion markers occur. The legacy regression
suite passes 345 JVM and 137 JS tests per compiler, with zero failures and 19
cancellations per lane: 1,446 successes and 114 cancellations. The plugin JVM
suite additionally passes seven tests per compiler. The JS plugin action succeeds
but has no corresponding ScalaTest test-count claim.
`python /srv/nvme/tmp/izumi-impl/2b-session-plugin-memoization-matrix-audit.py`
exits 0 and checks every terminal, all 33 memoization cases and all 54 completed
commands (`2b-session-plugin-memoization-matrix-audit.log` and summary JSON).

Incomplete harness captures do not count as passing evidence. These include the
initial incorrect project ID, intermediate fixture syntax/import errors, a
source-shadow classpath token error, and a Scala 2.13 compile failure for missing
generic-lambda parameter types. The latter is retained as
`2b-session-plugin-memoization-scala213-fixture-type-inference-failure.log`.
Explicit Seq[String]/Seq[Module] annotations correct the eight missing parameter
types, and the complete final matrix is rerun after that fixture-only correction.
The earlier passing Scala 3 capture retains its separate name
`2b-session-plugin-memoization-scala3-before-explicit-fixture-types.log`.

The independent final production replay in
`session-plugin-cache-central-snapshot-final-review/` passes 29 controls:
22 actual-engine memoization/package-domain cases, six unary/mapped custom
dispatch cases, and ordinary zero-argument DI wiring. Exact compiler/runtime
commands, source adaptation diffs and frozen published dependency hashes remain
there. It uses published Izumi jars, with no producer class directory on its
runtime classpath. The generic dispatch controls declare their request-normalizing
policy in their construction factory; these are cache-policy controls, not the
import-only suite compatibility gate. The frozen core/plugin jars contain 236/58
class/TASTy entries, all byte-identical to final compiled output. Comparing them
with the final republished jars confirms identical binary entries and entry sets;
only X-Build-Timestamp in the manifests changes
(`2b-session-plugin-memoization-frozen-final-binary-comparison.log`).

`python /srv/nvme/tmp/izumi-impl/2b-session-plugin-memoization-artifact-audit.py`
exits 0 (`2b-session-plugin-memoization-artifact-audit.log` and summary JSON):
all twelve POMs/jars and 2,268 exact binary entries match compiled output.
Fixture/scratch classes do not leak. All 24 actual classpath blocks are checked;
core Compile/Test and plugin Compile contain no ScalaTest or unrelated test/helper
output. A core Test fullClasspath includes its own test-classes directory, which
the first audit incorrectly rejected; that failed capture is retained as
`2b-session-plugin-memoization-artifact-audit-own-test-output-first.log`.
The corrected audit permits exactly that directory on the six core Test blocks,
while retaining the rejection of all other test/helper outputs there. The plugin
Test configurations intentionally retain their legacy ScalaTest dependencies.
Production POMs introduce no dependency or graph edge: core's only Izumi
dependency remains framework, and plugins' remains core-api. The preceding
observed project-graph acyclicity evidence remains applicable to this unchanged
graph.

Final `direnv exec . sh -c 'export JAVA_HOME="$JDK21";
exec bash sbtgen.sc --js --native'` exits 0
(`2b-session-plugin-memoization-generator-final.log`). All three generated
outputs retain their captured SHA-256 values
(`2b-session-plugin-memoization-generator-idempotence.log`). The 12:33 UTC
primary Maven metadata check observes HTTP 404 for both Native interop artifact
families (`2b-session-plugin-memoization-interop-release-boundary-final.log`).
Native higher-layer verification remains pending released artifacts; independent
engine/provider work continues. Parent items 2b.6/7/8/11, O.1 and O.18 and the
import-only compatibility gate remain outstanding. This correction establishes
cache semantics and local ownership, not completion of step 2b or a final gate.

The required read-only final reviewer independently reruns both audit scripts
with exit 0, reads the six lane terminals and legacy outcomes, compares the
frozen/final binary entries, and verifies the generated hashes and release
boundary capture. One unused inherited hash field in the replay's classpath JSON
still referred to the earlier checkpoint. The original is preserved as
`session-plugin-cache-central-snapshot-final-review/classpath-inherited-metadata.json`;
the corrected metadata and replay script derive the frozen core hash from the
actual jar. `metadata-correction.json` records that compiler/runtime paths are
unchanged. The artifact-manifest's hashes were already correct; execution
evidence is unchanged. No unresolved checkpoint defect is observed.
`git diff --check` passes. This section records the verified correction in its
containing local commit; its exact hash is recorded at the next checkpoint.
Nothing is pushed.

## Step 2b: prepared core execution boundary

The preceding verified cache correction is local commit
`ef531e40dd62106cdac271570e1ffdad7951e9d9` (2026-10-02). The tree is observed
clean immediately after that commit; no push occurs.

Before changing the execution boundary, a standalone probe against frozen
published jars records the original behavior in
`/srv/nvme/tmp/izumi-impl/prepared-execution-baseline/`. Exact compiler/runtime
commands and a validated source hash remain there. Both validated controls
compile and run with exit 0. Success loads configuration once, executes two
bodies with one resource acquisition/release and emits one begin/end scope.
A deliberate release exception preserves the exact exception object, after two
successful bodies, and emits no endScope. The probe's release counter records
the release callback invocation, including the deliberate failure; it does not
assert successful finalization in that control.

DistageTestRunner now exposes plan and runPrepared over its existing typed
planner result. PreparedRun belongs to the creating runner and atomically permits
one execution. Ownership rejection precedes the single-use claim; both precede
scope reporting. The legacy run method retains beginScope before planning.
The previous execution body is extracted unchanged, including environment merging,
proceedEnv and lifecycle usage. Prepared values remain in-process state, and
callers must keep the runner's surrounding lifecycle open through execution.
Planning still loads configuration and creates bootstrap injectors; the
resource-free claim applies to application/runtime providers and test bodies.

The first strict Scala 3 compile succeeds for production but catches a draft
fixture reading out directly from the new wrapper
(`2b-prepared-execution-scala3-first.log`). Correcting that inspection exposes
an unsupported fixture classification: its missing dependency yields TestStatus
Failed, not FailedInitialPlanning. The status capture is
`2b-prepared-execution-planning-status-observation.log`. A separate original-runner
published control in `prepared-execution-missing-baseline/` compiles/runs with
exit 0 and confirms two Failed:InstantiationFailure statuses, zero bodies or
application resources, and one begin/end scope. The fixture now preserves that
provisioning classification and separately injects a configuration-loading
exception to exercise the planning boundary. No production correction is made
for the fixture's initial assumption. The corrected strict JVM run exits 0 with
116 checks (`2b-prepared-execution-domain-corrected-scala3.log`); full compiler,
platform, publication and final-review evidence follows when complete.

Final checkpoint commands use three fresh SBT processes, JDK 21, batch mode and
`-J-Xmx6G`. The Scala 3 process retains the generated Scala 3.9.0 settings and
adds `-Wunused:all` to core JVM/JS scalacOptions before the corresponding platform
actions. The other processes start with `++2.13.18` and `++2.12.21`, respectively,
so Scala 3-only options do not cross compiler boundaries. Each process runs these
ten actions, JVM then JS:

```text
distage-testkit-coreJVM/testFull
distage-testkit-scalatestJVM/testFull
distage-testkit-coreJVM/publishLocal
show distage-testkit-coreJVM/Compile/fullClasspath
show distage-testkit-coreJVM/Test/fullClasspath
distage-testkit-coreJS/testFull
distage-testkit-scalatestJS/testFull
distage-testkit-coreJS/publishLocal
show distage-testkit-coreJS/Compile/fullClasspath
show distage-testkit-coreJS/Test/fullClasspath
```

All three processes exit 0. Captures under `/srv/nvme/tmp/izumi-impl/` are
`2b-prepared-execution-scala3-final.log`,
`2b-prepared-execution-scala213-final.log` and
`2b-prepared-execution-scala212-final.log`.
`python /srv/nvme/tmp/izumi-impl/2b-prepared-execution-matrix-audit.py` exits 0
(`2b-prepared-execution-matrix-audit.log` and summary JSON). Each compiler reports
116 JVM and 98 JS checks: 642 checks total, including 234 new prepared-execution
criteria. All 36 domain controls preserve configuration count 1; successful,
body-failing and finalizer-failing executions run two bodies with one application
resource each; abandoned, provisioning-failing and configuration-failing controls
run no bodies and acquire no application resources. The original body/finalizer/
configuration exception identities and InstantiationFailure classification are
asserted in the fixture, beyond its summary markers. Foreign-owner rejection
leaves a prepared plan available to its owner, and reuse adds no scope reporting.

The existing ownership/cache controls still pass: 768 environment and 768 plugin
requests, 33 memoization controls with 66 bodies and 42 matched resource
acquisitions/releases, plus all six owned-executor/JS completion markers. Legacy
regression suites report 345 JVM and 137 JS successes per compiler, zero failures
and 19 intentional cancellations per platform: 1,446 successes and 114
cancellations overall. Thirty action completion markers are checked. No plugin
source or dependency changes in this checkpoint; its preceding six-lane evidence
retains that provenance rather than being presented as a new plugin test run.

The read-only reviewer independently probes nonempty prepared plans in
`prepared-execution-boundary-review/`: reuse after success, reuse after the
original finalizer failure, and a concurrent contender while the first body is
held at a latch. The contender fails before a second beginScope or acquisition;
the first execution subsequently finishes both bodies and releases once. Its
owned executor terminates. `prepared-execution-deferred-io-review/` additionally
checks Cats IO: constructing planning/execution actions performs no corresponding
configuration/acquisition/reporting, and evaluating the same execution action
again rejects reuse, including after finalizer failure. Initial missing
cats.mtl.LiftValue captures are preserved as the harness's omitted direct Cats
Effect dependency, with its authoritative cached POM and correction JSON; no
project dependency is changed. Both initial probe directories source-shadow the
runner over frozen prior published dependencies. Their runner source SHA-256
equals current `4215fe088b61e4aaaeec718ba4a4fd7ded9d67469475ac6e74268be83b8fb74f`.

The final independent replay in `prepared-execution-published-final-review/`
uses the newly published core jar and frozen dependencies, compiles both probe
sources byte-for-byte unchanged, and passes all five runtime controls with exit 0.
There is no source-shadowed runner and no producer class directory on its compiler
or runtime classpath. Exact argument arrays, all artifact/source SHA-256 values,
logs and the binary comparison remain there. The frozen core's 238 class/TASTy
entries exactly match current compiled output. Root inspection checks every
frozen hash, source equality, the two compiled probe sources, all dependency
classpath entries and absence of runner classes in consumer output
(`2b-prepared-execution-published-provenance-audit.log`). The success and deliberate
finalizer-failure probes retain one configuration load, one acquisition, one
release callback and two bodies; the latter preserves the original exception
and absence of endScope. A release callback counter alone is not proof of
successful finalization.

`python /srv/nvme/tmp/izumi-impl/2b-prepared-execution-artifact-audit.py` exits 0
(`2b-prepared-execution-artifact-audit.log` and summary JSON). All six published
core jars/POMs and 1,808 binary entries match current compiled output exactly,
including PreparedRun and JS IR. No fixture/probe leakage occurs. The twelve
actual Compile/Test classpath blocks contain no ScalaTest or unrelated test/helper
output; each core Test block permits exactly its own test-classes directory.
Production POMs retain framework as their only Izumi dependency and introduce no
new graph edge. Previous graph acyclicity evidence remains applicable to the
unchanged build graph.

Final `direnv exec . sh -c 'export JAVA_HOME="$JDK21";
exec bash sbtgen.sc --js --native'` exits 0
(`2b-prepared-execution-generator-final.log`). All three generated SHA-256 values
match the before capture (`2b-prepared-execution-generator-idempotence.log`).
The final primary Maven metadata requests still return HTTP 404 for both Native
interop families (`2b-prepared-execution-interop-release-boundary-final.log`).
Higher-layer Native verification awaits published artifacts; independent work
continues. This checkpoint supplies a core planning/execution seam, not the
distage execution provider, four replacement front ends, import-only compatibility
fixture, or session bootstrap/logging audit. Parent items 2b.6/7/8/11, O.1 and
O.18 remain outstanding. The acceptance checklist and plan's owner decisions are
unchanged. Final read-only checkpoint review and commit follow below.

The required read-only reviewer independently reruns the matrix/artifact audits
with exit 0 (captures `prepared-execution-published-final-review/matrix-audit-review.log`
and `artifact-audit-review.log`), rechecks all 44 frozen artifact hashes, the three
source equalities, six argument arrays and absence of runner classes/producer
directories, and verifies the current published core hash equals its frozen jar.
The reviewer also checks generated hashes, the release-boundary capture, fixture
failure identities and docs' lifecycle scope. No concrete checkpoint defect or
unsupported completion claim is observed. Root `git diff --check` passes. This
section records the verified seam in its containing local commit; its exact hash
is recorded at the next checkpoint. Nothing is pushed.

## Step 2b: higher execution provider implementation in progress

The verified prepared core seam is local commit
`21657effa5755996b946dfc16b83af1fa1121721` (2026-10-02). The tree is observed clean
after that commit. Nothing is pushed.

Before adding the provider, `distage-provider-manual-parent-probe/` under
`/srv/nvme/tmp/izumi-impl/` checks the instance-only LocatorDef parent against the
published engine. Success, deliberate finalizer failure and controlled concurrent
reuse compile/run with exit 0 and retain the prior counters, exception identity
and reporting boundaries. The initial compile omitted an explicit QuasiAsync
constructor argument; its separate failed capture is retained. These Identity
controls establish only their measured domain, not arbitrary effect support.

The new distage-testkit-runner module depends on core and the base runner, with
deferred registration/resolution and existing core planning/execution. Generated
files are produced from sbtgen/Deps.scala with --js --native. Its initial strict
SBT expression used a hyphenated project name as a Scala identifier and failed
before compiling (`2b-distage-provider-scala3-compile-first.log`); corrected
commands use LocalProject. The next compile finds an omitted explicit
SessionTestEnvironment factory (`2b-distage-provider-scala3-first.log`).

The first actual provider fixture then fails its expected resource count
(`2b-distage-provider-scala3-explicit-factory.log`). Captured engine outcomes in
`2b-distage-provider-resource-observation.log` show zero acquisitions/bodies:
the instance parent lacks the outer effect TagK binding that the legacy runner
module supplies. Runtime provisioning fails with missing Tag[MiniBIOAsync[Throwable,_]].
Its failed setup completion also violates the base protocol's start-before-failed-
completion invariant and is incorrectly recovered as Finalization. Parent
bindings and attempt reporting are corrected only after this observed failure;
their same-fixture verification is pending below.

The bootstrap source audit also observes the default factory's static-router
installation path through ModuleProvider and LogstageModule. Independent
session-level mutation, skipped-status schema and thrown-reporting callback
reproductions are underway, before any policy correction. No provider, session
isolation, front-end or compatibility acceptance item is marked done.

The corrected parent/attempt fixture runs with strict Scala 3 unused checks on
JVM and JS: 64 checks per platform, exit 0
(`2b-distage-provider-parent-attempt-corrected-scala3.log`). Adding the
process-wide-router invariant fails before correction in
`2b-distage-provider-static-routing-before.log`, with the expected planning
mutation. The owned default bootstrap disables static router setup, while the
environment factory preserves a non-default custom bootstrap instance. The
same fixture then passes 65 checks on each platform, exit 0
(`2b-distage-provider-static-routing-after-scala3.log`). This does not establish
isolation for arbitrary custom hooks.

The read-only reviewer freezes independent sources/dependencies and reproduces
three additional boundaries in `distage-provider-boundary-before-review/`:
two plans replace the global router; a precondition skip fails the existing
protocol schema because it carries a failure; and a controlled reporting
callback exception is labeled Finalization. Each corrected harness compiles
with exit 0 and each runtime fails with exit 1 for its expected invariant.
The initial harness type errors are retained separately. The byte-for-byte
probe replay in `distage-provider-boundary-after-review/` compiles/runs with
exit 0 after the default-bootstrap, skipped-payload and reporting-capture
corrections; final-source verification is still pending.

A permanent combined reporting/finalizer control exposes another boundary
before further correction: `2b-distage-provider-combined-reporting-observation.log`
has exit 1. Callback-only failure preserves Transport and waits for a held
release gate. Callback plus deliberate release failure completes cleanup but
returns only the original callback exception, omitting the finalizer exception.
The inspected MiniBIOAsync.bracketCase explicitly discards release errors when
use fails. Because the incoming effect is MiniBIOAsync, changing only the
provider's outer effect would not correct that behavior. Reporting-boundary
policy and the pre-cancelled callback path remain under investigation at this
capture; it does not establish provider completion.

### Verified provider portion and reporting boundaries

The selected policy records each NonFatal external callback failure immediately
as Transport, returns normally from that callback boundary, and lets selected
bodies and finalizers settle under the existing effect semantics. The outcome
remains unsuccessful. Identity, ordering and missing-context invariants throw
outside callback recovery. Callback failures are retained per occurrence, even
when distinct events throw the same Throwable; an independent finalizer failure
retains its Finalization phase even when it reuses that Throwable. Failed event
delivery is never retried.

The preceding controls are reproduced before each correction:

- `2b-distage-provider-precancel-reporting-isolated-before.log` exits 1 with
  the controlled cancelled-result callback exception escaping instead of an
  outcome. The recording policy corrects both execution paths.
- `distage-provider-sequence-before-review/` compiles with exit 0 and runs with
  exit 1: a sink records Started then throws, and a distinct Finished delivery
  reuses ordinal 0. RunEventEmitter now consumes an ordinal before calling the
  sink. Permanent base-session and higher-provider controls cover partial
  delivery through their actual event paths.
- `2b-distage-provider-failure-occurrences-before.log` exits 1 because distinct
  cancelled-result events lose failure occurrences through exception-object
  deduplication. Occurrence capture then returns both selected Cancelled IDs
  and both Transport records.
- `2b-distage-provider-shared-throwable-phases-before.log` exits 1 after the
  callback-only and independent-exception controls pass: sharing the Throwable
  across callback and release suppresses the Finalization record. Removing
  phase suppression preserves both failure contexts.
- `distage-provider-candidate-policy-review/occurrence-runtime.log` exits 1:
  reporting before begin is incorrectly captured as Transport. The context
  is now validated before state mutation and before callback recovery. The
  unchanged probe passes in `distage-provider-context-invariant-after-review/`;
  its other four unchanged controls also compile/run with exit 0.

Two fixture-only portability failures are preserved separately. The first JS
link reaches java.security.SecureRandom through a newly added randomUUID call
(`2b-distage-provider-reporting-policy-after-scala3.log`); it is replaced by the
existing portable UUID generator. The next JS run fails on the fixture's
default Europe/Dublin timezone (`2b-distage-provider-scala3-timezone-first.*`);
its zero-duration timing now explicitly uses UTC. Scala 2.12 rejects two
wildcard declarations in one fixture block
(`2b-distage-provider-scala212-wildcard-first.*`); the project discard helper
replaces the second declaration. No production correction is inferred from
these fixture failures.

All evidence below is under `/srv/nvme/tmp/izumi-impl/`. The reproducible matrix
driver records the exact SBT argv and commands in each `.commands.json`, uses
JDK 21/SBT 2.0.9 in batch mode and starts a fresh process per compiler:

```sh
python3 /srv/nvme/tmp/izumi-impl/2b-distage-provider-run-matrix.py scala3
python3 /srv/nvme/tmp/izumi-impl/2b-distage-provider-run-matrix.py scala213
python3 /srv/nvme/tmp/izumi-impl/2b-distage-provider-run-matrix.py scala212
python3 /srv/nvme/tmp/izumi-impl/2b-distage-provider-artifact-audit.py
```

All three final matrix logs exit 0. Each process runs base testFull on
JVM/JS/Native and higher-provider testFull on JVM/JS, publishes those five
artifacts locally and captures their Compile/Test classpaths and the observed
SBT project graph. Scala 3 adds -Wunused:all to Compile/Test in all five
projects using LocalProject; 2.13.18 and 2.12.21 use their pinned flags without
Scala 3 flag leakage. There are 20 successful task actions per process.

The primary observed counts are 94 provider checks in each of six lanes (564),
50 base checks in each of nine lanes (450), and 22 JVM bootstrap checks per
compiler (66): 1080 checks. Thirty-six logged provider domain cases cover
success, body failure, finalizer failure, configuration failure, provisioning
failure and disabled cross-test memoization. Each evaluates configuration once.
Ordinary successful sharing acquires/releases one resource across two suites;
disabled memoization acquires/releases one per test while the Pair binding
retains sharing inside each graph. Deliberate release failure counts release
attempts, not successful finalization. Additional controlled gates prove that
normal and transport-failing runs remain pending until release; simultaneous
owners retain separate configuration, registration, resource objects and gates.
Abandoned planning acquires no application resources and preserves the global
router. Pre-execution cancellation acquires no application resources. Owned
executor termination and JS completion markers occur once per lane.

Artifact/graph audit exits 0: 15 published JAR/POM variants, 30 captured
Compile/Test classpaths and 1491 binary entries match compiled entry sets and
bytes. The artifacts contain no fixture/probe classes or ScalaTest/Scalactic
dependency. The higher POMs depend on core and the base runner; the base's
Izumi dependencies remain assertions and protocol. All three SBT graphs match
the generated dependency multiset exactly: 109 nodes, 213 scoped dependency
records, no cycle. Native C/ld warning output labeled [error] on stderr is not
an SBT task failure; actual task-error records, exits and runtime markers are
checked separately.

The final independent JVM consumer in
`distage-provider-published-final-review/` compiles and runs all five unchanged
controls with exit 0. It uses consumer classes plus a frozen 43-entry published
dependency closure, comprising 24 Izumi modules; there are no source-shadowed
production classes or producer class directories. Forty-eight distinct JARs,
43 POMs, four unchanged probe sources and seven uncompiled provenance sources
have hash/equality manifests. Base/higher JARs match 109/26 compiled binary
entries exactly (135 total). The base SHA256 is
`08b1c529af8f2912f12976cb3e29d4250646b36752b566e4e031ce8073ae2533`;
the higher SHA256 is
`396824ee31bd533a577bb8c7b47a32703610414aee547af405a06803258aedfc`.
The initial scratch freeze rejects a byte-identical compiler/runtime library
alias before compilation; that harness capture is retained separately and the
corrected freeze changes no product or probe input.

The final fixture discard change is recompiled and executed in separate fresh
Scala 3 and 2.13 processes: each runs higher-provider JVM/JS testFull with exit
0 and 94 checks per platform (376 revalidation checks). Scala 3 again adds
-Wunused:all to those Compile/Test scopes; 2.13 starts with ++2.13.18. Captures
are `2b-distage-provider-{scala3,scala213}-final-fixture-revalidation.log`.
`2b-distage-provider-matrix-audit.py` exits 0 and records all primary and
revalidation counts in its JSON summary. The independent reviewer reruns the
artifact/graph audit and separately verifies the primary matrix, both with exit
0. Its initial warning-only oracle failure is retained separately; it causes
no product correction.

The final generated-build check runs
`direnv exec . sh -c 'export JAVA_HOME="$JDK21"; exec bash sbtgen.sc --js --native'`
with exit 0 (`2b-distage-provider-generator-final.log`). Before/after hash
manifests agree for all three generated outputs:

- build.sbt: `ff7e1475115a1c269f4f649ccd99287ec2201f6b1596d891a7b33e28f944b82c`
- project/plugins.sbt: `3d66e16d3eb977416f059d7e2ec2ff87c323636db4ea49b2d5bf043431f73c34`
- project/build.properties: `669fae6680792604c3020a33e1d814dfef7b17fedb0ff6a843cc520685db984e`

The final read-only checkpoint review independently reruns both auditors and
rechecks published/source provenance and generator completion, with exit 0.
Its captures are in `distage-provider-final-checkpoint-review/`; generator
argv/exit/before-and-after hashes are preserved in
`2b-distage-provider-generator-final.{commands,completion}.json`. The reviewer
finds no concrete residual defect or overstated acceptance claim in this
portion, while retaining all outstanding parent requirements below.

The two required primary Native interop metadata URLs still return HTTP 404
at 14:42:58 UTC on 2026-10-02, captured in
`2b-distage-provider-release-metadata-final.json` and the response bodies. The
browser tool cannot access these URLs; the primary HTTP requests provide the
status evidence. This does not prevent independent front-end work.

These observations verify an implementation portion, not the complete step-2b
evaluation point. The four replacement spec entry points, effective activation
and custom suite hooks, import-only compatibility gate, active interruption,
complete isolation audit and final-head lanes remain outstanding. Parent items
2b.6/7/8/11 and O.18 stay in progress; no acceptance item or owner decision is
narrowed or marked done by this checkpoint.

### Replacement spec front ends: implementation underway

The provider checkpoint is committed locally on 2026-10-02 as
`58929ad2a6cfb9c8ae7eb0983e284413054dc00a`; the post-commit checkout is clean.
It contains the preceding commands/results and does not close step 2b.

The next portion retains Spec1/Spec2/SpecZIO/SpecIdentity and the registration
verbs through the base provider contract. Registration keeps Functoids and
positions; selected resolution constructs the owned environment. Existing
makeTestEnv, plugin-loader, role and merge hooks dispatch through suite methods;
the defaults use the provider caches. The effective activation policy is shared
with core planning, with suite activation overriding loaded configuration and
explicit run overrides last. Requested choices are validated before effective
axis filtering. These are implementation intentions; their verification is
pending and no acceptance state changes at this capture.

Initial strict Scala 3 production compilation passes on JVM/JS with exit 0
(`2b-spec-frontends-scala3-compile-first.*`). Core regression fixtures pass
116 JVM and 98 JS checks in `2b-spec-frontends-scala3-first.log`; its higher
JVM portion passes 109 checks. The higher JS portion fails the fixture's
assumed two resource scopes: all 14 selected statuses and 12 successful body
counters agree, but acquisition/release are three. The subsequent
`2b-spec-frontends-js-{sharing,binding}-observation.*` captures establish equal
effect tags and hashes, unequal default/app binding sets and unequal execution
parameters. The unequal bindings contain distinct function/singleton instances
from ZIOSupportModule and LogIOModule; the existing Provider/Module equality
preserves definition identity. No engine grouping correction follows from this
fixture assumption. Explicitly reusing session-owned ZIO default/logging module
values defines the intended shared scope, then passes 109 checks on each
platform with two resource lifetimes in
`2b-spec-frontends-scala3-shared-definitions.*`, exit 0.

The read-only reviewer captures two actual front-end defects before correction
in `spec-frontend-boundary-before-review/`. HookOrderProbe compiles with exit 0
and runs with exit 1: the old route invokes roles/merge/loader and succeeds;
the new route invokes loader first and fails selected resolution. Discovery
invokes no hook. DistageSpec restores the original local-variable order before
virtual makeEnv dispatch. PlanningHookPhaseProbe compiles with exit 0 and its
environment/loader controls both run with exit 1: exceptions retain original
class/message but become Selection failures instead of Planning. A stronger
`phase-resource-controls/` replay proves the resource binding is reachable in
the normal control (one acquisition/release/body, exit 0), while the two failing
controls acquire/release/execute zero and still fail for the wrong phase.
The environment construction boundary now retains a lazy Either snapshot,
including its original Planning failure, before effective settings resolution.
These independent probes freeze 48 dependency/compiler JARs plus two current
compiled core/higher snapshot JARs; they are pre-publication evidence, not a
published front-end consumer gate.

The first actual Scala 2.13 process passes both core fixtures (116/98), then
fails higher production compilation because the private nested Registration
case class generates an unchecked outer-reference test in equals. The original
fatal warning is retained in `2b-spec-frontends-scala213-first.*`, exit 1.
Registration now uses a private class with explicit fields, as the base front
end does; no generated equality is needed. Current verification strengthens
the unary fixture to Cats IO, records ZIO environment effect construction, and
adds unselected-effect and reachable-resource planning-hook controls. Their
runtime and remaining compiler lanes are pending at this capture.

The stronger Scala 3 effects/hooks run passes core 116 JVM/98 JS checks and
higher 119 checks per platform with exit 0
(`2b-spec-frontends-scala3-effects-and-hooks.*`). Its four front ends produce
14 terminal results, construct nine deferred effects, execute 12 successful
bodies, and acquire/release three scopes (Identity, Cats IO and the explicitly
shared ZIO definitions). Unselected effects remain unconstructed. Reachable
application-resource controls for failed makeTestEnv and PluginLoader.load
retain their original Planning failure once and acquire/execute zero.

The unchanged independent probes now compile and pass all four controls with
exit 0 in `spec-frontend-boundary-after-review/`: original roles/merge/loader
order, normal one-acquisition/one-release resource control, and both Planning
exception controls. Root checks the two unchanged probe hashes, 14 current and
frozen production source hashes, and every frozen core/higher binary entry
against current compiler output. The snapshots have 240/51 binary entries;
compiler and consumer paths are seven/43 JARs. These remain pre-publication
controls, not a published consumer gate.

The effective-settings controls add loaded configuration, suite precedence,
explicit override precedence, filtering after resolution, memoization disabled
with intra-test graph sharing, and rejection of unknown axis/value/filter/ID or
an empty effective filter. Scala 3 JVM and JS each pass 169 checks, exit 0
(`2b-spec-frontends-scala3-activation-first.*`). Enabled cross-test memoization
acquires/releases one resource for two bodies; disabling it acquires/releases
two while both dependency references inside each body retain the same resource.
All rejected selections acquire/release/execute zero.

The first new Scala 2.13 activation fixture compilation fails before execution
because inferred `Map[Mode.type, Mode.Suite.type]` does not match the overloaded
Activation constructor (`2b-spec-frontends-scala213-activation.*`, exit 1).
The fixture uses the existing tuple constructor. Its fresh rerun passes core
116/98 and higher 169/169, exit 0, in
`2b-spec-frontends-scala213-activation-final.*`.

The subsequent actual Scala 2.12 process passes core 116/98, then fails higher
production compilation: inference at the generic environment delegation cannot
match the TagK/DefaultModule higher kinds
(`2b-spec-frontends-scala212-activation-final.*`, exit 1). DistageSpec.makeEnv
now supplies its declared G argument explicitly to environments.load[G].
Fresh three-compiler runtime/publication/classpath/graph verification is pending
in `2b-spec-frontends-final-matrix.py`; its commands and separate exit captures
will determine the verified portion. No parent acceptance state changes.

The first final-matrix attempt passes Scala 3 and 2.13, including their four
publications, classpaths and observed graphs. Scala 2.12 now passes production
compilation but fails two fixture inference boundaries: the mixed-effect suite
factory Vector's inferred higher-kinded least upper bound, and a generic
super.loadEnvironment delegation. All three original logs/argv are retained
as `2b-spec-frontends-*-final-before-fixture-correction.*`; the 2.12 exit is 1
with seven type errors. The fixture now states Vector[() => TestSuite] and
super.loadEnvironment[F] explicitly. The fresh final matrix is rerunning;
production is unchanged by these fixture corrections.

Before the owned-configuration correction below, verification passes on all
three compiler versions.
`python3 /srv/nvme/tmp/izumi-impl/2b-spec-frontends-final-matrix.py` runs a fresh
batch SBT process per version, through direnv and JDK21, with Scala 3
`-Wunused:all` set on both Compile/Test for core and higher JVM/JS projects.
Scala 2 processes receive no Scala 3 warning flag. Exact 18-command lists and
argv are retained in
`2b-spec-frontends-{scala3,scala213,scala212}-final-before-owned-config.commands.json`;
all three logs end in exit 0. Each compiler passes core 116 JVM/98 JS and
higher 169/169 checks, publishes four artifacts locally, captures eight
Compile/Test classpaths and the observed project graph. No push is performed.

`python3 /srv/nvme/tmp/izumi-impl/2b-spec-frontends-matrix-audit.py` exits 0:
1,656 primary checks (642 core and 1,014 higher), 84 terminal results from the
four-entry-point fixture and 60 activation request controls. Each platform
retains 14 front-end outcomes, three acquisition/release scopes, nine deferred
effect constructions and 12 successful bodies. These counts describe the
bounded fixture observations; they do not substitute for outstanding checklist
gates. Earlier debug/failed/repeated runs are excluded from primary totals.

`python3 /srv/nvme/tmp/izumi-impl/2b-spec-frontends-artifact-audit.py` exits 0:
12 core/higher artifacts, 24 Compile/Test classpaths and 2,138 published binary
entries matching their current compiled bytes exactly. JARs contain the four
entry points and the shared activation resolver, with no fixture binaries.
POMs/classpaths contain no ScalaTest, Scalactic or test-support project leaks.
Core retains its sole direct izumi dependency on framework; higher retains
core/base, optional ZIO and test-only Cats Effect. Each observed graph matches
the generated graph: 109 nodes, 213 scoped records and no cycle. Both auditors
retain stdout, exact argv, exit captures and structured summaries as
`2b-spec-frontends-{matrix,artifact}-before-owned-config-audit.*` and corresponding
before-owned-config summary JSON. Those captures are historical evidence for
the source before the following configuration correction.

The final generator command
`direnv exec . sh -c 'export JAVA_HOME="$JDK21"; exec bash sbtgen.sc --js --native'`
exits 0 and leaves all three generated outputs byte-identical. Exact argv and
before/after digests are in `2b-spec-frontends-generator-final.*`:
build.sbt `cd9a723e92db4e4652a1ccc91fb92ad7b09f0a982e97ca2b3ec83094f81e6e56`,
plugins `3d66e16d3eb977416f059d7e2ec2ff87c323636db4ea49b2d5bf043431f73c34`,
properties `669fae6680792604c3020a33e1d814dfef7b17fedb0ff6a843cc520685db984e`.

The required primary Native interop metadata requests still return HTTP 404 at
15:56:37 UTC on 2026-10-02 (`2b-spec-frontends-release-metadata.json` and response
XML). The browser tool cannot access either URL; the primary HTTP requests
establish those statuses. Independent compatibility and isolation work remains.
Published consumer replay and final read-only checkpoint review are pending.
Parent step 2b, O.13/O.18 and all final evaluation requirements stay open.

The published front-end review first passes the four unchanged hook controls
and independently reruns both auditors. It resolves the actual 45-entry Compile
closure to published JARs/POMs; root checks all frozen source/artifact hashes,
the exact core/higher 240/51 binary sets/bytes, and the closure against its
producer command/log. Its outside-izumi four-spec consumer expands all four
entry points, Functoids, ZEnv and inherited assertion macros. Its first runtime
fails because its manually frozen Cats Effect closure omitted cats-mtl:
captured failures contain NoClassDefFoundError cats/mtl/LiftValue. The exact
Test-minus-Compile classpath contains that pinned 1.6.0 dependency alongside
the three CE 3.7.1 JARs. Adding it to the consumer passes 26 checks, 15 terminal
IDs, three resource scopes, nine deferred builds and 12 successful bodies.
This is a consumer dependency correction, with no product change from it.

Configuration-snapshot probes then distinguish the default and supplied-loader
domains. `PublishedConfigSnapshotProbe.scala` compiles against those frozen
published JARs. The default-loader control invokes the wrapper twice but loads
its backend once, resolves First and injects First, with one acquisition,
release and body, exit 0. The supported public injection route with a
nonmemoizing TestConfigLoader loads twice, resolves First but injects Second,
and reports successful=true; its named snapshot invariant fails with exit 1.
Root reads the full source and failure before correction. The failing source
and original published binary proof are preserved in
`spec-frontend-published-final-review/config-snapshot-{compile,cached,reload}.log`
with exact commands and exit records. This establishes a supplied-loader
snapshot defect, not a defect in the default memoized route. O.18 does not
restrict configuration ownership to the default collaborator.

The proposed standard three-field source key is rejected by a separate model
probe, not by assuming custom loaders use only those fields. In
`spec-config-source-key-review/`, a deterministic activation-sensitive loader
passes its original baseline (two configurations, resources and bodies); a
consumer model keyed only by base/bootstrap/overrides supplies First config to
the Second environment and fails its named full-input-domain assertion. That
model is not production code. A further actual old-publication probe in
`spec-config-reference-domain-before-review/` measures structuralEquality=true
and distinctReferences=true for original environments. Value-keyed resolution
collapses them; execution injects the wrong configurations despite reporting
success, and its named snapshot assertion fails, compile 0/runtime 1.

The provider now retains successful configuration snapshots by original
environment reference identity. It binds every effective environment to that
snapshot and carries the loaded AppConfig in its grouping inputs. The
effective-resolution key also uses reference identity, retaining the supplied
loader's full input domain rather than assuming standard-source dimensions.
Failures preserve the original exception; each effective-resolution request
retains its failure and performs no automatic retry. The default loader keeps
its existing backend memoization policy.

The first strict Scala 3 JVM/JS run passes 184 checks each with exit 0
(`2b-spec-frontends-scala3-owned-config-first.*`). Permanent controls retain the
injected exact AppConfig references through planning/execution, load once per
independent owner with a changing loader, and retain two configuration/resource
scopes for an activation-sensitive loader. The previous 169-check final
matrix, artifacts and external consumers are preserved before-owned-config
evidence and do not verify the changed production. A fresh three-compiler
matrix/publication plus unchanged external snapshot replay is underway.

The original autoset and sequential-ordering suites pass a separate Scala 3
legacy baseline: 46 tests/four suites on each JVM/JS platform, exit 0
(`2b-spec-import-only-original-scala3-baseline.*`). Their prepared migration
diffs change only the Spec1 import. The migrated fixtures have not yet been
compiled or executed; this baseline does not complete 2b.10.

The corrected front ends and owned configuration snapshots now pass the fresh
three-compiler matrix. `2b-spec-frontends-final-matrix.py` runs the exact
18-command sequences retained in `2b-spec-frontends-*-final.commands.json`;
Scala 3.9.0, 2.13.18 and 2.12.21 each exit 0. Each compiler passes core
116 JVM/98 JS and higher 184 JVM/184 JS checks, then publishes core/higher
JVM/JS locally, captures eight classpaths and the observed dependency graph.
The fresh matrix auditor exits 0 with 1,746 primary checks (642 core/1,104
higher), including 84 four-entry-point terminal outcomes and 60 activation
request controls. The configuration fixture additionally records its changing
and activation-sensitive loader controls on all six lanes. Failed and repeated
historical runs are excluded from the primary count.

The fresh artifact auditor exits 0 with 12 publications, 24 Compile/Test
classpaths and 2,167 exact published binary entries. All four entry points
and the shared activation resolver are present; fixture binaries are absent.
The POM/classpath scope and absence checks above still pass. Each graph has
109 nodes, 213 scoped records and no cycle, matching the generated build.
Both auditors retain their current stdout, argv, completion and summary JSON
in `2b-spec-frontends-{matrix,artifact}-audit.*` and corresponding summaries.
The generator is rerun after the correction: exit 0, all three output hashes
unchanged; its final captures supersede the preserved before-owned-config ones.

The read-only reviewer freezes the new actual published Scala 3 closure in
`spec-frontend-owned-config-published-after-review/`: 49 consumer JAR entries
(45 Compile entries plus four explicit pinned Cats consumer dependencies),
seven compiler JAR entries, and 103 artifact records (54 distinct JARs/49
POMs). Six external probe sources are byte-identical to their preserved
controls; 15 frozen production sources are provenance only and are not
compiled into the consumer. One JDK21 compilation and nine runtime controls
all exit 0, with exact commands/results retained. Root independently checks
every artifact/source hash and byte equality, the resolved closure against the
producer log/classpaths, all ten argv, and the exact current core/higher binary
sets/bytes (240/55). Its evidence audit is retained as
`root-after-evidence-audit.json` in that review directory.

The four-spec consumer passes 26 checks/15 terminal IDs, including inherited
assertion expansion, three acquisition/release scopes, nine deferred builds
and 12 successful bodies. The four hook/phase controls pass unchanged.
Both default and nonmemoizing supplied configuration loaders now record one
invocation/one actual load, matching resolved/injected First, and one resource
scope/body. The activation-sensitive control records two loads, correct First
and Second injections and two scopes. The reference-identity control retains
structuralEquality=true/distinctReferences=true while now injecting each
matching snapshot, with two loads/scopes/bodies. These observations verify the
previous supplied-loader reproduction and both domain controls against the
published correction; they do not establish all remaining isolation gates.
Published core/higher JAR SHA-256 values are
`19e5bca16da3d403f28c3f5c944bae47f5fefd9f1c37ab9de0a1fcdf6ef80734`
and `d9e3f777b2d9dbcd3dd3b73b4bd1e64fc6d1e993e1c3602aaa403d866567a475`.

Primary Native interop metadata requests still return HTTP 404 at 16:33:32 UTC
on 2026-10-02; current captures are `2b-spec-frontends-release-metadata.json`
and response XML. Parent 2b, O.13/O.18, compatibility migration and all final
evaluation points remain open. This is a verified front-end/snapshot sub-step;
active interruption, full isolation and the later host integrations remain.

The final read-only bounded checkpoint review reports no concrete residual
defect or overstated current claim (`final-checkpoint-review.json` in the
after-publication review directory). It independently rechecks both auditors,
generator hashes and the external artifacts/sources/controls, while retaining
the open checklist scope. The after producer log is frozen as
`published-producer-final.log`; root verifies its SHA-256 and updated closure
provenance. A reviewer-only correction labels the core digest as coreSha256
and records the actual base-runner digest as baseSha256 in classpath metadata;
the prior metadata is preserved, and artifact bytes, paths and commands do not
change. Root checks both corrected digests against the frozen JARs.

### Import-only distage compatibility: six JVM/JS lanes verified

The preceding four-front-end/owned-snapshot sub-step is committed locally as
`0b87e5facd39305995e7c801b1a64f01d0ff51d3` on 2026-10-02. Root verifies that
hash and observes a clean working tree immediately after the commit. No push
is performed; the commit does not close parent 2b or its final requirements.

Original autoset/sequential-ordering baseline batches exit 0 on Scala 3.9.0,
2.13.18 and 2.12.21, JVM and JS. Scala 3 runs 46 tests/four suites on both
platforms; Scala 2 JS also runs 46. The first Scala 2 JVM captures run only
one autoset test, with empty sequential-suite headers. Their exit 0 does not
verify the 45 omitted sequential tests; that omission is under investigation.
Exact batch/JDK21 argv, stdout and completion records are retained in
`2b-spec-import-only-original-{scala3,scala213,scala212}-baseline.*`.
Higher test sources now retain AutoSetTestkitTest and the sequential-ordering
file under their original packages. Each differs from its pre-migration source
at `58929ad2a6cfb9c8ae7eb0983e284413054dc00a` only in the Spec1 import;
the originals remain in the legacy test project. Root checks full byte equality
to that commit and the exact import replacement before copying. The preserved
manifest and unified import-only diffs are in `spec-import-only-preparation/`.

The public RunSession compatibility harness supplies four fresh factories,
checks the 46 expected structured IDs and source locations, and executes all
four suites twice through independent owners. The retained sequential bodies
assert declaration order for Identity, Cats IO and ZIO; the retained autoset
body asserts the bootstrap planning-hook result. At installation its compiler
and runtime verification was pending; the completed evidence follows below.
This is an additional compatibility fixture portion;
2b.10 and its all-applicable-suite/final-head evaluation stay in progress.

The read-only reviewer identifies the Scala 2 JVM baseline omission in the
captured summaries/planning output before any legacy correction. Root's initial
46-per-lane inference used the JS tails and was unsupported for those two JVM
lanes; the baseline statement above is corrected. The original captures are
preserved. Candidate source diffs still change only the Spec1 import, and this
baseline discrepancy does not itself establish a new-runner defect.

The retained migrated suites now pass Scala 3.9.0, 2.13.18 and 2.12.21 on JVM
and JS. `python3 /srv/nvme/tmp/izumi-impl/2b-spec-import-only-final-matrix.py`
runs one fresh batch SBT process per compiler through direnv/JDK21; Scala 3
adds `-Wunused:all` to both higher Compile/Test scopes, and Scala 2 receives no
Scala 3 flag. Each three-command sequence and argv is retained in
`2b-spec-import-only-*-final.commands.json`, with separate logs and completion
exit 0. Each platform passes 198 higher checks, including two complete
46-test/four-suite compatibility runs through fresh factories. All original
autoset and effect-specific declaration-order assertions execute and succeed.

`python3 /srv/nvme/tmp/izumi-impl/2b-spec-import-only-final-audit.py` exits 0:
1,188 higher checks across six lanes, with 84 additional compatibility checks,
12 compatibility sessions and 552 successful terminal results (46 per
session). Repeated runs preserve the same 46 logical IDs; terminal result,
start/completion event and contiguous ordinal checks all pass. The auditor
checks full original/candidate byte equality and writes the verified unified
diff of each held file. The exact changed lines in each diff are:

```diff
-import izumi.distage.testkit.scalatest.Spec1
+import izumi.distage.testkit.runner.spec.Spec1
```

Both `.verified.diff` files retain the original commit/path and installed
candidate path in `spec-import-only-preparation/`; the auditor retains its
argv/stdout/completion and structured source/hash summary. No production,
dependency or generated-build change is needed for this fixture portion.

A fresh Scala 2.13 JVM legacy debug control repeats the original selection
with `-J-Dizumi.distage.testkit.debug=true`. It exits 0, gathers 46 tests from
four suites and reports 46 successful tests
(`2b-spec-import-only-original-scala213-debug-parallel.*`). Unlike the first
nondebug captures, it includes the sequential bodies. This supports a
timing-sensitive omission hypothesis but does not establish its mechanism.
Source review identifies early singleton registration in the base constructor
and collection of already registered instances before their subclass
construction necessarily finishes; filtering remains another boundary to
distinguish. Explicit serial Scala 2 JVM controls are underway with source
unchanged. No legacy correction or silent settings fallback is made.

Both explicit serial controls finish with SBT exit 0 but only one successful
test, on Scala 2.13 and 2.12. Their completion records explicitly set
countInvariantHolds=false against the expected 46
(`2b-spec-import-only-original-{scala213,scala212}-serial-control.*`). They do
not validate the simple parallel-constructor-race explanation or restore the
omitted bodies. The omission's mechanism remains unestablished. All original
and diagnostic captures are preserved; no legacy source or committed test
setting is changed. The migrated fixture independently executes all 46 bodies
on these JVM lanes, so the bounded import-only portion is verified while the
legacy discrepancy remains disclosed for subsequent investigation.

The final read-only review finds no concrete defect in this bounded migration
portion. It independently checks the source/commit byte oracle, both exact
import-only diffs, all six runtime lanes and the matrix auditor. Its immutable
captures and `verification-audit.json` are in
`spec-import-only-requirements-review/`; the review keeps full 2b.10 and parent
2b open. The current generated files have no diff from the committed outputs
(`git diff --exit-code build.sbt project/plugins.sbt project/build.properties`,
exit 0), and `git diff --check` passes.

Primary Native interop metadata requests still return HTTP 404 at 16:53:44–45
UTC on 2026-10-02 (`2b-spec-import-only-release-metadata.json` and responses).
The higher Native lanes wait on that named external condition; independent
ownership, duplicate-identity and host integration work remains.

### Front-end ownership boundaries: reproduced defects

The verified import-only fixture portion is committed locally as
`92b8bace3127b84389a55c6633f17aeb8f5456c7` on 2026-10-02. Root verifies the
hash and observes a clean tree immediately afterward. No push is performed.

The read-only ownership audit reproduces two additional defects against the
unchanged published closure in `spec-session-ownership-published-before-review/`.
Two public probe sources compile on JDK21, exit 0, without source shadowing or
producer class directories. ConcurrentSharedSuiteProbe accepts the same
SpecIdentity instance into both first/second catalogues on attempt 0; its
ownership oracle fails with exit 1 before application execution. The suite's
ownedProvider check and assignment were not atomic. Registration now holds the
suite monitor across the ownership check and registration; verification of the
correction was pending at installation and is completed below. The original
failure source/command/log is preserved.

CustomBootstrapRouterProbe's default control preserves its sentinel router,
exit 0. With fresh custom factories that only delegate to BootstrapFactory.Impl,
planning replaces the sentinel and then replaces the first session's router
with the second's, exit 1. The custom hooks do not explicitly mutate global
logging state. Root reads both full probe sources and the expected failure
messages before correction, checks all 54 frozen JAR hashes/bytes and 15 frozen
production-source hashes/bytes, both probe hashes, four exact JDK21 commands,
and the exact current core 240 binary entries. The frozen higher publication
has 55 entries, but the later compatibility compiler output does not match
every frozen higher byte; root's current-byte comparison fails and is under
investigation. The published reproductions retain their original publication
and source provenance and are not presented as current-byte-equality proof.
The custom-bootstrap defect remains
uncorrected while its ownership policy is investigated; no blind definition
replacement or process-global property override is introduced.

The audit also reproduces a cached custom-loader defect before any loader
correction. CustomPluginLoaderStateProbe uses fresh zero-argument
PluginLoaderDefaultImpl instances, an explicitly cached scanned plugin, and
actual DI execution in two fresh owners. The default route succeeds with
Vector(1, 1) and distinct injected state; the custom route successfully executes
both bodies but observes Vector(1, 2) and the same injected state, then fails its
session-isolation oracle with exit 1. This establishes mutable run-state reuse,
not merely PluginDef reference equality. Both probe/control source and commands
are preserved with compile exit 0. The initial extra-probe harness compile
failure and the separate uncached controls remain historical evidence; they
are not the cached reproduction. Root reads the full corrected cached probe and
its expected failure before any plugin correction. Its separate
`root-before-command-evidence-audit.json` verifies all eight captured commands
and results, four compiled probe sources, frozen artifact hashes, and absence of
producer class directories or production-source shadowing.

The historical binary discrepancy has no established compiler-input cause.
The reviewer's captured javap instruction/signature comparisons match for all
four affected classes; their TASTY UUID attributes differ. Compiler-version
TASTyPrinter captures show equal Names sections but different tree serialization
lengths (6041/6038, 3162/3161, 1104/1102 and 1268/1266). This does not prove
complete TASTy semantic equivalence and does not erase the failed exact-byte
comparison. No product correction is made from that metadata observation.

### Verified atomic registration and duplicate front-end portion

On 2026-10-02, the one-line registration monitor correction and permanent
SpecRegistrationFixtures pass Scala 3.9.0, 2.13.18 and 2.12.21 on JVM and JS.
One fresh batch SBT process per compiler runs through direnv/JDK21; Scala 3
applies `-Wunused:all` to higher Compile/Test on both platforms. Captures are
`2b-spec-registration-scala3-first.*` and
`2b-spec-registration-{scala213,scala212}-final.*`; each retains exact commands,
log and completion exit 0. Each platform passes 242 higher checks. The unchanged
import-only fixtures still complete both 46-body compatibility sessions.

`python3 /srv/nvme/tmp/izumi-impl/2b-spec-registration-final-audit.py` exits 0:
1,452 higher checks, including 264 added registration checks. Four front ends
each repeat 32 paired discoveries per lane: 768 pairs, exactly 768 accepted
owners and 768 original Discovery rejections. JVM workers enter through a
bounded start gate on the existing borrowed executor; JS checks the paired
queued discovery semantics without claiming simultaneous threads. All 72
duplicate cases check explicit path/suite/test rejection, retained failure,
factory counts, suspended configuration/effects/bodies and correct run terminal
reporting. Full source-byte comparison against 92b8bace verifies that the monitor
is the only production change. The first auditor failed because Scala 3's
command capture is a list rather than the Scala 2 records' object shape; its
TypeError is preserved as an auditor harness failure, then corrected and rerun.
It is not a runner regression or a failed producer lane.

All six higher artifacts are published locally. The five publications in the
runtime sequences and separate strict Scala 3 JS publication finish with exit
0 (`2b-spec-registration-scala3-js-publication.*`). The artifact auditor exits
0 and compares all 348 current binary entries byte for byte to their six
published JARs, verifies required front ends, excludes fixture classes and
checks unchanged expected POM dependency scopes. Its prior classpath and graph
evidence is explicitly reused from the previous unchanged-build gate, rather
than presented as a fresh SBT graph capture. A fresh generator invocation exits
0 with all three generated output hashes unchanged
(`2b-spec-registration-generator-final.*`), and `git diff --check` passes.

The independent published replay in
`spec-session-ownership-published-atomic-after-review/` freezes the current
higher JAR with SHA256
6283f47d0a3531f2f50846133e32dd3554db5bc9d075dbc5df136395529fc7a3 and the
unchanged dependency closure. All four probe sources are identical to their
before captures. The original race probe now exits 0 over 128 attempts with
violated=false; twelve duplicate controls and both default hook controls pass.
The custom bootstrap and custom cached-plugin controls still fail their original
isolation oracles, exit 1. Root independently verifies seven exact JDK21
commands/results, 19 source hashes/bytes (four compiled probes and 15
production-provenance-only sources), 55 frozen artifact hashes/bytes, immutable
producer log provenance and all 295 current core/higher JVM3 binary entries.
`root-after-evidence-audit.json` retains these checks; no consumer compilation
uses producer classes or production-source shadowing.

Primary Native interop metadata remains HTTP 404 at 17:29:05 UTC on 2026-10-02
(`2b-spec-registration-release-metadata.json` and response XML). Higher Native
lanes wait on that named external condition. This verified registration portion
leaves parent 2b, full 2b.8/O.18 custom-hook isolation and final-head evaluations
open; the two actual custom-hook failures remain independent implementation
work.

The read-only reviewer concludes the bounded atomic checkpoint with no concrete
residual defect or overstated acceptance claim. It independently reruns both
auditors, verifies the persisted generator command/completion/current hashes,
reads the final ledger and API documentation, and runs `git diff --check`, all
exit 0. Its review explicitly retains both custom-hook failures and the open
parent/final-head gates. This checkpoint is committed locally; its exact hash
is recorded at the next ledger evaluation after commit creation. No push is
performed.

### Verified delegating bootstrap portion

The verified atomic registration portion is committed locally as
`bb2c7f0ba4a3532eebf707cc4110eb140b8c1e26` on 2026-10-02. Root verifies the
hash and observes a clean tree immediately afterward. No push is performed.

The unchanged CustomBootstrapRouterProbe's expected isolation failure is read
before editing either bootstrap implementation. The testkit-specific built-in
BootstrapFactory.Impl now passes setupStaticLogRouter=false on JVM and JS;
its unused role DebugProperties imports are removed. This corrects the library
side effect when fresh custom factories delegate to that built-in implementation,
preserving their virtual dispatch, custom definitions and router collaborator.
It changes the prior testkit behavior, which read the role-app static-router
property with default true; testkit no longer installs that process-global
router through the built-in factory. Role-app ModuleProvider/LogstageModule
explicit setup behavior is unchanged. The reviewer finds no plan/checklist or
testkit public documentation requiring the historical auto-installation.
Explicitly mutating custom hooks are not established isolated by this correction;
full O.18 and final-head evaluation remain open.

SpecBootstrapFixtures adds sequential/repeated and concurrent planning/execution
controls through fresh delegating custom factories. It checks configuration
loader dispatch/snapshot, custom bootstrap/app binding preservation, suspended
application resources during discovery/planning, same-owner memoization,
resource release before terminal completion and retained sentinel router.
Compiler/runtime verification is pending at installation.

The initial strict Scala 3 core/higher JVM/JS sequence passes (116/98 core and
269 higher checks per platform) and publishes both JVM modules, exit 0
(`2b-spec-bootstrap-scala3-first.*`). Review identifies two oracle limits,
rather than production defects: the custom sink initially observes release
only after execute returns, and paired asynchronous runs do not guarantee
overlapping resource lifetimes. The strengthened fixture records the release
count at Finished emission and uses a shared Promise to hold each owner's
second Cats IO body until every owner has acquired its resource. The gate
observes each scope acquired=1/released=0 before opening. The first strengthened
Scala 3 sequence passes 272 higher checks on JVM and JS and publishes all four
modules, exit 0. Final per-owner sequencing and failure-path checks are pending;
this intermediate capture is not the final fixture checkpoint.

Review also identifies an uncompleted test-helper waiter if the gate's final
verification throws. Root reproduces this against the actual compiled JVM3
OwnerGate through a scratch reflection probe (explicit test-class directory,
not a published-consumer claim). Compile exits 0; the original controlled
verification failure is preserved but waitingSettled=false, and the named
settlement oracle exits 1. Exact source/commands/logs are retained in
`bootstrap-owner-gate-before/` before any helper correction. The final fixture
will settle every waiter with the same failure and explicitly serialize tests
within each owner while retaining overlapping independent owner resources.

All six core/higher runtime lanes and twelve publications complete the initial
strengthened matrix with exit 0. The final helper revision is applied while the
remaining Scala 2.12 process runs core-only dependency compilation; higher
fixtures are outside that active task graph. The higher final checks will be
rerun consistently on every compiler afterward, rather than claiming those
mixed-revision intermediate logs verify the final test helpers.

Root also reproduces a lost fixture-counter update before changing its method:
the compiled DistageProviderFixtures.Checks receives 20,000 successful concurrent
verify calls, prints 20,000 check lines, but reports 19,999. The scratch probe
compiles with exit 0 and fails its exact-count oracle with exit 1
(`bootstrap-check-counter-before/`). The new concurrent bootstrap callbacks
require serialized counter updates; Checks.verify now holds its instance
monitor. This is a test-harness correction, not a production runner defect.
The unchanged gate/counter probes and fresh six higher runtime lanes remain
pending final rerun at installation of these corrections.

The final strict Scala 3 helper/runtime/publication sequence completes with
exit 0, passing 272 higher checks on both JVM and JS and publishing all four
modules (`2b-spec-bootstrap-helper-scala3-final.*`). Root replays the unchanged,
previously compiled gate and counter probes against the corrected actual JVM3
test classes. The gate preserves its controlled original failure and now
reports waitingSettled=true, exit 0. The counter prints exactly 20,000 successful
check lines and reports observed=20000, exit 0. Both after directories preserve
identical probe source/class bytes and exact runtime argv/results:
`bootstrap-owner-gate-after/` and `bootstrap-check-counter-after/`. These are
explicit test-helper controls using the compiled test directory, separate from
the published-consumer gate. Scala 2 final helper verification is underway.

The separate instance-cache policy experiment is not applied. Its preserved
actual-engine captured-definition control in
`spec-custom-loader-cache-scope-captured-definition-review/` observes two fresh
custom loaders within one RunSession: the published baseline acquires/releases
one shared resource, while the per-loader candidate acquires/releases two
distinct resources and fails its named preservation oracle. Both compile with
exit 0. The earlier stateless-binding candidate control passed and remains
separate evidence; it does not disprove the captured-binding counterexample.
The current product's cached custom-loader cross-session state defect remains
uncorrected. A correction must retain both session isolation and compatible
same-session sharing; no caching requirement is narrowed.

The final helper revision passes all six fresh higher runtime lanes, 272 checks
each, and twelve core/higher publications, exit 0
(`2b-spec-bootstrap-helper-{scala3,scala213,scala212}-final.*`). The strict
Scala 3 invocation enables -Wunused:all in Compile/Test for all four scoped
projects; Scala 2 invocations use their pinned versions without Scala 3 flags.
Core runtime evidence is explicitly reused from the immediately preceding
unchanged-production matrix, 116 JVM and 98 JS checks per compiler. The final
matrix auditor exits 0 (`2b-spec-bootstrap-final-audit.*`): 2,274 primary checks,
642 core and 1,632 higher, including 180 added bootstrap checks, 24 owner scopes,
48 successful terminal results and six controlled overlapping-owner pairs.
Its production byte oracle admits only the two static-router policy changes
and their unused import removals relative to bb2c7f0.

The artifact auditor exits 0 (`2b-spec-bootstrap-artifact-audit.*`), comparing
2,167 current binary entries against all twelve freshly published JARs, checking
expected dependency scopes and excluding test fixtures. Its 24 prior resolved
classpath blocks and three unchanged-build graphs (109 nodes, 213 scoped edges,
no cycles per compiler) are explicitly reused. The fresh generator exits 0
(`2b-spec-bootstrap-generator-final.*`) and leaves the hashes of build.sbt,
project/plugins.sbt and project/build.properties unchanged. No root SBT or
generator process remains active after these captures.

The independent replay in `spec-bootstrap-published-final-review/` freezes 54
dependency JARs and 24 POMs, including the final JVM3 core JAR SHA256
a4a82693b87d5c865cbbb03b8f58aed7127a35dea0ef9a831fb806e322fd8f8f and higher
JAR c81452f21adb27b839ec556568897cd9c6d59bafd456e2b90e0413642170174b. Its
immutable producer-log SHA256 is
406e2c51cf827b81903361b83dc9c8fa28f78719f9a7a73c167fda05950cff95.
Four prior public probes remain byte-identical; a fifth checks explicit
role-app static-router policies. The ten exact JDK21 commands yield compile
exit 0, shared-suite/duplicate/default-bootstrap/custom-bootstrap controls
exit 0, both explicit role-property values for the delegating factory exit 0,
owned plugin-loader control exit 0, and all four explicit role-app module
policy controls exit 0. The unchanged cached custom-plugin control still exits
1 for its expected cross-owner shared-state failure (Vector(1,2), shared=true).
Both bodies succeed, so this failure is not attributed to test execution.

Root independently audits all 78 artifact hashes/bytes, 22 sources (five compiled
probes and 17 production-provenance-only sources), ten command/result records,
consumer classpath provenance and all 295 current JVM3 core/higher binary
entries. `root-after-evidence-audit.json` records exit 0. No consumer compilation
uses producer class directories or production-source shadowing. An initial
root auditor lookup used publishedJar instead of the actual jar record key and
failed before writing its result; only that auditor schema lookup was corrected.
The review auditor's preserved command-string and quoting failures likewise
precede its successful rerun, without changing product artifacts or probes.

Primary Native interop metadata returns HTTP 404 at 18:20:05 UTC on 2026-10-02
(`2b-spec-bootstrap-release-metadata.json` and response XML). Higher Native
lanes remain waiting on that named external condition. This bounded bootstrap
correction leaves parent 2b, the cached custom-plugin failure, full O.18 and
final-head evaluations open. The acceptance checklist and owner decisions
remain unchanged. Final read-only review and local commit are pending below.

The read-only reviewer finds no concrete residual production defect or
overstated bounded-checkpoint claim. It independently reruns both final auditors
with exit 0, verifies the persisted generator argv/completion and all three
actual output hashes, reads the final ledger/API documentation and runs
git diff --check successfully. Its conclusion explicitly preserves the reused
core/classpath/graph provenance, the test-helper/auditor failure captures and
the cached custom-plugin, full O.18, parent and final-head work. This verified
bootstrap portion is committed locally; its exact hash is recorded at the next
ledger evaluation after commit creation. No push is performed.

### Verified request-preserving cached custom-plugin portion

The verified delegating bootstrap portion is committed locally as
`7ed12ae9b6fd21f9b1298d404f9bf3735edd7b23` on 2026-10-02. Root verifies the
hash and observes a clean tree immediately afterward. No push is performed.

The unchanged published CustomPluginLoaderStateProbe still demonstrates the
expected isolation failure before this correction: two fresh zero-argument
custom loaders use the library's legacy global package cache, both bodies
succeed, and mutable scanned state yields Vector(1,2) instead of Vector(1,1).
The captured-definition same-session control also rejects replacing that cache
with separate per-loader caches. A permanent actual-engine fixture is added
before changing the production cache ownership policy. Its checks require fresh
state across owners, compatible sharing within each owner, suspended resources
during discovery/planning, preserved forwarding/map/copy hooks and release
before Finished. Verification is pending at installation.

The fresh strict Scala 3 permanent-fixture reproduction compiles and exits 1
for its expected repeated-owner isolation oracle
(`2b-spec-plugin-ownership-before.*`). Both owners execute their two bodies
successfully. The first reports counts=1,2, shared=true, acquired=1/released=1;
the repeated owner reports counts=3,4, shared=true, acquired=2/released=2. Its
failure is "repeated custom plugin owner starts with fresh scanned state".
The exact fixture sources are frozen in `spec-plugin-ownership-before-sources/`
before changing the production policy. This also confirms that compatible
same-owner memoization passes in the pre-correction library.

The production correction carries the provider's existing package-cache owner
through both app and bootstrap PluginConfig requests. Forwarding/custom loader
dispatch is retained; Classgraph selects this owner only for its built-in legacy
cache route, retaining an explicitly supplied protected cache collaborator and
one getter invocation per package. Default and fresh custom loaders in a session
therefore use the same package cache rather than separate caches or disabled
caching. PluginConfig retains its six fields, generated copy/default getters,
constructor, extractor and Product shape. Its final modifier is removed solely
to permit a private immutable Owned subclass whose exact six-argument copy
override has no new defaults. Scope metadata survives copy/snapshot/helpers and
is excluded from value equality/Product reconstruction; reconstructing a fresh
six-field value does not reconstruct its runtime owner. This is an explicit
implementation tradeoff, not an assertion of actual public API compatibility.
The independent model passes 18 commands on the three compilers, but actual
published old-binary/source/Mirror controls remain pending.

The current build defines extension-plugins only for JVM/JS. No higher Native
ownership result is inferred from the common source change. Existing Native
implementation and interop gates remain open. A fresh strict Scala 3 core/higher
JVM/JS runtime sequence is running, without publication while the reviewer
freezes actual pre-correction PluginConfig artifacts. It found missing Scala 2
published core-api prerequisites; these unchanged modules must be published
before the complete actual published-only API baseline can run. Producer class
directories or unrelated released artifacts will not substitute for that gate.

The first strict Scala 3 correction attempt stops at compilation of the new
snapshot test helper: ArrayBuffer is not the API's Seq parameter type
(`2b-spec-plugin-ownership-scala3-first.*`, exit 1). Root corrects only the
helper's .toSeq conversion; the production correction is unchanged. A fresh
rerun is captured separately. The reviewer freezes all three actual old
extension-plugin JARs/POMs before publication; twenty missing prerequisite
coordinates are recorded in its `plugin-config-actual-published-api-before-review/`
prerequisite-gaps.json. Separate fresh Scala 2 compiler processes publish only
those ten unchanged prerequisite projects per version. They do not publish the
changed extension-plugin module or substitute producer classes for API evidence.

The corrected strict Scala 3 runtime sequence exits 0: core checks=122 JVM/103
JS, higher checks=302 JVM/272 JS
(`2b-spec-plugin-ownership-scala3-corrected.*`). The unchanged-production
isolation oracle now observes counts=1,2/shared=true/acquired=1/released=1 for
each of the four initial custom-loader owners. The JVM fixture is strengthened
after its runtime task completes, while the active JS/prerequisite graphs exclude
those JVM-only test sources: Cats IO now holds each owner's second body until
all concurrent scopes have acquired their resource, and a fifth owner mixes the
default and fresh custom route. Final reruns must verify this revision instead
of attributing its stronger guarantees to the earlier 302-check capture.
Both ten-project Scala 2 prerequisite publication sequences finish with exit 0
(`2b-spec-plugin-api-prerequisites-{scala213,scala212}.*`). The reviewer observes
and freezes complete published-only baseline closures, without producer classes
or unrelated releases. Actual pre-correction API baseline completion is pending.

The strengthened JVM fixture initially fails because its gate assumes the
debug=true suite runs second. The observed engine runs it first. Root preserves
the failed final-lane capture as
`2b-spec-plugin-ownership-scala3-final-initial-fixture.*`, adds only diagnostics,
and reproduces the actual cause in `2b-spec-plugin-owner-gate-before.*`, exit 1:
the gate observes counts=1/acquired=1/released=0 and the protocol outcome retains
the named "first custom plugins hold every owner's resource before opening the
gate" Test failure. This is a test-harness ordering assumption, not a production
ownership defect. Only after capturing that cause does the helper change:
Statistics atomically returns whether the second body has completed, and that
body enters the gate regardless of suite identity. Production policy remains
unchanged. All final lanes and publications will be rerun; the failed capture
is not treated as successful evidence.

The corrected final Scala 3 sequence now exits 0
(`2b-spec-plugin-ownership-scala3-final.*`): 122/103 core and 331/290 higher
checks on JVM/JS, seven unchanged extension JVM test cases across three suites,
four fresh extension Compile/Test classpath captures and six publications. The
five scanned owners include the mixed default/custom route; all report two
successful results, fresh counts=1,2 and shared=true/acquired=1/released=1.
The concurrent gate explicitly observes counts=1,2;1,2, acquired=1,1 and
released=0,0 before releasing either owner. The common static-request fixture
checks both app/bootstrap scope forwarding and original cache flags on JVM/JS;
JS runtime scanning remains unsupported and no JS scanning result is claimed.
All three final compilers and after-publication proofs remain pending closure.

The actual BEFORE API gate passes all thirteen commands on Scala 3.9, 2.13 and
2.12. Shared Scala consumers check the original six-argument constructor/apply,
copy/defaults/extractor/Product/snapshot/helpers; Java directly links all six
synthetic copy-default getters and copy6; Scala 3 Mirror.fromProduct6 passes.
The reviewer freezes 127 artifacts (70 JARs/57 POMs), 24 production-provenance
sources, 162 old producer binary entries and thirteen compiled consumer files.
Root reads the full three consumer sources and independently audits thirteen
exact JDK21 commands/results, all 127 artifact hashes, four source hashes,
thirteen consumer binary hashes and 162 producer entries, exit 0
(`root-before-api-evidence-audit.json`). The frozen original PluginConfig source
matches git show at 7ed12ae9 exactly. Consumer paths use published-only closures,
without producer classes or production-source shadowing. The review preserves
an initial invalid Scala reference to JVM synthetic getters as a harness failure;
the explicit Java consumer supplies that linkage check. These successful before
controls do not establish compatibility with the changed producer.

The final Scala 2.13 sequence passes the same checks and six publications, exit
0. Scala 2.12 stops at a newly introduced test-helper compilation error:
two val _ declarations in one block are rejected by 2.12
(`2b-spec-plugin-ownership-scala212-final-pre-helper-correction.*`, exit 1).
Root reads the exact "_ is already defined as value _" diagnostic before
replacing only that second discarded result with the project's Discarder
syntax. The product policy is unchanged. Earlier successful Scala 3/2.13
captures are preserved separately, and final consistent helper/runtime and
publication sequences are rerun on all three compilers.

The initial published Scala 3 AFTER consumer gate passes eight commands: all
three byte-frozen before consumer binaries link, and identical Scala/Java/Mirror
sources recompile and run. The separate six unchanged public runtime probes
pass eleven commands, including the original cached-plugin state oracle
(Vector(1,1), sharedState=false, both successful) and captured-definition sharing
control (two results/two loaders, acquired=1/released=1/shared=true). Registration,
bootstrap/property and explicit role-app controls remain passing. These are
initial after captures pending final artifact closure, not final-head claims.

Republishing after the Scala 2.12 test-helper correction changes three Scala 3
whole-JAR hashes. Root's initial current-artifact assertion fails before writing
its result. The follow-up ZIP comparison observes identical entry sets and all
entry bytes except META-INF/MANIFEST.MF; the only changed line is
X-Build-Timestamp. `root-repackaging-comparison.json` and the review's
`spec-plugin-ownership-publication-repackaging-comparison.json` preserve the
observed manifests. All 354 class/TASTy bytes still match. This is evidenced
repackaging, not an inferred source or binary change. Prior immutable captures
are retained; the reviewer freezes and replays the actual final-helper JARs in
fresh directories so final whole-artifact provenance is direct.

All three consistent-helper final sequences finish with exit 0
(`2b-spec-plugin-ownership-{scala3,scala213,scala212}-final.*`). Each fresh
compiler runs six module testFull commands and six publications; only Scala 3
sets -Wunused:all in Compile/Test for all six named LocalProjects. The matrix
auditor exits 0 (`2b-spec-plugin-ownership-final-audit.*`): 2,538 primary checks,
675 core and 1,863 higher, including 264 added plugin checks. It separately
counts 21 unchanged extension JVM test cases, fifteen scanned-owner scopes,
thirty successful scanned terminal results, three controlled overlapping-owner
pairs and 24 static-request owner scopes. Every held pair observes both resources
acquired and neither released; Finished observers require release before the
event. All five production sources remain identical to their recorded policy
snapshot, captured during the final runtime sequence.

The artifact auditor exits 0 (`2b-spec-plugin-ownership-artifact-audit.*`):
eighteen fresh artifacts contain exactly 2,654 current binary entries, compared
byte for byte. It checks expected POM scopes, higher optional ZIO/test Cats
Effect and absence of fixtures. Twelve extension Compile/Test classpath blocks
are fresh; 24 prior unchanged-build core/higher blocks and three graphs (109
nodes, 213 scoped edges, no cycle per compiler) are explicitly reused. Extension
Test may retain its existing ScalaTest dependencies; none leak into the core or
higher modules. The fresh generator exits 0 with all three generated output
hashes unchanged (`2b-spec-plugin-ownership-generator-final.*`). All root SBT
and generator processes have completed.

The actual final AFTER API gate in
`plugin-config-actual-published-api-after-final-helper-review/` passes twenty
exact commands. Frozen old consumers link on each compiler, and identical
Scala/Java/Mirror sources recompile and run against the actual final published
PluginConfig. Root independently audits all 127 artifacts, three unchanged
consumer sources, 26 consumer files (thirteen identical old files), twenty
commands/results and all 165 current extension JVM binary entries, exit 0
(`root-after-api-evidence-audit.json`). Its two initial schema assertions used
the wrong recompiled directory name and treated a per-compiler binary object
as a list; only those auditor assumptions are corrected. No product or probe
changes are involved. The review also preserves an earlier deferred Scala 2
freeze guard when the expected completion record was absent after historical
capture renaming; it performed no artifact freeze and is not a compiler failure.

The final runtime replay in `spec-plugin-ownership-published-final-helper-review/`
passes eleven commands with six byte-identical prior public probes. Root audits
all 78 artifact hashes/bytes (54 JARs/24 POMs), 26 sources (six compiled and
twenty production-provenance-only), exact consumer classpaths/commands/results,
and all 354 current JVM3 core/higher/extension binary entries, exit 0
(`root-after-runtime-evidence-audit.json`). The actual final immutable producer
log SHA256 is
6c3e7a7246e4393ffff7f27748da0ace01dd0660de3133f9d340cd26b9410c8d.
The original cached custom-plugin failure is corrected: both owner bodies
succeed with fresh Vector(1,1) and sharedState=false. The captured-definition
same-session sharing control remains acquired=1/released=1/shared=true.
Registration, duplicate, bootstrap/property and explicit role-app controls pass.
No consumer compilation uses producer classes or production-source shadowing.

Primary Native interop metadata returns HTTP 404 at 18:55:58 UTC on 2026-10-02
(`2b-spec-plugin-ownership-release-metadata.json` and response XML). Higher Native
work remains waiting on that named release condition. The bounded cached-loader
correction leaves parent 2b, the complete custom-hook audit, remaining compatibility
inventory, cancellation and final-head gates open. The acceptance checklist and
owner decisions remain unchanged. Final read-only closure and local commit are
pending below.

The read-only review reproduces a remaining ownership defect in a public custom
load override that reconstructs PluginConfig from its six fields before calling
super.load. The new value loses the request's owner and reaches the legacy
global package cache. The final published probe in
`spec-plugin-ownership-reconstruction-boundary-review/` compiles with exit 0
and fails the original isolation assertion with exit 1: both owners' bodies
succeed, but seen=Vector(1,2), sharedState=true. Root reads its full source,
exact commands and captured failure before making any further product change.
The API documentation now states this residual defect explicitly. Requiring
users to preserve an otherwise equivalent request is not adopted as a narrowed
acceptance criterion; complete ownership remains unfinished. This checkpoint
verifies the request-preserving routes and their same-owner sharing only.
Parent 2b.8/O.18 and the reconstruction correction remain open. The reviewer
also retains an extra generator-log EXIT marker assertion as an auditor
assumption: generator completion and all actual output hashes pass, while that
command's log contains only direnv output.

Final read-only review closes this bounded portion with no additional findings:
`spec-plugin-ownership-bounded-final-review/review-verdict.json` records five
successful auditors/diff checks, generator completion and three exact hashes,
13 before/20 after API commands and 11 runtime commands. Root independently
checks the reconstruction capture with
`spec-plugin-ownership-reconstruction-boundary-review/root-reconstruction-evidence-audit.json`
(exit 0): seven compiler and 49 published consumer JARs, source hash, exact
compile 0/runtime 1 commands and the expected isolation assertion. Its
`root-source-variant.diff` confirms only the custom six-field reconstruction
differs from the preceding passing public probe. Local commit below records
this verified forwarding portion; no parent or final gate is marked done.

## 2026-10-02: verified synchronous custom-loader reconstruction portion

The preceding verified forwarding checkpoint is committed as
`cbe133bb38dbf7ae76ac6f6858f20bbdafa7ef23`. `git rev-parse HEAD` and
`git status --short` immediately after that commit return the exact hash and
an empty working tree. This section retains parent 2b/O.18 and every final
evaluation point as open.

Fail-first evidence: the unchanged public six-field reconstruction probe above
compiles with exit 0 and fails with exit 1 against that checkpoint's published
closure. A permanent JVM resource fixture adds direct and built-in-map
reconstruction to the existing five-owner controls. Before any production
correction, `python3 /srv/nvme/tmp/izumi-impl/2b-spec-plugin-reconstruction-before.py`
runs a fresh strict Scala 3 process and exits 1. Compilation succeeds; all
forwarding owners and the first reconstructed owner pass. The reconstructed
repeated owner reaches its gate with counts=3,4, acquired=2, released=1, and the
fresh-owner assertion becomes the recorded Test failure. The fixture source,
exact SBT argv, log and completion are captured under that prefix. Root reads
the nested failure and its counts before editing production code.

An initial one-time instance-binding candidate is frozen but never compiled
under `spec-plugin-reconstruction-rejected-binding-candidate/`. Review identifies
that it would reject a shared stateless loader after its first session. The
actual published shared-instance control in
`plugin-loader-binding-candidate-review/ControlledSharedLoaderProbe.scala`
passes: the same loader serves fresh sessions with Vector(1,1), sharedState=false.
Root reads the full source and captured exit-0 output. The later source-shadow
candidate uses the revised invocation implementation, not the rejected binding
candidate. Its initial reviewer script still expects the discarded binder's
rejection; that stale oracle fails after the actual shared-instance run passes.
The reviewer retains this harness failure separately; it is not a production
failure or verification of the discarded candidate.

The correction passes the owner explicitly through an internal concrete
PluginLoader.loadOwned call and a scoped wrapper. Built-in map forwards that
call without executing its mapping function during wrapper creation. The
Classgraph implementation retains virtual load/scan and protected package-cache
dispatch; a ThreadLocal owned by that loader instance exposes the invocation's
cache while a custom override reconstructs its request. A finally block restores
an outer invocation or removes the scope. No process-level ownership registry
is added. SessionPluginLoader validates its fixed owner. The session environment
factory passes the scoped wrapper while preserving the original owned requests.

The first strict Scala 3 check,
`python3 /srv/nvme/tmp/izumi-impl/2b-spec-plugin-reconstruction-first.py`, exits 0:
core 128 checks, higher 372. Its direct/mapped reconstruction controls now have
fresh repeated/concurrent state, same-owner resource identity, one acquisition
and release, and release before Finished. JVM core controls verify a shared
loader across owners, map, failure cause and legacy-policy restoration, nested
invocation restoration, two barrier-controlled simultaneously entered worker
invocations, and preserved explicit protected-cache policy. A later helper edit
derives the barrier size from its owner vector; the consistent final matrix
below is the verification of that final helper source.

Final producer verification is running through
`python3 /srv/nvme/tmp/izumi-impl/2b-spec-plugin-reconstruction-final-matrix.py`.
Each compiler has a fresh JDK 21 batch process; Scala 3 has Compile/Test
-Wunused:all on all six relevant LocalProjects, while Scala 2 receives no Scala 3
flags. The script runs extension/core/higher JVM and JS tests, captures 36 fresh
Compile/Test classpath blocks, and requests 18 publications. Four production
sources are frozen before these final processes in
`spec-plugin-reconstruction-production-snapshot/manifest.json`. Scala 3 and
2.13 completions currently return exit 0; 2.12, published consumers, binary
auditors, generator and final review remain pending.

Candidate-only review reproduces two remaining isolation defects: an opaque
wrapper reconstructs then delegates to a hidden Classgraph loader, and a custom
Classgraph override waits synchronously for a worker-thread super.load of a
reconstructed request. In
`plugin-loader-owned-route-candidate-review/{opaque-reconstructing,handoff-reconstructing}/`
both compile with exit 0 and run with exit 1: both bodies succeed but state is
Vector(1,2), sharedState=true. The corresponding metadata-preserving routes
pass. These captures use frozen source-shadow candidate units, never producer
classes; final-publication replays are pending. They are concrete scope findings,
not evidence that all custom hooks are isolated. Equivalent reconstruction and
thread handoff are not declared unsupported, and acceptance is unchanged.

Consistent final producer results: all three completion files return exit 0.
The six lanes pass core 128 JVM/103 JS and higher 372 JVM/290 JS checks, plus
seven existing extension cases per compiler. The independent
`python3 /srv/nvme/tmp/izumi-impl/2b-spec-plugin-reconstruction-final-audit.py`
exits 0 and records 2,679 primary checks (693 core/1,986 higher), 141 added
reconstruction checks, 30 scanned owners, 60 selected terminal results and six
controlled overlapping owner pairs. All four production snapshots still match
the checkout exactly. The independent
`python3 /srv/nvme/tmp/izumi-impl/2b-spec-plugin-reconstruction-artifact-audit.py`
exits 0: 18 publications, 2,673 exact current binary entries, expected POMs,
36 fresh Compile/Test classpaths, no core/higher ScalaTest or fixture leakage.
Three unchanged earlier graphs are explicitly reused and still match the
generated build: 109 nodes, 213 scoped edges, no cycle.

`python3 /srv/nvme/tmp/izumi-impl/2b-spec-plugin-reconstruction-generator-final.py`
runs the prescribed JDK 21 `bash sbtgen.sc --js --native` command and exits 0.
Before/after hashes of all three generated files are identical to the preceding
checkpoint's recorded hashes. The primary interop Native metadata URLs both
return HTTP 404 at 19:37:22 UTC; exact URLs, responses and times are captured in
`2b-spec-plugin-reconstruction-release-metadata.json` and its two XML files.
This condition leaves the released higher Native lane open.

Actual published runtime closure:
`spec-plugin-reconstruction-published-final-review/` contains 78 frozen artifacts
(54 JARs/24 POMs), 33 source records including 12 unchanged consumer sources,
and 357 exact current core/higher/extension JVM binary entries. Its 39 JDK 21
commands match their explicit expectations: 16 preserved old-binary runtime
controls execute before seven identical-source compilations, then 16 recompiled
controls execute. Direct reconstruction, the same shared loader instance,
original forwarding/registration/bootstrap/property controls and the captured
same-session resource all pass. Both reconstruction residuals fail their original
isolation assertion with successful body outcomes; replaying each with old and
recompiled binaries gives four expected runtime exit-1 records, not four defects.
Their preserving-request controls pass. Consumer classpaths contain only their
own output directory followed by 49 frozen published JARs; no producer classes
or source-shadow candidates are used.

Actual API closure:
`plugin-config-actual-published-api-reconstruction-final-review/` contains 127
artifact records, three unchanged Scala/Java consumer sources, 26 consumer files
and 172 exact extension JVM binary entries. All 20 commands exit 0 on 3.9,
2.13 and 2.12: seven old-binary runs, six same-source compilations, seven
recompiled runs, including the six copy default getters and Scala 3 Mirror.
Both reviewer manifest auditors exit 0. Root's independent
`python3 /srv/nvme/tmp/izumi-impl/2b-spec-plugin-reconstruction-root-publication-audit.py`
also exits 0 and writes separate runtime/API audit JSONs after checking every
artifact/source hash, prior source/binary identity, exact command/classpath,
expected failure cause, current binary set/bytes and immutable producer log.
The runtime log SHA256 is
537b54d66a79346265fff1aa4821183b033efada2176fc6f641edd087bf4ac66.
Root retains its initial API-auditor assumptions separately: the Java compilation
also includes its own consumer output directory, and the substring `recompile`
also matches recompiled runtime labels. Correcting those oracle assumptions
changes no product code, consumer command or runtime result.

The product documentation now qualifies the verified synchronous routes and
names both actual published residual defects. Parent 2b/O.18, the complete
custom-hook audit, compatibility inventory, cancellation, common host integration,
higher Native and final-head gates remain open. Final read-only document closure
and local commit are pending below; the acceptance checklist and owner decisions
are unchanged.

Read-only document closure is recorded in
`spec-plugin-reconstruction-bounded-final-review/document-closure-verdict.json`:
the reviewer finds no additional defect or overclaim in this verified portion.
The revised documents distinguish the corrected synchronous routes from the two
published residual routes. All parent and final gates remain open. `git diff
--check` exits 0 before the local commit; its exact hash is recorded at the next
evaluation point.

## Step 2b: atomic plain registration in progress (2026-10-02)

The verified synchronous custom-loader portion is committed locally as
`22eee8ef9054ce52a0c707e9a02451fa40d378a0`; exact HEAD and a clean working tree
were observed after the commit. No push was performed. Its two published
opaque/handoff reconstruction defects and all parent/final gates remain open.

Hypothesis: `AnyWordSpec.register` checks and sets its ownership flag without a
shared lock, so two sessions may accept the same suite instance. The unchanged
public consumer in
`/srv/nvme/tmp/izumi-impl/plain-concurrent-registration-before/` confirms this:
its separate JDK 21/Scala 3.9 compiler exits 0, then its runtime exits 1 after
both simultaneously entering sessions return successful catalogues on attempt
0. Its exact `commands.json`, frozen published-only `classpath.json`, source,
manifest, `compile.log`, `runtime.log` and `results.json` retain the reproduction.
The probe's literal `resources=0 bodies=0` labels are not counter observations.

Before changing production, the permanent black-box regression fixture runs
through a fresh strict Scala 3 root process. Its command is captured in
`/srv/nvme/tmp/izumi-impl/2b-plain-registration-before.commands.json`; adjacent
snapshot files preserve the unchanged production source and the added fixtures.
Compilation succeeds, then `distage-test-runnerJVM/Test/testFull` exits 1 at
the intended invariant: `synchronous` attempt 1 accepts both `first` and
`second`. The full log and completion JSON retain the failure. Only after
reading this failure, the correction synchronizes the complete existing
registration transition. `AsyncWordSpec` inherits that transition.

Both front ends now have 64 paired shared-instance discoveries. JVM and Native
helpers hold both worker invocations at a start gate; JS queues both calls and
does not claim simultaneous threads. Each pair requires exactly one accepted
owner, a Discovery failure with the existing rejection message for the second,
and an unchanged registered test. Measured counters require both factories,
zero body evaluations and zero reports. Fresh suite instances in independent
sessions remain covered by the existing controls. Final nine-lane execution,
publication, independent consumers, artifact inspection and read-only closure
are in progress; this is not completion of 2b.8 or step 2b.

The first matrix passes Scala 3 JVM/JS/Native, then Scala 2.13 rejects the new
fixture's anonymous async suite: the enclosing implicit `ec` conflicts with
the inherited `executionContext`. This is a fixture compilation failure, not
a production runtime result. The first script, commands, logs and completion
JSONs are retained in `plain-registration-first-matrix/`, with its original
`plain-registration-production-snapshot/`. Moving the two ownership fixtures
to private sibling suite classes removes the competing enclosing context while
retaining inherited async dispatch. Production is unchanged. The restarted
matrix freezes all five final sources in `plain-registration-final-snapshot/`
and rechecks every compiler/platform; earlier fixture passes are not used as
final verification.

The second matrix passes all Scala 3 and 2.13 platforms, then Scala 2.12 rejects
two wildcard discard bindings in one callback block. This is another fixture
compilation failure. `plain-registration-second-matrix/` retains all three
compiler logs, commands, completion JSONs and the script. The fixture now uses
one discard binding for the event and counter increment. Production remains
the one-line synchronized transition. The final matrix starts with 2.12 and
freezes its five sources in `plain-registration-final-verified-snapshot/`.

`python3 /srv/nvme/tmp/izumi-impl/2b-plain-registration-final-matrix.py` now
exits 0 in three fresh root processes. Exact argv and compiler commands are in
`2b-plain-registration-{scala212,scala213,scala3}-final.commands.json`, with
matching logs and completion JSONs. All nine JVM/JS/Native lanes pass 436 base
checks each (3,924 total), including 386 added ownership assertions per lane;
the three JVM bootstrap runs also pass 22 checks each (66 total). All 1,152
paired discoveries retain one accepted and one explicitly rejected owner:
768 pairs use simultaneous JVM/Native workers, 384 are queued JS pairs. All
measured body and report counters remain zero. Native outputs are cleaned in
each compiler process; Scala 3 applies `-Wunused:all` to Compile and Test
through the three exact LocalProject IDs, with no Scala 3 flags on Scala 2.
Native toolchain diagnostics include warnings logged with SBT's error prefix;
the Native executions and actual process exit codes are successful.

`python3 /srv/nvme/tmp/izumi-impl/2b-plain-registration-artifact-audit.py`
exits 0. Its summary checks nine current jars/POMs and all 1,320 binary entries
against their compiled sets and bytes, all 18 fresh Compile/Test classpaths,
the five unchanged source snapshots, all execution/ownership/bootstrap markers,
the expected dependency scopes and absence of ScalaTest/Scalactic and fixture
leakage. The base dependency boundary remains fundamentals assertions and the
portable protocol; Scala 3 still resolves the protocol's 3.8.4 classes. The build
graph inputs are unchanged; this reuses prior verified graph evidence rather
than claiming a fresh graph observation.

`bash /srv/nvme/tmp/izumi-impl/2b-base-runner-consumer-matrix.sh` also exits 0,
captured by `2b-plain-registration-consumers-final.commands.json`, log and
completion JSON. Its independent builds clean and execute all nine compiler/
platform outputs. Nine markers retain four completed sync/Future bodies,
source positions, constructor-captured and overridden execution contexts,
resource-free discovery and event wire round-trips. The root consumer audit
records nine published-only classpaths (145 entries), seven source/build hashes
and the log SHA256 in `2b-plain-registration-consumer-audit.json`.

At 2026-10-02 20:12:42 UTC both primary Native interop metadata URLs still
return HTTP 404 (`2b-plain-registration-release-metadata.json` and adjacent
response files). Higher Native remains pending while independent work continues.
Generator idempotence, published old-binary registration replay, final read-only
closure and the local commit are recorded below when complete. No parent or
final gate is marked done.

Final generation with
`direnv exec . sh -c 'export JAVA_HOME="$JDK21"; exec bash sbtgen.sc --js --native'`
exits 0 (`2b-plain-registration-generator-final.commands.json`, log and
completion JSON). All three generated files retain the prior SHA256 digests:
build.sbt cd9a723e92db4e4652a1ccc91fb92ad7b09f0a982e97ca2b3ec83094f81e6e56,
plugins.sbt 3d66e16d3eb977416f059d7e2ec2ff87c323636db4ea49b2d5bf043431f73c34,
build.properties 669fae6680792604c3020a33e1d814dfef7b17fedb0ff6a843cc520685db984e.
The generator log has no synthetic exit footer; its actual completion records
exit 0 and byte identity. `git diff --check` exits 0.

The independent actual-publication proof is retained in
`/srv/nvme/tmp/izumi-impl/plain-registration-published-final-review/`.
The unchanged fail-first consumer's five old class/TASTy files execute first
against the new frozen base jar, then the identical source compiles and its
five new files execute. All three captured JDK 21 commands exit 0; each runtime
completes 65,536 paired attempts with no double admission and terminates its
executor. This original oracle requires absence of double admission; the
stronger exact-one-owner and measured zero-body/report postconditions come
from the nine producer fixtures, not its literal labels. Consumer classpaths
contain 49 frozen published jars and no production class directories or shadow
sources. The proof records 55 artifact records (54 unique jars and the new
base POM), two source records, all old/new hashes, the five-source final
snapshot, and all 109 exact current base JVM binary entries. The base jar SHA
is 76e2ba7084d3aa0f5f6994cd5f0bc5760fdc90058a7876f4763dfb81139a5008.
Only META-INF/MANIFEST.MF differs between the intermediate and final packaging;
their complete binary sets and bytes are identical. Final runs use the final
packaging. The immutable producer-log SHA is
75ddda327a2b331aa0fe302904323ee916e3c0992cfbe5a27ea603d0e46d7631.

The reviewer's publication `audit.py` and root's independent
`python3 /srv/nvme/tmp/izumi-impl/2b-plain-registration-root-publication-audit.py`
both exit 0. Root verifies every manifest hash, original source and old-binary
identity, the exact 49-jar closure replacement, all current binary bytes,
producer provenance, packaging difference, command/classpath and actual runtime
oracle. Its result is `root-evidence-audit.json`. The reviewer's attempted
Scala 2 source harness stopped while resolving an unrelated unpublished higher
prerequisite; it establishes no compilation or product result. The authoritative
root failures and subsequent final nine-lane results are recorded above.

The product documentation now states atomic admission for both plain front
ends. Read-only source/API review finds no additional defect in this bounded
registration correction; final document closure is pending below. Parent 2b,
the two custom-plugin reconstruction defects, complete compatibility/custom-hook
audits, active cancellation, common host integration, higher Native and final
head evaluations remain open. Acceptance and owner decisions are unchanged.

The final read-only closure is
`plain-registration-bounded-final-review/document-closure-verdict.json`. The
reviewer independently reruns both artifact/publication auditors and verifies
the final nine consumer markers, all 145 published classpath entries, generated
hashes and document scopes. It finds no additional material defect or acceptance
overclaim in this bounded portion. `git diff --check` exits 0 before the local
commit; its exact hash is recorded at the next evaluation point. All parent and
final gates remain open.

## Step 2b/O.18: active cancellation investigation (2026-10-02)

The atomic plain-registration checkpoint is committed locally as
`eb0481071eccddec6190255de5434f6df7a7ee9a`. Exact HEAD and a clean working
tree were observed after the commit. No push was performed.

Hypothesis: the higher provider uses its non-interruptible `runFuture` route
and only samples the session cancellation flag, so cancellation cannot stop an
already suspended effect. Before any correction, a published-only public
consumer reproduces the failure in
`/srv/nvme/tmp/izumi-impl/distage-active-cancellation-before/`. Its exact source,
compiler/runtime argv, frozen 49-jar classpath, source hash and results are
retained there. Compilation exits 0. Runtime exits 1 at the expected active
cancellation assertion after safely opening its body gate and terminating the
probe executor.

Observed: discovery acquires nothing and starts no body; the IO body then holds
one acquired resource. After `session.cancel()`, the run remains pending over
the probe's named two-second observation interval while its body gate stays
closed, with acquired=1/released=0. Manually opening that gate permits normal
completion: the body reports Succeeded, the run's cancelled flag is true, and
released=1 before Finished. The finite interval is a reproduction observation,
not an additional acceptance deadline. The first probe's `IO.fromFuture` wait
is itself uncancelable in the pinned Cats Effect 3.7.1 implementation, so its
closed-gate completion assertion is not a valid cancellation oracle. Its
measurements are retained, but the explicitly cancelable reproduction below
supersedes that assertion. The inspected execution path calls
`effectRunner.runFuture(runner.runPrepared(prepared))` and has no registration
link between `Cancellation.request()` and an active interrupt action. This
supports the missing-link hypothesis; it does not yet verify a correction.

The existing core `RunnerToF.AsyncImpl` runs each nested effect interruptibly and
awaits its interrupt action in guarantee cleanup. QuasiIORunner exposes the
same interruptible Future route on JVM and JS. The higher provider's owned
cancellation link, active finalization/reporting semantics and plain-body
cancellation still require implementation and verification. The plan's later
cancellation/IDE gates remain open; acceptance and owner decisions are unchanged.

Root's corrected published-only reproduction is
`distage-active-cancelable-before/ActiveCancelableProbe.scala`, SHA
e91cc04ee4d4c77945852de0682160404bcce7b48ec6c8ff0c2bd4d9708a0854.
It uses `IO.fromFutureCancelable` with an explicit cancellation action. The
directory retains all 49 jar hashes, exact compiler/runtime argv, source and
classpath manifests, pinned authoritative source captures, and `replay.py`.
`python3 /srv/nvme/tmp/izumi-impl/distage-active-cancelable-before/replay.py`
records compile=0/runtime=1. Root reads the expected failing assertion and
the same measured held-resource state, manual-opening result and terminated
executor. No production source has changed. The reviewer's independent
`distage-active-cancellation-cancelable-before-review/` also records
compile=0/runtime=1 with the same explicitly cancelable wait.

A direct outer interruption substitution has a separate reproduced lifecycle
defect: `active-cancellation-bridge-cancelable-finalizer-before/` uses the
published core `RunnerToF.AsyncImpl`, incoming Cats IO and outer MiniBIOAsync.
Compilation exits 0 and runtime exits 1 for the expected lifetime assertion.
After outer interruption, incoming interruption is invoked once and its
finalizer starts, but the outer execution settles while released=0 and the
finalizer gate is still closed. Cleanup safely opens the gate and observes
released=1. Root reads its exact source, results and runtime log. This rules
out treating a direct outer interrupt link alone as a verified correction.

The reviewer's `active-cancellation-cats-direct-cancelable-control/` and
`active-cancellation-identity-finalizer-control/` each compile and run with
exit 0. Root reads their sources and outputs: direct Cats interruption reaches
the held finalizer without completing execution; Identity's interrupt-action
Future completes before its held finalizer and original execution. The latter
establishes that interrupt-token completion alone is not a general lifecycle
completion contract. These controls support the next implementation decision;
they do not verify an active session correction. Pinned Cats Effect sources:
[Async.scala](https://raw.githubusercontent.com/typelevel/cats-effect/v3.7.1/kernel/shared/src/main/scala/cats/effect/kernel/Async.scala)
and [IO.scala](https://raw.githubusercontent.com/typelevel/cats-effect/v3.7.1/core/shared/src/main/scala/cats/effect/IO.scala).

The completed read-only investigation is
`active-cancellation-readonly-review/REPORT.md` and `evidence-audit.json`.
Root reads both and independently verifies every recorded source, artifact,
command/result and log hash for all seven authoritative cases;
`root-seven-control-audit.json` records exit=0 and 378 artifact records.
The earlier five-case root audit is `active-cancellation-root-controls-audit.json`;
its first schema attempt stopped on non-list classpath metadata, then the
corrected audit verified explicit compiler/consumer lists and exited 0.
No source or artifact changed to satisfy an auditor.

Two additional captures constrain cancellation implementation. The incoming
MiniBIO finalizer probe compiles with exit 0 and fails the expected lifetime
assertion with exit 1: both its interruption action and original execution
settle while acquired=1/released=0 and the finalizer gate is closed. After
manual gate opening and executor termination, released remains 0. The direct
pinned Cats cancellation/finalizer-error control compiles and runs with exit 0:
its finalizer executes and throws once, but the original error is absent from
both returned Future cause/suppressed trees and appears on stderr instead.
Root reads each exact source and output. Neither observation proves behavior
of an unimplemented provider correction. Both prevent claiming that joining
interruption and execution Futures alone meets the full lifecycle/error gates.
Active cancellation, generic effect lifecycle safety, external-interruption
status policy and later host/IDE gates remain open.

## Step 2b: independent Identity interruption reporting (2026-10-02)

Before any correction, the public published-only consumer
`active-cancellation-identity-reporting-before/IdentityReportingProbe.scala`
compiles with exit 0 and runs with exit 1 for its expected reporting assertion.
A body independently throws its original InterruptedException without a
session cancellation request. Observed acquired=1/released=1/bodies=1,
one Failed test, cancelled=false, and an additional Finalization failure:
`Engine reported a repeated test completion`. Root reads its source, original
failure stack, results and runtime log, and verifies its hashes in the seven-case
audit. The stack and inspected source show `guaranteeOnInterrupt` reporting
inside `definitelyRecoverWithTrace`, then Identity's catch-all recovery reaching
the ordinary failure branch and reporting the same test again. This is a
separate reproduced defect; it does not establish active session cancellation.

The permanent shared regression adds two independent InterruptedException
bodies and an ordinary failure to one sequential Identity suite, repeated in a
fresh session. Repeated Cats and ZIO self-interruption controls retain their
observed outcome baselines, identities, phases, body/resource counts, terminal
events and event ordinals. Self-interruption outcomes do not by themselves
prove the external interruption reporting hook was invoked. The reviewer
identifies ZIO's internal-interruption recovery path explicitly; no hook
preservation claim follows solely from a Failed payload.

Initial permanent harness captures are retained separately:
`2b-identity-interruption-before.*` fails fixture compilation for an extra
Lifecycle parameter list; `2b-identity-interruption-before-valid.*` fails for a
missing generic TagK. Both are corrected only in the new fixture and establish
no product reproduction. `2b-identity-interruption-failing-regression.*`
compiles and measures Cats' one incoming Finalization failure and ZIO's zero;
its unsupported equal-count assertion fails. The fixture is corrected to those
observed per-runtime baselines before any production edit.
`2b-identity-interruption-actual-before.*` then passes both repeated controls and
fails Identity's terminal-ID assertion with one body/result and three run
failures. A final diagnostic capture records the exact failure payload below.
All source snapshots, argv, logs and process completions remain under
`/srv/nvme/tmp/izumi-impl/`; none of these preliminary failures is a passing gate.

`2b-identity-interruption-confirmed-before.*` is the final permanent fail-first
capture. Strict Scala 3 compilation succeeds, both repeated Cats/ZIO controls
pass, and the process exits 1 at Identity's exact selected-result assertion.
The added diagnostic records one body/result, acquired=1/released=1 and these
three failures: Finalization `Engine reported a repeated test completion`, then
two Transport reconciliation failures for the omitted selected IDs. Its frozen
source snapshot retains the unchanged core implementation. Root reads this
payload before applying the production correction.

The correction moves `guaranteeOnInterrupt` outside
`definitelyRecoverWithTrace` in `IndividualTestRunner`. Identity's independently
thrown InterruptedException is recovered first and therefore reaches only the
ordinary failure report. Unrecovered effect interruptions still surround that
recovery operation structurally; external interruption hook/status/lifetime
behavior is not claimed verified by these self-interruption controls. No
per-test deduplication state, broad exception-type cancellation predicate,
Cancellation registry or outer/incoming MiniBIO runtime change is added.

Read-only review identifies that a payload-set assertion could permit failure
payloads to be exchanged between logical IDs. Root interrupts only the owned
SBT process before final verification, strengthens the Identity assertion to
compare each logical path with its corresponding original class/message, and
restarts the full matrix. The interruption ownership/PID/argv/signal record is
`2b-identity-interruption-oracle-revision-interrupt.json`. The first process's
actual exit 1, argv/log/completion and original matrix script are retained in
`identity-interruption-first-matrix/`; its original source snapshot remains
`identity-interruption-final-snapshot/`. It is not a final passing matrix. The
final stronger source snapshot is
`identity-interruption-final-verified-snapshot/manifest.json` and its three code/
fixture hashes are checked unchanged after the final matrix.

`python3 /srv/nvme/tmp/izumi-impl/2b-identity-interruption-final-matrix.py`
runs three fresh, sequential root SBT processes at Scala 3.9.0, 2.13.18 and
2.12.21. Each runs `Test/testFull`, shows fresh Compile/Test fullClasspath and
publishes core and higher provider on JVM and JS (four projects per version).
Scala 3 sets Compile/Test `-Wunused:all` using exact LocalProject names; Scala 2
receives no Scala-3-only flag. Exact argv, complete producer output and actual
process exits are retained as `2b-identity-interruption-{scala3,scala213,scala212}-final.*`.
Every process exits 0; the root matrix tool process is observed closed with
exit 0. `2b-identity-interruption-final-test-summary.json` verifies 3,039 checks:
693 core (128 JVM/103 JS per compiler) and 2,346 higher (432 JVM/350 JS per
compiler). The new 60 checks per higher lane total 360; 36 repeated public
interruption cases measure 60 bodies and 36 acquired/released shared resources.
Each Identity case reports all three original failures on their own logical
IDs, with no run failure, no cancellation flag and exactly one start/completion
per test. Cats retains its one incoming Finalization failure per case; ZIO
retains zero run failures. Existing body, planning, provisioning, finalizer,
transport, owner, bootstrap and import-only compatibility controls pass.

`python3 /srv/nvme/tmp/izumi-impl/2b-identity-interruption-artifact-audit.py`
exits 0 and records `2b-identity-interruption-artifact-summary.json` and its log.
It verifies all 12 current published JAR/POM pairs, complete binary entry sets
and exact 2,167 current class/TASTy/Scala.js bytes, fixture exclusion, required
front ends and dependency scopes/layers. All 24 Compile/Test classpaths are
fresh; no prior classpath output is substituted. Generator/build inputs have
not changed, so the prior three exact 109-node/213-scoped-edge graphs are
explicitly reused and checked against the unchanged generated definitions;
this is not a fresh graph execution.

The independent Scala 3 published-only consumer proof is
`identity-interruption-published-final-review/`. Its unchanged original public
Identity source and eight original compiled class/TASTy entries are copied
exactly from the captured failure. The final 49-jar consumer/7-jar compiler
closures are frozen, with only current core/higher published JARs replacing
predecessors. All five commands in `commands.json` exit 0: old-binary runtime
before recompilation, identical-source compilation/runtime, and separate
compilation/runtime of the exact permanent public fixture with a small owned-
executor consumer driver. The original consumer now records one Failed test,
zero run failures, cancelled=false and acquired=1/released=1/bodies=1. The
separate consumer executes all 60 stronger checks, ten bodies and six released
resource scopes. All three consumer executors terminate. No production class directory or
shadow source is on either runtime/compilation dependency path; the test fixture
and driver define no class/TASTy entry present in the published dependency jars.
These are Scala 3 consumer proofs; they do not substitute for the other compiler
lanes' producer or later final-head consumer gates.

`python3 /srv/nvme/tmp/izumi-impl/2b-identity-interruption-root-publication-audit.py`
exits 0 and writes `root-evidence-audit.json`. It verifies all 56 artifact
records, three source records, old-binary identity, exact command/classpath
closures, runtime postconditions, final source snapshot and all 295 exact
current core/higher JVM Scala 3 binary entries. The immutable producer-log SHA
is fe911770fa604d176aad1c172b1de3d0d64dd6a06bdf60bac900d6a65768c1c1.
The proof's `replay.py`, old/new consumer hashes, POMs, jar/class comparison and
producer provenance remain alongside its commands/results and logs.

The prescribed `direnv exec . sh -c 'export JAVA_HOME="$JDK21"; exec bash sbtgen.sc --js --native'`
exits 0 with byte-identical build.sbt, project/plugins.sbt and build.properties.
Before/after hashes, exact argv/log and actual completion are retained as
`2b-identity-interruption-generator-final.*`. The two Native interop primary
metadata endpoints again return HTTP 404 at 21:05:56 UTC on 2026-10-02;
responses/hashes are in `2b-identity-interruption-release-metadata.json` and its
XML captures. Higher Native remains waiting on the release; this independent
reporting work proceeds without waiting. The product documentation describes
one Test-phase report for an independent Identity interruption. Read-only
bounded evidence/document closure follows below before the local commit.

This checkpoint does not close parent 2b, active cancellation, full custom-hook/
compatibility inventories, the opaque/handoff reconstruction ownership defects,
MiniBIO cancellation lifetime/error behavior, host/CLI/IDE integration, higher
Native or final-head evaluation. Acceptance and owner decisions are unchanged.

The read-only closure is
`identity-interruption-bounded-final-review/document-closure-verdict.json`.
Root reads the complete verdict, supplemental audit and replay results, checks
all five reviewed file hashes and every successful runtime marker, and observes
`git diff --check` exit 0. The reviewer finds no concrete residual defect or
acceptance overclaim in this bounded reporting correction, reruns both artifact/
publication auditors with exit 0 and independently replays all five published
commands in separate outputs with exit 0. Its first supplemental audit expected
an EXIT footer in the generator text log; the prescribed generator capture uses
actual completion JSON instead. That auditor assumption is corrected and
retained separately, with no product/source/acceptance change and no duplicate
runtime execution. Parent and final gates remain open. The local commit's exact
hash is recorded at the next evaluation point; no push is performed.

## Step 2b/O.18: MiniBIO cancellation lifetime investigation (2026-10-02)

The bounded independent Identity reporting checkpoint is committed locally as
`e27f9db79e1e5a495fd989cb9cf9793640ee5804`. Exact HEAD and a clean
working tree are observed after the commit. No push is performed. Active session
cancellation and all parent/final gates remain open. The next investigation
addresses the already reproduced incoming MiniBIO held-finalizer abandonment;
no MiniBIO production source has changed at this evaluation point.

Root subsequently reads the read-only design report and auditor under
`/srv/nvme/tmp/izumi-impl/minibio-interruption-design-review/`, inspects the
authoritative public probe sources/runtime logs and reruns `python
/srv/nvme/tmp/izumi-impl/minibio-interruption-design-review/audit-evidence.py`
with exit 0 before editing MiniBIO. That command checks all seven cases' frozen
7-JAR compiler/49-JAR consumer classpaths, all 378 artifact records, source and
log hashes, exact command/result pairs and absence of production definitions in
consumer output. All seven compile with exit 0. Mask, nested-mask, zip child
drain, traversal child drain and inline child signaling fail their named runtime
assertions with exit 1; direct restore is the exit-0 control. The seventh case
reproduces stale callback clear erasing a newer pending request at the internal
`AsyncInterruptRef` component boundary; exact public-fiber reachability of that
interleaving is not established. The probe report preserves this distinction.
The frozen MiniBIO implementation's 23 class/TASTy entries equal current compiled
entries/bytes; unchanged production source SHA256 is
`60bd3740e386a09470d04e762590c66acfbedac9cf59c6efcff24fd8a6d5abcc`.
Public held-gate captures open their gates, settle children and drain owned EC
callbacks before executor shutdown. Initial compile/cleanup harness failures
are retained and do not replace these authoritative captures.

Six shared permanent regressions are added to `MiniBIOAsyncTest` before the
runtime edit. `2b-minibio-mask-before.*` records a settings-reference failure
(unsuffixed cross-project names); `2b-minibio-mask-valid-before.*` records a
fixture type-inference failure. Neither is product reproduction evidence.
`python /srv/nvme/tmp/izumi-impl/2b-minibio-mask-typed-before.py` runs the correctly
named `fundamentals-bioJVM`/`fundamentals-bio-testJVM` strict Scala-3 Compile/Test
settings and `fundamentals-bio-testJVM/Test/testOnly
izumi.functional.bio.test.MiniBIOAsyncTest`. Actual compilation succeeds;
runtime exit is 1: 21 tests, 16 pass and five fail for their expected reasons.
Both masked continuations execute zero rather than once; interrupted async
release executes zero rather than once; a body-error release control preserves
the original error but abandons release; and interruption replaces the original
release exception. The direct-restore control passes. Root reads those five
assertion failures before editing the runtime. Exact argv, completion and both
source snapshots/hashes are retained beside each capture.

The initial runtime edit's `2b-minibio-mask-first-after.*` compilation fails
because `Morphism2` requires its `Instance` constructor; the compile error and
source snapshot are retained, then the constructor is corrected. At this
evaluation point runtime masking/bracket verification remains in progress;
parallel parent joining and active session cancellation remain open. No
acceptance item or owner decision changes.

`2b-minibio-mask-valid-after.*` then compiles and passes the targeted 21 JVM
tests, including the original five failing regressions. Acquisition cancellation,
explicit bracketExcept restoration, failed-region restoration, repeated external
interruption during held release, and the explicit pending execution assertion
are added to strengthen the same scope. The first broader
`2b-minibio-mask-scala3-final.*` process exits 1 at the applicable Cats Sync
`canceled associates left over flatMap` law. Its `/Test/test` task delegates to
testQuick (101 tests/one failure); it is not full final matrix evidence.
Root inspects the actual law failure and `CatsConversions.isUninterruptibleNoOp`:
real masks change that adapter's reference-identity classification from
Uncancelable to Cancelable, exposing the old no-op `sendInterruptToSelf`.
The reviewer preserves a same-source/same-bytecode frozen-publication comparison
under `minibio-self-cancellation-law-baseline-valid` (0/0) and
`minibio-self-cancellation-law-mask-candidate` (runtime 1; explicitly source-shadow
candidate). Primary pinned Cats Effect 3.7.1 MonadCancelLaws/Tests distinguish
these cancellation scopes. No unchanged law or acceptance item is removed.

`2b-minibio-self-interrupt-before.*` compiles and fails the two new self-request
regressions for Success instead of interruption, plus the applicable law.
The first self-request implementation still uses Termination;
`2b-minibio-self-interrupt-after.*` exposes five property failures. Stronger
`2b-minibio-interruption-outcome-before.*` then compiles and fails cleanup count
0/expected 1 and sandbox continuation count 1/expected 0, with an independently
raised InterruptedException (`F.terminate`) control passing. Root reads these failures before
adding actual `Exit.Interruption` propagation. Ordinary recovery frames now
cannot catch cancellation; dedicated bracket frames still run release and
restoration frames unwind either outcome. Execution APIs in the implementation
package return `Exit`, reflecting that additional outcome, rather than falsely
promising `Exit.Uninterrupted`. Published/source/binary compatibility verification
of that change remains pending at this evaluation point.

The JVM law environment now compares explicit Cats Outcome values for success,
error and cancellation. Throwing cancellation through its old `Try` adapter is
invalid: Scala Try excludes InterruptedException, and cancellation is neither
successful return nor ordinary error. The test adapter retains exact error
comparison and marks a canceled property computation unsuccessful; it does not
discard a law. `2b-minibio-interruption-outcome-after.*` compiles and passes all
104 actual tests (shared behavior plus complete applicable Sync laws). The
original independent body/release exception controls and independent
InterruptedException sandbox control pass. Full final matrix command requests
use `/Test/testFull` for both BIO test projects and both core/higher platforms,
fresh Compile/Test classpaths and BIO publications on all three compilers. All
parent/final gates and parallel joining remain open while those checks run.

The first full true-interruption matrix's Scala-3 process exits 0 (430 JVM BIO
tests, 94 JS BIO tests, core/higher fixtures, classpaths and publications).
Scala 2.13 exits 1 at a nonexhaustive Identity adapter match because the proposed
direct execution API widening adds Interruption. This incomplete matrix is
retained as `2b-minibio-interruption-*-final.*`; no Scala-2 pass is inferred.
The independent narrow-public-API controls also establish a concrete source and
binary regression: unchanged `Exit.Uninterrupted` consumers fail recompilation
and old classfiles throw ClassCastException. Root reads their source, E007
diagnostics and actual runtime trace before correcting the proposal.

All five existing direct API signatures (three execution methods plus Sync.a
and Async.register) are restored. Their explicit boundary projection maps
Interruption to Termination while retaining its compound exception, NEList and
trace. The existing UnsafeRun2 APIs retain full Exit.Interruption through the
private interpreter. No old consumer cast/type failure is silently accepted.
The independent `minibio-narrow-wrapper-candidate-review` source-shadow replay
reports six actual exit-0 commands: old classfiles, unchanged narrow sources and
all five signatures, and broad UnsafeRun2 outcome/hook/recovery controls. Those
are candidate evidence, not current published-artifact proof. Actual publication
checks remain required. Shared self-interruption checks and the JVM law adapter
use the existing full-outcome UnsafeRun2 API.

Two further runtime boundaries are reproduced before correction. The independent
held-synchronous-body success capture receives a real request but returns
Success(42); its independently thrown body exception control retains the exact
original error. A throwing release constructor during failed/interrupted use
escapes a recovery callback and leaves execution pending in the asynchronous
probe. Captured inputs/logs under `minibio-true-interruption-candidate-review` and
`minibio-release-constructor-interruption-candidate-review` preserve source-shadow
provenance and owned executor cleanup. Permanent
`2b-minibio-boundary-before.*` compiles and fails 3/108 checks: missing terminal
interruption and both unhandled release-constructor exceptions. Root reads the
named failures before adding successful-Sync exit polling, guarded recovery
callbacks and suspended release construction. Independent failing-body outcomes
still take precedence over release errors, as documented by MiniBIO's existing
policy; this is not an all-finalizer-errors preservation claim.
`2b-minibio-boundary-after.*` compiles and passes all 108 checks. The next complete
matrix freezes all four implementation/test sources and requests full BIO and
core/higher checks on six JVM/JS compiler lanes under
`2b-minibio-boundary-final-matrix.py`. Parent/active-cancellation and parallel
joining gates remain open. No push, owner-decision or acceptance change occurs.

The `2b-minibio-boundary-*-final.*` matrix actually exits 0 for Scala 3 and
Scala 2.13: respectively 434/96 and 435/97 JVM/JS BIO checks, plus the requested
core/higher checks, fresh classpaths and publications. Scala 2.12 exits 1 before
tests because two restore-method type parameters shadow the enclosing trait's
parameters under its pinned fatal-warning policy. Root reads both exact compiler
diagnostics before renaming those parameters. The shared independent-exception
control is also strengthened to `F.sync(throw original)`, distinguishing an
actually throwing thunk from the earlier explicit `F.terminate` control.
The complete rerun uses `2b-minibio-boundary-compatible-final-matrix.py` and a
new immutable four-source snapshot; neither the failed matrix nor its published
Scala-3 proof is overwritten or represented as current final evidence.

The earlier frozen Scala-3 publication replay is preserved under
`minibio-boundary-published-final-review`: the reviewer reports 28 successful
commands and two named held-child join failures, with owned cleanup markers,
against seven compiler/49 consumer JARs. Root reads the join inputs and actual
failure logs: the parent completes while the child's entered finalizer remains
held, then manual release drains both children. This establishes the next
parallel-join correction's failure mode; it does not close parallel or session
cancellation. Root also reads the separate source-shadow publication-race probe
and logs: cancellation during held child enqueue precedes handle publication;
after enqueue resumes the parent settles with two acquired resources, zero
releases and both body gates closed. Its manual cleanup releases both resources
and drains the executor. The source-shadow capture remains candidate evidence
until replayed against an actual publication.

The complete compatible matrix closes all three fresh producer processes with
actual exit 0; root reads their completion files, log footers and named check
counts. Commands/results are `2b-minibio-boundary-compatible-{scala3,scala213,
scala212}-final.{commands,completion}.json` and adjacent full logs. Scala 3
Compile/Test use strict `-Wunused:all`; Scala 2 uses its pinned flags.

| Compiler | JVM BIO tests | JS BIO tests | JVM core/higher checks | JS core/higher checks |
| --- | ---: | ---: | ---: | ---: |
| 3.9.0 | 434 | 96 | 128 / 432 | 103 / 350 |
| 2.13.18 | 435 | 97 | 128 / 432 | 103 / 350 |
| 2.12.21 | 435 | 97 | 128 / 432 | 103 / 350 |

All 1,594 BIO tests and 3,039 core/higher checks pass. The four implementation/
test source hashes remain equal to the frozen compatible snapshot after all
three processes. `/Test/testFull` is used, not testQuick. All six BIO publications
complete. `python .../2b-minibio-boundary-compatible-artifact-audit.py` exits 0
after two retained harness corrections: resolving the actual `${CSR_CACHE}`
placeholder and excluding only an unpublished project's own compiled classes
from the dependency-purity guard. Neither harness failure is product evidence.
Root reads the final summary: six JAR/POM pairs contain 6,424 binary entries,
each entry set/byte matches currently compiled classes, no fixture leaks,
production dependency checks pass, and all 48 fresh Compile/Test classpath blocks
resolve with the required own Test entry exactly once. No prior classpath block
substitutes for this matrix.

The supplemental downstream audit initially fails: four Scala-3 core/higher
JARs differ from their currently tested class files. The exact mismatch inventory
is `2b-minibio-boundary-compatible-downstream-before-publication.json`. Root
refreshes only those four publications with the same strict settings;
`2b-minibio-boundary-compatible-downstream-publication.*` exits 0.
`python .../2b-minibio-boundary-compatible-downstream-artifact-audit.py` then
exits 0: 12 current core/higher JAR/POM comparisons, 2,167 exact binary entries
and 24 fresh core/higher classpath blocks (part of the 48 above). Four Scala-3
publications are fresh; eight unchanged Scala-2 publications are reused only
after exact current-byte equality checks. The three 109-node/213-scoped-edge
graphs are explicitly reused from the earlier observed SBT graph and compared
against the unchanged generated build; each has no cycle.

`python .../2b-minibio-boundary-compatible-generator-final.py` exits 0 after
all matrix processes close. Root reads actual command/completion/log evidence;
the generator (`sbtgen.sc --js --native`, JDK21) preserves all three generated
build-file hashes recorded at the preceding checkpoint. Product documentation
now describes bracketCase masking, complete async release, true UnsafeRun2
interruption and the five retained narrow public API signatures. It continues
to state that active session interruption remains subsequent work.

Root also captures a separate actual-published parallel-combiner defect before
any parallel correction: `minibio-zip-combiner-published-before/replay.py`
compiles the public input with strict Scala 3 (exit 0), then both an independently
thrown IllegalStateException and InterruptedException produce runtime exit 1.
At the named three-second deadline execution remains pending and the exact
exception has reached ExecutionContext.reportFailure instead of an Exit.
Manual parent interruption settles the original execution, and the owned
executor terminates in each case. Root reads both expected failures and verifies
the three command/result/log hashes, source and 56 immutable JAR records
(seven compiler/49 consumer JARs), with no production shadow or class directory.
This is open parallel scope; it does not become a false successful outcome or
an exception-class cancellation heuristic.

The mandatory release boundary check changes the observed external state.
`2b-minibio-boundary-compatible-release-metadata.json` records HTTP 200 at
2026-10-02 22:32:20 UTC for both required Native metadata URLs; root reads their
`release`/`latest` tags as 23.1.0.14. Earlier HTTP-404 captures remain historical
evidence. Under `native-interop-23.1.0.14-release-audit/`, root directly downloads
and audits all 36 POM/JAR responses: both artifacts on JVM, JS and Native, each
for Scala 3, 2.13 and 2.12, all HTTP 200, POM version 23.1.0.14 and valid JARs.
Their Native entries are actual NIR, not local stand-ins. The POMs require ZIO
2.1.26 and Cats Effect 3.7.1; the repository currently pins ZIO 2.1.24 and
interop 23.1.0.5. This establishes availability, not build compatibility.
Item 1a.5 moves from waiting on the release to in progress. Following the brief's
dependency order, work returns to 1a part 2 after this bounded MiniBIO checkpoint;
parallel joining/callback defects remain captured and open. No release-dependent
gate is marked done before compilation, Native linking/execution and publication.

The reviewer additionally captures actual-published short-circuit failures at
`minibio-parallel-short-circuit-published-valid-before/`: strict public-input
compilation exits 0; zip-left, zip-right and traverse runtimes each exit 1.
Root reads the source and all three expected assertion logs, then audits the
four command/result/log hashes and JAR-only compiler7/consumer49 paths (exit 0).
The failing branch settles, but its resource-owning sibling never starts cleanup
within the named observation while its body gate remains closed; the parent
remains pending. Manually opening that gate starts its held finalizer, and
opening the release gate yields the exact original independent Error, one
acquisition/release and complete callback/executor drain. This violates the
public zipWithPar sibling-interruption contract and is retained as open parallel
scope. The first helper failed compilation because fromFuture has a Throwable
error in an infallible release; that harness attempt is retained separately and
is not a runtime reproduction. No parallel implementation changes occur in
this masking/boundary checkpoint.

The final reconciled actual-publication replay is
`minibio-boundary-reconciled-published-final-review/`: 32 exact commands,
30 actual exit-0 controls and two separately named exit-1 child-join reproductions.
Root reads the auditor, command outcomes and concrete masking/finalizer/API/
terminal/constructor/exception logs, then reruns `audit.py` (exit 0).
The immutable compiler7/consumer49 closure uses the actual current BIO, core
and higher JARs, with 60 artifact/provenance records, 11 input sources and 48
old/new consumer binary files; all 1,136 current JVM3 producer entries match
compiled bytes. Old Mask/finalizer/narrow classfiles and unchanged source
recompiles pass. All five original public narrow signatures compile, and broad
constructor/copy/Product, true UnsafeRun2 interruption/hook and ordinary sandbox
controls pass. Two disclosed source adaptations move true-interruption
assertions from the narrow facade to existing UnsafeRun2; no production
definitions are shadowed and no producer directory enters a consumer classpath.

The separate unchanged-source throwing-hook consumer at
`minibio-thrown-hook-reconciled-published-review/` compiles/runs with actual
exits 0/0 against that same reconciled JAR closure. Root reads its source and
actual markers: independently throwing `F.sync` preserves the exact original
InterruptedException as sandboxed Termination and invokes zero interruption
hooks; an owned self-request invokes one hook and bypasses ordinary recovery.
Root reruns `supplemental-audit.py` (exit 0), covering this control and the
retained short-circuit/combiner reproductions. The reviewer's initial
case-sensitive cleanup-marker spelling error is retained separately; inputs
and observed product failures are unchanged.

The read-only review's `BOUNDED-REVIEW.md` and
`bounded-review-verdict.json` find no concrete remaining defect or unsupported
claim within the four-source masking/interruption/callback-constructor scope.
Root reads that verdict, verifies the current four source hashes against the
frozen matrix, and runs `git diff --check` (exit 0). This verified sub-step is
committed locally as `Implement MiniBIO interruption masks; verify six JVM and JS lanes`.
Parallel joins/publication/short-circuit/combiner defects, active session
cancellation, opaque/handoff ownership, complete compatibility inventory and
all parent/final evaluation points remain open. No push, acceptance narrowing,
owner-decision change or whole-goal completion is implied.

## Step 1a part 2: released Native BIO integration (2026-10-02)

The bounded MiniBIO masking/boundary checkpoint is committed locally as
`256091cc7e7f639ccf1950cf28fbc39606459fca`. Root observes that exact HEAD
and a clean working tree before the following Native changes. No push occurs.
The captured parallel and active-session cancellation defects remain open;
release availability returns work to 1a part 2 in the brief's dependency order.

The directly downloaded 23.1.0.14 POMs require ZIO 2.1.26, so the generator
inputs now pin those two released versions together. Cats Effect remains
3.7.1. Only the bounded prerequisite projects `fundamentals-orphans`,
`fundamentals-bio`, their unpublished `fundamentals-bio-test`, and the BIO
assertion adapter gain Native in this checkpoint. The remaining logstage and
distage modules, full lanes L1–L3/L6 and the whole 1a gate remain outstanding.
No item is narrowed or marked done by the presence of these inputs.

Root downloads and reads the actual JVM/Native ZIO 2.1.26 source JARs;
their provenance is `native-interop-23.1.0.14-release-audit/zio-sources-provenance.json`.
Native `ZIOCompanionPlatformSpecific` extends `ZIOPlatformSpecificJVM`, whose
CompletionStage adapter constructs under a mask, restores `asyncInterrupt`
and cancels the Java future with `cancel(false)`. The new Native BIO source
therefore reuses the reviewed JVM CompletionStage integration and its
platform-specific cancellation case. It does not use the spike's older callback
substitute. Reviewed JVM blocking, MiniBIO scheduling and Identity threading
sources are reused; Native avoids SecurityManager lookup and obtains Identity
runner thread-name UUIDs through the existing IzUUID abstraction. Native UUID
generation uses the reviewed getentropy binding, with a named 256-byte maximum
request and checked error returns.

The initial 12-file source provenance is
`1a-part2-bio-native-platform-source-provenance.json`. Read-only source review
corrects an inaccurate new comment claiming Native lacks MAC-address APIs;
the implementation deliberately chooses a random UUID node ID. Existing
Entropy1 byte-array tests use ordinary Scala Random, so they cannot verify
the new secure entropy binding. `NativeSecureRandomTest` directly exercises
zero-length and 255/256/257/1000-byte requests. These checks establish successful
completion across the POSIX request boundary, not statistical entropy quality.
The corrected inputs and all Native sources are frozen and hashed under
`1a-part2-bio-native-first-snapshot/manifest.json` before the first build.

Root runs
`direnv exec . sh -c 'export JAVA_HOME="$JDK21"; exec bash sbtgen.sc --js --native'`;
actual process completion is exit 0, with log
`1a-part2-bio-native-generator-first.log`. The generated diff adds the four
Native aliases/settings/aggregate entries and Native scala-java-time test
dependencies. `direnv exec . python3 -c` with shutil.which verifies clang on
PATH at `/nix/store/sqlnjj8c3n3si3sjnadhdbcwgrk97g2w-clang-wrapper-21.1.2/bin/clang`
and the exported JDK21 at
`/nix/store/p71r5719vxhj2l3qil2bhk6nsi0plj0g-openjdk-headless-21.0.9+10`.
The plan records the newly observed release availability without changing any
gate, fallback, owner decision or default.

Verification in progress: `python
/srv/nvme/tmp/izumi-impl/1a-part2-bio-native-first-matrix.py` starts separate
batch SBT processes for 3.9.0, 2.13.18 and 2.12.21. Its exact argv/commands,
logs and actual exit completions are saved per compiler. Scala 3 applies
`-Wunused:all` to Compile/Test in the four new projects. The matrix checks
versions, compiles the two production modules, runs both Native test projects,
captures eight resolved Compile/Test classpaths per compiler, publishes the
three production artifacts and verifies the test-only project's publication
skip. No compile/link/runtime/publication success is inferred before the
corresponding process completes and its output is inspected.

The first matrix completes with actual exits 0/0/0. Its Native BIO counts are
104/105/105 for Scala 3/2.13/2.12; the BIO assertion adapter prints 13 checks
on each compiler. Root reads the completion records and runtime summaries.
`1a-part2-bio-native-first-artifact-audit.py` exits 0 after comparing all nine
published binary JAR/POM pairs against current entry sets and bytes: 7,488
binary entries, including 5,159 NIR entries, and 24 fresh resolved classpaths.
Production scopes contain no test framework or test-support dependency; the
test-only Native coordinates are absent after all three actual publishLocal
requests. The first auditor attempt exits 1 because it treats every Native
subprocess stderr line labeled `[error]` as a task failure. Root inspects those
lines: they are clang `_FORTIFY_SOURCE` and linker warnings, while the SBT
processes and tests exit 0. That attempt is retained separately; the corrected
auditor checks actual completions and SBT task failures instead of the stderr
label. No production fallback or acceptance change follows from the harness
correction.

Review strengthens the entropy fixture with a 1,000-byte zero-prefilled buffer
and ten independent 100-byte observation windows. Each window must contain a
changed byte. This detects a fill that stops after its first 256-byte request
without using a one-byte statistical assertion. It checks writes throughout
the buffer, not entropy quality or that every individual byte changes.

Before the standalone published consumer, root compares its Native Scala 3
dependency closure with actual Ivy-local bytes. Six older foundation JARs differ
from current compiled output, including the TagExpr$Strings NIR entry set;
the three newly published artifacts match. Captured differences are in
`1a-part2-bio-native-scala3-downstream-before-publication.json`. Root therefore
cleans the entire Native fundamentals aggregate and republishes that closure;
it does not claim a current published consumer from the older artifacts.

`1a-part2-bio-native-final-matrix.py` freezes the strengthened sources and
standalone consumer build, then runs fresh per-compiler processes with
`fundamentals-native/clean`, `fundamentals-native/Test/testFull`, the four new
projects' eight classpath captures and `fundamentals-native/publishLocal`.
The Scala 3 process completes with exit 0: 299 ScalaTest cases, no failures,
and 86 plain/12 Cats/13 BIO assertion checks, followed by publication. Scala 2
processes and final artifact/public-consumer verification remain in progress.

The standalone `test-fixtures/assertion-consumer` now also defines
`bioConsumerNative`, pins ZIO 2.1.26 and supplies Native scala-java-time. Its
existing public source will check deferred evaluation, repeated independent
defects and the unchanged typed-error channel against published artifacts.
It has not run at this evaluation point. Item 1a.5 is still in progress along
with every parent/final gate. The broader read-only port inventory is
`1a-part2-remaining-native-inventory/INVENTORY.md`; source counts and API review
there do not establish Native compatibility.

The clean Native final matrix then completes with actual exits 0/0/0.
Observed ScalaTest totals are 299/295/295; Native BIO contributes 105/106/106,
including all six direct secure-random cases. Assertion checks total
111/111/108: plain range-mode 86 on Scala 3/2.13, default point-mode 83 on
2.12, plus Cats 12 and BIO 13 on each compiler. Root's initial summary/auditor
incorrectly assumes range mode for 2.12; the captured point-mode marker corrects
that assumption. This final auditor's first exit-1 attempt is retained
separately, with no test or product correction and no acceptance change.

`1a-part2-bio-native-final-artifact-audit.py` subsequently exits 0. It checks
all 39 current Native fundamentals binary JAR/POM pairs against the clean
compiled entry sets and bytes, including 14,673 binary/9,368 NIR entries,
checks the 24 fresh new-project Compile/Test classpaths and all Native
fundamentals JUnit XMLs against the logged totals. All XMLs report zero
errors/failures/skips; the secure adapter suite reports six cases per compiler.
All production POMs remain within fundamentals and contain no test-framework
dependency. The six unpublished test-only Native coordinates and the legacy
ScalaTest Native coordinate are absent after aggregate publishLocal. Full
evidence is `1a-part2-bio-native-final-artifact-summary.json`; root's actual
process completions are recorded separately. This closes the stale local
dependency publication discrepancy observed above, not the broader L6 gate.

At 23:13:18 UTC on 2026-10-02, direct primary Maven metadata checks report
release/latest ZIO 2.1.26 and interop 23.1.0.14, both HTTP 200. XMLs,
timestamps, URLs and hashes are
`native-interop-23.1.0.14-release-audit/current-version-discipline-metadata.json`.
The web reader could not access those XML endpoints; direct HTTP reads provide
the actual version evidence. Neither a local nor a snapshot interop is used.

Root now runs `1a-part2-bio-jvm-js-regression-matrix.py`: separate Scala
3.9.0/2.13.18/2.12.21 processes run both full fundamentals aggregates and the
four higher core/provider fixture lanes, with strict Scala 3 Compile/Test
options on the twelve impacted projects, 24 fresh classpaths per compiler and
local publication of both fundamentals aggregates plus the four higher
artifacts. Frozen sources/argv/commands/logs/completions are retained. These
checks verify the released dependency upgrade on existing JVM/JS targets;
they do not stand in for L1's three-JDK full repository matrix, L2's full
repository lanes, or any 1a parent/final evaluation.

The JVM/JS regression matrix completes with actual exits 0/0/0. Root reads
all final markers and resolves the collection report arithmetic before claiming
counts. The first seven ScalaTest cases are IzEither, not a duplicate JSON run;
they are included in the collections project's combined 33-case result. The
language project's one case also uses the base framework on JVM. Original
production-module XMLs are historical; their test-class directories contain
no class files. Only the current five moved test projects' XMLs are counted.
`1a-part2-bio-jvm-js-regression-artifact-audit.py` exits 0 and reconciles
916/918/918 fundamentals cases with the mixed-framework logs, plus 1,013
higher core/provider checks per compiler (128/103 core and 432/350 provider).
It compares all 90 published JVM/JS fundamentals/core/provider JAR/POM pairs
with current compiled entry sets and bytes: 17,651 binary entries, including
5,493 JS IR entries, and 72 fresh resolved Compile/Test classpath blocks.
Current XMLs have zero errors/failures/skips; production scopes remain free
of test-framework/test-support dependencies. BIO remains 434/96, 435/97,
435/97 on JVM/JS. Both BIO assertion targets report 13 checks per compiler.
Parent L1/L2 remain outstanding.

The separate published consumer uses exact copies of the five tracked
`test-fixtures/assertion-consumer` inputs under
`/srv/nvme/tmp/izumi-impl/1a-part2-assertion-published-consumer/`, with their
source/copy SHA256 manifest. It executes its own build outside the checkout;
no producer directory is on any consumer classpath. The first launcher attempt
exits 1 before compilation because the default SBT thin client cannot connect
to a newly started server. That exact script/argv/log/completion is retained.
The corrected batch command adds `--server` and exits 0, after cleaning all
six targets. JVM/JS/Native each print the plain/unary consumer marker and
JVM/JS/Native each print the BIO consumer marker. The existing BIO source
checks suspended condition evaluation, two independent defects and the
unchanged typed-error channel through actual published adapters.

`1a-part2-assertion-published-consumer-audit.py` exits 0: all six actual
Compile classpaths are JAR-only, each izumi producer JAR equals the current
publication auditor's hash, all consumer binary definitions belong to the
fixture namespace, and all five source/copy hashes match. There are 103
distinct actual JAR records, six own consumer JARs and six classpath blocks
with 12/17/24/19/30/31 entries. The auditor's first attempt rejects the absent
`.jar` suffix on a resolved SBT content-addressed symlink target; its logical
classpath path is a valid JAR. That failed harness capture is retained, then
logical entry validation, real-file hashing and archive CRC/namespace checks
are applied separately. All 103 actual JAR bytes are subsequently frozen under
the consumer's `frozen-jars/`, with verified hashes in
`1a-part2-assertion-published-consumer-frozen-summary.json`. These are frozen
copies of the artifacts actually used, not source-shadow replacements.

The final prescribed `--js --native` generator completes with exit 0 and
byte-identical build.sbt, plugins.sbt and build.properties. Exact argv,
before/after hashes, log and actual completion are
`1a-part2-bio-native-generator-final.*`. Root observes `git diff --check`
exit 0. Read-only Native-phase review independently reruns the artifact audit
with exit 0 and finds no concrete remaining Native source/runtime/publication
defect in this bounded prerequisite. Final combined evidence/document review
follows before the local commit; every whole-step and final gate remains open.

The final combined read-only review is
`1a-part2-bio-combined-final-readonly-review/FINAL-REVIEW.md`. It independently
reruns the Native, JVM/JS and separate-consumer auditors (all actual exit 0),
verifies all 103 frozen consumer JAR hashes, and checks the final generator's
actual completion and byte-identical before/after/current outputs. Across all
three compilers, every shared JS BIO case remains on Native, with exactly nine
additional Native cases: six entropy, two synchronous-terminal interruption
and one CompletionStage cancellation case. The review finds no concrete
residual defect or unsupported completion claim in this bounded prerequisite.
Root reads the final report and verifies all 28 source/configuration and three
document hashes in `reviewed-input-manifest.json`; its SHA256 is
`b77147a6e525de68bfd332bec06e553a73292e6a57f10909935ed8d60030c589`.
Root runs `git diff --check` before committing. This verified sub-step is
committed locally as `Enable Native BIO with released interop; verify nine lanes`.
The remaining Native logstage/distage ports, full L1–L6 evaluations, all parent
and final gates, active session cancellation and captured MiniBIO parallel
failures remain open. Work continues in the brief's dependency order; no push
or acceptance/owner-decision change occurs.

## Step 1a part 2: Native logstage checkpoint (2026-10-03)

The Native BIO prerequisite is committed locally as
`57f33433a31a57f3413bfcfcbf28138042ed0812`; root observes that exact HEAD
and a clean working tree before the logstage port. The fixed acceptance scope
remains every JS module except the legacy adapter, with item 1a.11 separately
requiring Native implementations/tests or documented unavailability for the
file sinks, threaded queue and JUL adapter.

The two previously JVM/JS logstage modules now select `Targets.cross` and core
adds its Native scala-java-time Compile dependency. JVM-only SLF4J modules
retain their existing targets. Root reads the JVM file services/models/sink,
real/dummy file suites, queue and rendering helper before adding Native sources.
The 13 unchanged JVM-derived source/test files and SHA256 provenance are
`1a-part2-logstage-native-source-provenance.json`. The existing five-case
abstract file suite runs against both the real filesystem service and the
hand-written in-memory service. The existing shared asynchronous sink test
checks delivery of 100 messages after start/close. The new Native queue suite
adds a controlled held flush: close must remain pending during an observed
200ms interval and complete after release, with cleanup in finally. A second
case checks synchronous flush and sync for a message appended after close.
These feature tests are verification candidates, not yet passing evidence.

Pinned Scala Native 0.5.12 Runtime source implements add/removeShutdownHook;
root reads that primary source and captures its HTTP response/hash. Direct
inspection of the actual resolved javalib JAR finds zero entries under
`java/util/logging/`. `1a-part2-logstage-native-api-evidence/api-provenance.json`
records those observations, not a JUL compile/link reproduction. Native JUL
remains unavailable and will be documented. The downloaded scala-java-time
2.6.0 Native source JAR supplies TimeZone initially set to UTC; its source
explicitly leaves host-zone discovery unimplemented. The reused TimeOps calls
ZoneId.systemDefault and does not establish host-zone parity. Source-JAR
provenance is `java-time-provenance.json` in the same evidence directory.

Root regenerates with the prescribed `--js --native` flags; the actual process
exits 0. `1a-part2-logstage-native-first-matrix.py` freezes all generator and
Native inputs, then starts three separate batch processes for Scala
3.9.0/2.13.18/2.12.21. It applies strict Scala 3 Compile/Test unused checks,
cleans the Native logstage aggregate, runs its full tests, captures four fresh
Compile/Test classpaths per compiler and publishes both Native artifacts.
Exact argv, logs and actual completions are saved per compiler. No build,
runtime, publication or whole-step success is inferred before inspection.

The first Native matrix completes with actual exits 0/0/0 and identical
per-compiler totals: 106 core cases and three Circe-rendering cases. Root
reads final markers, the two queue-case outputs, real/dummy file cases and
current XML reports. `1a-part2-logstage-native-first-artifact-audit.py` exits 0,
comparing all six published JAR/POM pairs to exact current compiled entry sets
and bytes: 3,449 binary entries, including 2,040 NIR, and 12 fresh Compile/Test
blocks. Sixteen XML reports per compiler have zero errors/failures/skips.
Production graphs contain no test framework/support dependency. JUL definitions
and test fixtures are absent from production JARs; the two JVM-only SLF4J
Native coordinates are absent.

A subsequent producer-publication recheck finds seven of the 39 earlier Native
foundation publications differ from newly compiled bytes after the generated
graph changes, including five Scala 3 artifacts and one BIO entry on each Scala
2 compiler. The exact list is
`1a-part2-logstage-native-fundamentals-publication-recheck.json` (32 matches,
seven differences). This is a current-output/local-publication discrepancy;
the earlier checkpoint's historical comparisons remain valid. Current producer
publications will be reconciled before this checkpoint's final public-consumer
proof, rather than describing those older JARs as current compiled output.

Read-only review identifies a Native shutdown-lock hypothesis beyond the
explicit-close tests: Runtime.runHooks holds its hook monitor while waiting for
a hook whose stopPolling path calls removeShutdownHook and acquires that same
monitor. A separate public Native process-exit probe is being built with frozen
published inputs, explicit-close and no-queue controls, and a debugger capture
if it stalls. No fix is applied from source inference alone. Root's tracker
searches for shutdown/removeShutdownHook/runHooks deadlocks yield no matching
report; unrelated GC/filesystem results are not evidence for this hypothesis.

`1a-part2-logstage-jvm-js-regression-matrix.py` runs both full aggregates,
including the two existing JVM-only SLF4J modules, across three compilers, with
12 fresh classpath blocks and six publications per compiler. Its first attempt
exits 1 before tests: it incorrectly gives the JVM-only project IDs a JVM
suffix. Root resolves the actual generated IDs, preserves the first script,
argv/log/completion/input freeze, corrects those two IDs and starts the captured
batch again. The final input freeze covers the shared/JVM/JS sources that these
lanes actually read, so Native-only changes are not misrepresented as inputs
to those regression processes. The user-facing Native capabilities paragraph
records JUL and SLF4J unavailability and the initially UTC time-zone policy.

The corrected JVM/JS regression matrix completes with actual exits 0/0/0:
110 JVM cases (105 core, three renderer and one per SLF4J module) and 97 JS
cases (94 core, three renderer) per compiler. Root reads logs and current
XMLs. `1a-part2-logstage-jvm-js-regression-artifact-audit.py` exits 0 and
compares 18 current JAR/POM pairs, 4,010 binary entries including 1,232 JS IR,
and 36 fresh Compile/Test blocks. All 31 XML reports per compiler have zero
errors/failures/skips; production scopes retain their bounds. Its first audit
attempt exits 1 because the existing SLF4J adapter main-resource directory uses
SBT's `${BASE}` path alias. The failed script/completion are retained; resolving
that alias alongside `${OUT}`/`${CSR_CACHE}` verifies the actual directory,
without changing product code or tests.

The shutdown defect is reproduced before its fix in
`logstage-native-shutdown-published-before/`: a JAR-only separate consumer
freezes 36 actual dependency JARs and nine producer POMs. Core's complete
current compiled binary entry sets/bytes match its publication; the five older
Scala 3 foundation publications differ from current output and are explicitly
disclosed. No production definition is source-shadowed. Build exits 0;
no-queue and explicit-close controls exit 0 in approximately 16ms. The unchanged
unclosed-queue input prints its marker, fails to exit during a five-second
observation, then its owning harness kills/reaps that child. This duration is
a reproduction observation, not a new acceptance deadline.

The separate GDB capture shows the actual hook thread blocked in
Runtime.removeShutdownHook and the exit thread joining the same hook object
`0x7fde79836a80` while Runtime.runHooks holds the registry monitor. Root reads
the full stacks, consumer/source/argv/runtime logs and final REVIEW.md, confirms
the pinned Runtime source equals the primary source capture, and reruns the
independent `audit.py` (actual exit 0). All fixture-owned children and the
GDB inferior are reaped. Initial public-consumer FileConverter, duplicate
Native runtime closure and CAS/class-directory auditor harness failures remain
separately captured; none substitutes for this actual runtime failure.

Only after that expected failure is inspected, root changes the Native queue's
private stopPolling boundary to take an explicit unregisterHook Boolean.
The shutdown callback passes false, avoiding acquisition of Native's registry
monitor while Runtime waits for that callback. Explicit close and the inherited
poller-interruption path pass true. Draining/joining behavior remains in the
Native implementation. This mitigates the reproduced queue deadlock; it does
not repair the upstream Runtime monitor behavior or claim verification of other
poller-interruption cases. JVM/JS sources remain byte-identical to their tested
freeze. A minimal Runtime-only public reproduction and unfiled upstream draft
are being captured separately, including Native's single-threaded control.

Root runs `1a-part2-logstage-foundation-publication-reconciliation.py` in three
separate batch processes, publishing all three fundamentals aggregates on each
compiler; actual exits are 0/0/0. It freezes all foundation sources and build
inputs, and does not consume the concurrently corrected logstage source.
Current publication-byte verification follows. Root then starts
`1a-part2-logstage-native-final-matrix.py`, with a new source freeze and three
clean Native compile/link/runtime/publication lanes for the corrected queue.
The historical first matrix and before-publication reproduction remain intact.
Final tests, exact published-consumer replay and read-only review remain
required before this sub-step is committed; whole 1a/final gates remain open.

All three corrected Native final lanes complete with actual exits 0/0/0,
again reporting 106 core and three renderer cases per compiler. Root reads the
final markers. `1a-part2-logstage-native-final-artifact-audit.py` exits 0 with
six current JAR/POM pairs, 3,449 matching binary entries including 2,040 NIR,
and 12 fresh classpath blocks. Test/production boundaries and unavailable
Native JUL/SLF4J coordinates remain verified. Source equality against the
JVM/JS regression freeze holds for all 126 tested shared/platform/build inputs,
so the Native-only queue correction does not invalidate those six lanes.

The retained `1a-part2-logstage-foundation-reconciliation-artifact-audit.py`
exits 0 after the final Native lanes, verifying the thirteen non-legacy
foundation production families across nine lanes: 117 current JAR/POM pairs
and 30,157 exact binary entries. It also checks frozen foundation/build inputs
and production layer/test-dependency bounds. This comparison closes the seven
previously observed publication discrepancies; it does not include the legacy
JVM/JS assertion bridge in its production-purity claim. Aggregate publication
commands remain broader than the explicitly named audit scope.

The final prescribed `--js --native` generator completes with actual exit 0;
build.sbt, plugins.sbt and build.properties are byte-identical before/after.
Exact argv/hashes/log/completion are
`1a-part2-logstage-native-generator-final.*`. Root observes `git diff --check`
exit 0. The current generated build hash is
`5776970ce00360cd110e5ec54adc2623caf89158f8bbc9798072f430643e3df8`.

The independent Runtime-only public reproduction is
`scala-native-runtime-shutdown-hook-public-before/`: ten frozen Scala/Native
library JARs, zero Izumi dependencies/source shadows, explicit Native
multithreading enabled. Three controls exit 0; removal from its own concurrent
shutdown hook times out and the GDB stacks show the same lock/join cycle.
The identical compiled public classfiles on JDK 21 reject removal with
IllegalStateException and exit 0, matching the primary Java 21 Runtime API
contract. A separately retained detected single-threaded Native control also
rejects removal and exits 0, so the report is explicitly scoped to concurrent
shutdown. Root reads the public source, draft and auditor, checks the primary
API documentation, and reruns `audit.py` with actual exit 0. All owned children
are reaped. `DRAFT-ISSUE.md` remains unfiled; no external message or upstream
modification occurs. The upstream monitor defect remains; the Native queue
avoids its reproduced trigger.

The corrected public-consumer replay in
`logstage-native-shutdown-published-after/` builds two separate JAR-only
consumers on each compiler. Their closures freeze 36/35/35 dependency JARs
and nine producer POMs per lane. The nine consumed producer binary entry sets
and bytes match the current compiled outputs at capture. The original
three-mode source is byte-identical to the failing before reproduction;
no-queue, explicit-close and unclosed-queue modes now exit 0. The first
held-sink drain probe also exits 0 and records 32 flush calls. Its IDs come
from a delivery counter, so that first oracle establishes count, not original
message identity or ordering. Root reads and reruns its retained `audit.py`
with actual exit 0; the historical proof remains unchanged.

Root strengthens that drain oracle in the separately retained
`logstage-native-shutdown-published-final-review/`, using the same frozen
published closures. The sink persists each original integer message argument,
while a held first flush leaves all 32 entries undelivered before System.exit.
A separate shutdown hook releases the sink. The exact output must be the
ordered list 1 through 32, detecting omissions, duplicates and reordered
delivery. The drain source SHA256 is
`ebe63b029f8e58e53a0565b656fbb71a74e54e64c31021785d8a3ed414bfd6ad`;
the unchanged original three-mode source is
`5affb7a2bd94d33942d0c84a80c129fbf05637362ea078b7628fb852d9e87d46`.

All three final separate builds/link commands exit 0. Root runs
`python3 logstage-native-shutdown-published-final-review/runtime-replay.py`
with each of `3.9.0`, `2.13.18` and `2.12.21`, from the scratch evidence root.
All twelve controls exit 0 without timeouts; each actual held-drain output
contains the original IDs 1–32 once, in order. Exact build/runtime argv,
completions, logs, source/artifact/executable hashes and physical drain outputs
are retained per compiler. Root reads the final auditor and runs
`python3 logstage-native-shutdown-published-final-review/audit.py`, actual exit
0: six JAR-only classpaths, no producer class directories or source shadows,
frozen hashes intact and all fixture-owned children reaped. This establishes
the reproduced queue shutdown correction and the controlled drain behavior;
it does not establish arbitrary concurrent-producer shutdown semantics.

The final read-only reviewer finds no concrete residual defect or unsupported
completion claim in this bounded checkpoint. Its retained report is
`1a-part2-logstage-final-readonly-review/FINAL-REVIEW.md`, SHA256
`10cc70e4fb3b1c72b3f86976f21ec3810775a24ffaa361fd3687ee36eae88345`.
It reruns the three Native, JVM/JS and foundation artifact auditors with actual
exits 0. Independent XML multiset comparisons retain every one of the 94 JS
core cases in Native's 106: exactly ten real/dummy file cases and two queue
cases are added. Native omits JVM's single JUL case. Its direct consumed-closure
comparison rereads all 27 frozen/current published/current compiled producer
JARs, matching 15,993 binary entries, and verifies all 133 original/frozen
JAR/POM hashes. Generator before/after/current hashes agree.

Root reads that final report, the direct comparison script and recorded
completions, then verifies all 37 reviewed input hashes (24 checkout inputs,
13 evidence inputs) before this review-provenance append. The retained
`current-input-manifest.json` SHA256 is
`915373ebc694cc8885e36834739875613febf9cc3302fac166cd098e0d313f1a`;
its ledger hash intentionally names the reviewed pre-append version.
Root checks the final diff and commits this verified sub-step locally as
`Enable Native logstage; verify nine lanes and shutdown drain`. The remaining
Native distage ports, full step-1a evaluations, all parent/final gates, active
session cancellation and captured MiniBIO parallel failures remain open.
Work continues in the brief's dependency order, without pushing or changing
acceptance/owner decisions.

## Step 1a part 2: Native distage core and APIs (2026-10-03)

The logstage checkpoint is committed as
`d2d5d23163c25298b24dbab38a2c443121997e64`; root observes that exact HEAD
and a clean working tree before this next slice. Core API, framework API and
core now select cross targets, and core adds Native test scala-java-time.
Five platform sources are copied with provenance in
`1a-part2-distage-core-native-source-provenance.json`. The closed-world mirror
and disabled dynamic proxies reuse JS boundaries with a Native diagnostic;
the graph observer reuses JVM filesystem output. Native ZIO borrows its
default executor as JS does; no executor shutdown ownership is claimed.

Root reads the actual released Native source JARs for Cats Effect 3.7.1 and
ZIO 2.1.26, retained in `1a-part2-distage-native-api-evidence/` with HTTP URLs
and SHA256s. Cats' Native compute factory returns compute, polling API and
shutdown; the Native port retains the poller and owns the third-field cleanup
through Lifecycle. Blocking workers and runtime registration also have explicit
resource release. Named CPU/IO overrides feed the actual runtime without
transferring ownership of application-supplied executors. These are candidate
implementation semantics pending execution, not yet verified postconditions.

Native feature checks now exercise poller discovery, scheduled effects,
termination of observed default compute/blocking workers, custom named
executors and their retained ownership, Cats resource acquisition/release,
both ZIO defaults, and actual GraphViz filesystem output. They supplement the
unchanged shared suites; no parent or final evaluation is marked passed.
The prescribed generator runs with actual exit 0; the generated build hash is
`aa7b3ede3c25bdfe843bf87fbb075ee9541b5f0d053a39a4c53b557288b0268a`.
Its argv/log/completion are `1a-part2-distage-core-native-generator-first.*`.

The first clean strict Scala 3 lane exits 1 after compiling/linking the API and
core tests. Its current XMLs record two core-API cases and 341 core cases,
with exactly one failure in the newly written Cats default-resource fixture.
The effect returns Some(Succeeded), because ScalaTest's assert returns its
Assertion value; the fixture incorrectly expects Some(Unit). Root reads the
actual failure and freezes all first-run XMLs before editing. This is a fixture
oracle defect, not a resource/provisioning failure. The source freeze, argv,
log, completion and `1a-part2-distage-core-native-first-xml/` preserve it.

The first runtime checks of observed Cats compute/blocking worker termination,
named executor overrides and actual graph-file output pass, as do both initial
ZIO defaults. Root corrects the Cats fixture to return Unit explicitly. The
Native ZIO candidate is then changed to acquire a separate Java work-stealing
executor through Lifecycle.fromExecutorService and pass it through
Executor.fromJavaExecutor, following the dependency/ownership rule. The revised
default-module fixtures run yielding ZIO bodies through their injected runner
and require the executor service and observed worker to terminate after
release. This supersedes the first candidate's borrowed default executor;
shared/JVM/JS sources remain unchanged. Revised verification is still required.

The second clean Native matrix completes Scala 3 with actual exit 0: two
core-API and 341 core cases, zero failures; framework API has no test cases.
Its six fresh classpaths and three Native publications are captured. Scala
2.13 then exits 1 during compilation of the new test fixtures: the final assert
in a finally block and in a Unit-returning helper implicitly discard
ScalaTest's Assertion under the project's fatal value-discard checks. Root
reads both diagnostics, preserves the second source/argv/log/completion/XMLs,
and explicitly binds those assertion results to val _. No production change
is made for these fixture compile errors. Scala 2.12 has not run yet.

The third clean Native matrix again completes Scala 3 with actual exit 0 and
the same two core-API and 341 core cases. Scala 2.13 now compiles the tests,
but core Native linking exits 1 with exactly two unreachable types,
`zio.managed.ZManaged` and its companion. The captured reachability chains
include interop Cats instances and Cats traversal through QuasiPrimitives;
they do not establish that the test directly uses ZManaged. Root reads the
actual linker diagnostics and freezes 38 available XMLs in
`1a-part2-distage-core-native-third-xml/` before any subsequent clean.
Scala 2.12 has not run. The third source freeze, argv, log and completion remain
unchanged.

A separate fresh classpath capture exits 0, recorded as
`1a-part2-distage-core-native-scala213-link-classpath.*`. Its three blocks
(core API Compile, core Compile, core Test) contain 38, 38 and 63 entries.
Only core API Compile contains zio-managed; neither core classpath contains
it. The released Native 2.13 interop POM declares zio-managed 2.1.26 optional.
This is an observed dependency boundary; the upstream/library cause is still
under investigation with a separate release-only probe. No dependency has
been changed for this failure. The probe's first invocation fails while
loading its build because the standalone SBT 2 harness has no `%%%` extension;
that is retained as `bootstrap-*`, and does not reproduce the linker failure.
The corrected harness uses explicit released Native artifact names.

The corrected simple release-only Async probe links with actual exit 0 without
Managed. Adding Cats IO List traversal to a separate probe reproduces the
expected two unreachable Managed types with actual exit 1, using no izumi
artifacts. Its 28 dependency JARs and POMs are frozen with hashes in
`zio-interop-native-traversal-before/dependency-manifest.json`. An unchanged
source control adding only Managed links and executes with exits 0 and the
expected marker. A separate control uses the released public
`zio.interop.CatsEffectInstances` class instead of the bundled catz object,
and links and executes with exits 0 without Managed. The released sources
show catz inherits eager Managed instance fields; the direct class supplies
the same ZIO Async/Parallel implementations without those fields. The simple
probe's success limits the finding to programs reaching the reported dispatch
paths; merely summoning Async does not always fail.

Root changes the shared ZIOCatsEffectInstancesModule to hold a private
CatsEffectInstances instance and supply both bindings from it. No library
dependency is added or made mandatory. This is a shared correction because
the DI module's bundled instance selection causes the unwanted boundary; JVM
and JS regression execution is required. The Native interop default fixture
now invokes both injected Async and Parallel and checks their computed value.
A fourth clean three-compiler matrix will verify this correction. The upstream
tracker searches find no matching report; a minimal unfiled draft is pending.

All fourth-matrix producer commands exit 0 and publish the nine Native
JAR/POM pairs. The core cases execute as 341 on Scala 3 and 308 on Scala 2.13.
Scala 2.12 executes 307 successful core cases and cancels one inherited
Functoid constant-type case, guarded in the shared source by a Scala >= 2.13
assumption; all three core-API lanes execute two successful cases. Root's first
artifact audit fails because it requires zero cancellations. Root inspects the
actual cancellation, shared source and XML before refining the audit to
recognize exactly this compiler-specific existing case, with no unknown
cancellation allowed. This does not mark a parent acceptance item done or
change the suite's policy. JVM/JS reconciliation of the same case is pending.

ScalaTest's Scala 2.12 XML records the canceled case as a testcase without
a skipped child, and reports skipped=0. Therefore XML alone is insufficient
to establish that every registered case executed; the audit records the actual
ScalaTest result line and canceled-case marker alongside the XML identities.
No zero-cancellation or 308-executed-case claim is made for this lane.
The initial auditor is preserved as
`1a-part2-distage-core-native-fourth-artifact-audit-first-attempt.py`.

The released-library reproduction's independent dependency/source audit exits
0 (`zio-interop-native-traversal-audit.py/.json`): 28, 29 and 28 frozen JARs
and matching POM counts; the Managed control adds only its JAR/POM with
byte-identical source and all preceding dependency bytes unchanged. The direct
instance control uses the original 28 dependency bytes. Both executables exit
0 with the expected marker. The minimal report is retained as
`zio-interop-native-traversal-DRAFT-ISSUE.md`, explicitly unfiled and limited
to the reproduced traversal/dispatch path.

The revised fourth Native artifact audit exits 0: nine JAR/POM pairs, 9,806
current binary entries including 6,428 Native IR entries, and 18 fresh
Compile/Test classpaths. All published binary-entry names and bytes match
current compiled outputs; no new Native fixture is published, and production
classpaths/POMs exclude ScalaTest/test support and Native ByteBuddy. The audit
records the sole inherited Scala 2.12 cancellation separately from executed
cases. Its summary is `1a-part2-distage-core-native-artifact-summary.json`.

The first separate published Native consumer run passes Identity and effect
programs on Scala 3 with exit 0. Scala 2.13's Identity executable also passes,
but the effects fixture fails compilation: accessing
DefaultModule.forZIOPlusCats.module without an expected DefaultModule type
infers environment Nothing and conflicts with invariant Tag[R]. The compiler
then reports four downstream Any-typed tuple errors. Root reads the primary
type mismatch and preserves this first fixture/log/completion. The final
fixture gives the selected default module the explicit DefaultModule[Task]
type, as the producer test already does through its parameter. No production
change is made for this fixture inference failure. Final published consumer
verification uses a separate directory and all three compilers.

All JVM/JS regression producer commands exit 0 across the three compilers.
The exact artifact audit exits 0: 18 JAR/POM pairs, 9,888 current binary entries
including 3,140 JS IR entries, and 36 fresh classpaths. Core executes
400/366/365 JVM and 335/302/301 JS successful cases on Scala 3/2.13/2.12,
plus two API cases on each lane. Both Scala 2.12 core platforms cancel the
same inherited constant-type case as Native; no additional case is canceled.
The registered-case multiset audit finds every JS case on Native, with exactly
the six new Native behavior cases added for each compiler. The summary is
`1a-part2-distage-core-jvm-js-regression-artifact-summary.json`; these bounded
regressions do not complete L1/L2 or any parent evaluation.

All six final published Native consumer commands execute successfully, but
their first complete closure audit fails an exact-byte check for a previously
published fundamentals-platform Native IR entry. Root freezes every consumed
JAR/POM before changing publications and audits all 36 consumed producer pairs.
Entry sets match current outputs for all 36; eight pairs differ in 119 Native
IR entries, with no class/TASTy differences. They are six Scala 3 foundation
modules (platform, functoid, bio, functional, language and collections) and
bio on both Scala 2 versions. No source-change cause is inferred from this
binary observation. `1a-part2-distage-core-native-consumed-closure-before-*`
retains exact differences; each final consumer's
`consumed-before-reconciliation-manifest.json` retains frozen inputs. The
reconciliation republishes those eight current compiled prerequisite pairs
with separate recorded commands. Consumers must rerun against those actual
publications before the checkpoint is claimed.

The prerequisite reconciliation exits 0 for all three compiler processes;
root directly compares every binary entry of the eight republished JARs to
current classes and finds exact equality. A separate reconciled consumer build
again executes all six programs with exits 0. Its first auditor then rejects
Identity's classpath using an overly broad dev.zio group check: required
izumi-reflect and standalone tracer artifacts belong to that group but are
not the ZIO effect runtime. Root reads the actual classpath before correcting
the audit to check the concrete Cats and ZIO runtime/interop artifact families.
Identity omits Cats and the ZIO core effect library; it still resolves the
required reflection/tracer dependencies. No production dependency is changed
for this audit predicate correction. Managed and Streams remain absent from
both consumer classpaths. The original predicate is retained in the
reconciled auditor's `-first-attempt.py` file.

A further auditor failure corrects the earlier stated absence of Managed and
Streams in all consumer classpaths. Root reads all three released interop
POMs: the Native Scala 3 POM declares Managed, Streams, Cats MTL and FS2 as
ordinary transitive dependencies, while both Scala 2 POMs mark them optional.
The ordinary Scala 3 effects consumer therefore includes those libraries;
its successful execution does not prove their absence. The earlier collective
absence claim is superseded. The observed POM differences are retained in
`zio-interop-native-optional-dependency-platform-differences.json`.
A final explicit-exclusion consumer control will exclude Managed and Streams
on every compiler; the Identity consumer's classpath remains free of Cats
and the ZIO core effect library, with reflection/tracer auxiliaries present.

During final source review, root finds the new Native createCPUPool helper has
no Native/shared caller; its JVM counterpart is used only by JVM-specific
tests. It is removed from the new Native file. The newly copied Native proxy
diagnostic names the retired cglib implementation; its wording is corrected
to generated proxies. These are cleanup of this port's additions, not changes
to JVM/JS behavior. A fifth clean Native matrix and final published consumers
will verify the resulting sources; fourth-matrix evidence remains historical.

All fifth clean Native producer processes now complete with actual exits 0.
The six final published consumer commands also complete with actual exits 0;
they explicitly exclude Managed and Streams on every compiler and record the
matching fifth producer completion path/hash before each compiler's build.
The Scala 2.12 consumer waits for that producer to finish before loading its
publications. Full classpath/byte auditing remains required before the absence
and final publication claims are made. The fifth producer's first artifact
audit invocation was started prematurely and exits 1 on a missing Scala 2.12
completion file, before that producer finished. This is an orchestration
precondition failure, not a compilation, test or artifact failure. Root observes
all producer completions and reruns the audit only after completion.

The final prescribed generator runs with actual exit 0 and identical before/
after hashes for all three generated files. build.sbt remains
`aa7b3ede3c25bdfe843bf87fbb075ee9541b5f0d053a39a4c53b557288b0268a`;
its command/completion/log are `1a-part2-distage-core-native-generator-final.*`.
No generator input has changed during the final source cleanup. The reviewer
reports no concrete source defect after reading all nine Native sources/tests,
the generated targets, shared lifecycle/runtime modules and released Native
CE shutdown/polling implementation. Final evidence review is pending.

The completed final audits exit 0. The fifth Native producer audit verifies
nine JAR/POM pairs and 9,803 current binary entries, including 6,425 Native IR
entries, against the exact current compiled entry sets and bytes. Its 18 fresh
Compile/Test classpaths satisfy the production/test dependency boundary. The
final published consumer audit verifies all six executable markers, six fresh
classpath blocks, and all 36 consumed izumi JAR/POM pairs (12 nonlegacy Native
families on each compiler): 23,440 binary entries, including 15,209 Native IR
entries, equal their current compiled outputs. It freezes 434 consumed JAR/POM
references with hashes. Both consumer classpaths omit Managed and Streams on
all three compilers under the explicit exclusions; Identity also omits Cats
and the ZIO core effect library. Own core effect dependencies remain optional
in its POM. Required reflection/tracer auxiliaries remain present.

The final bounded nine producer lanes execute 3,043 successful cases: 962
Native, 1,137 JVM and 944 JS. Each Scala 2.12 platform cancels the same inherited
constant-type case; those three cancellations are separately recorded, not
counted as execution. The final Native registered-case identities still include
every JS case and exactly six additional Native core behavior cases per
compiler. Framework API has no cases. These results are for this three-module
checkpoint on JDK 21, not the complete L1/L2/L3 or final-head gates.

The final verification entry points are below, all under
`/srv/nvme/tmp/izumi-impl/`. The scripts retain the exact SBT argv, source hashes,
compiler/classpath captures, full logs and actual completion records. Every
listed command has observed exit 0; earlier failed attempts remain retained.

| Command | Final captured evidence |
| --- | --- |
| `python3 1a-part2-distage-core-native-fifth-matrix.py` | `1a-part2-distage-core-native-fifth-snapshot/manifest.json`, `1a-part2-distage-core-native-scala{3,213,212}-fifth.*` |
| `python3 1a-part2-distage-core-native-fifth-artifact-audit.py` | `1a-part2-distage-core-native-artifact-summary.json` |
| `python3 1a-part2-distage-core-jvm-js-regression-matrix.py` | `1a-part2-distage-core-jvm-js-regression-snapshot/manifest.json`, `1a-part2-distage-core-jvm-js-scala{3,213,212}-regression.*` |
| `python3 1a-part2-distage-core-jvm-js-regression-artifact-audit.py` | `1a-part2-distage-core-jvm-js-regression-artifact-summary.json` |
| `python3 1a-part2-distage-core-native-published-consumer-final-head.py` | `1a-part2-distage-core-native-published-consumer-final-head/{3.9.0,2.13.18,2.12.21}/` source manifests, commands, logs and completions |
| `python3 1a-part2-distage-core-native-published-consumer-final-head-audit.py` | `1a-part2-distage-core-native-published-consumer-final-head/audit-summary.json` and frozen dependencies |
| `direnv exec . sh -c 'export JAVA_HOME="$JDK21"; exec bash sbtgen.sc --js --native'` | `1a-part2-distage-core-native-generator-final.*`, identical before/after hashes |

The implementation/source freezes are based on predecessor
`d2d5d23163c25298b24dbab38a2c443121997e64` plus this checkpoint's working-tree
changes. The JVM/JS regression freeze remains applicable after the final
Native-only cleanup because neither its source inputs nor build inputs changed.
Root verifies and freezes all 322 final producer XML reports, Native and JVM/JS,
under `1a-part2-distage-core-final-report-freeze/manifest.json`; their bytes
equal the hashes recorded in the two final artifact summaries.
Final read-only review and the local checkpoint commit are pending. Items
1a.2, 1a.3, 1a.6 and 1a.10 remain in progress; no complete step or final
evaluation point is declared done.

The final read-only review finds no concrete bounded defect or completion
overclaim. Its report is
`1a-part2-distage-core-native-final-readonly-review/FINAL-REVIEW.md`, SHA256
`c86b506a30ba7907b8ded305765b8ddbc86b3a3f393cef5b152b32df249ce04a`.
The reviewed input manifest is `final-input-manifest.json` in that directory,
SHA256 `af8b3bfacd34465f7e0fd3c7731ed4246d1c0feccea0861926a8bbe8507567e1`.
Root reads the complete report and verifies all 108 manifest hash records
(20 candidate inputs and 88 evidence files) before this provenance append.
The reviewed ledger hash is
`2a2c4ab157eeae2d2892a82a32b4bfdac89637338b5c8edd1da221dc7dfb19c0`;
only this review-provenance record is appended afterwards. Root separately
checks all 287 Native and 1,644 JVM/JS current/frozen input pairs, recorded in
`1a-part2-distage-core-native-root-final-snapshot-check.json`, with exit 0.
The review launches no SBT, generator, publication or Native executable. Its
three artifact-auditor reruns rewrite only their deterministic evidence
summaries; root verifies those resulting hashes against the final manifest.
The review's first direct input checker uses the wrong argv index for the
shell payload and fails; its retained correction exits 0 without changing
product inputs. Root's first manifest checker expects a nonexistent `files`
key and fails; after reading the actual manifest keys, the corrected checker
verifies both input groups with exit 0. Neither harness failure is presented
as a product failure. This bounded checkpoint is ready for a local commit;
complete-step and final-head gates remain open.

## Step 1a part 2: Native configuration extension (2026-10-03)

The core/API checkpoint is committed as
`fde7bc046c881f18ed9affb605f5f86805d2fdce`; root observes that exact HEAD
and a clean working tree before this slice. Native configuration follows the
owner's JS Circe JSON default. Ten JS main/test platform sources are reused,
with Native derivation comments and the Native quoted-path diagnostic;
provenance is `1a-part2-distage-config-native-source-provenance.json` under
`/srv/nvme/tmp/izumi-impl/`. The first copy also includes three ignored SBT
stream files; root observes and removes only those newly copied artifacts
before freezing sources, retaining their original copy manifest and hashes.

The Native config model retains String file-source paths as the existing JS
model does. Circe core/generic are Native Compile dependencies; parser and
scala-java-time are Native Test dependencies. No HOCON library is added.
Public documentation states JSON, custom/automatic decoders, unquoted paths
and unavailable derived schemas. Three Native boundary cases supplement the
shared config suite: lazy defaults only for absent paths, domain errors for
missing/quoted paths, and explicit-over-fallback nested JSON precedence.
Candidate separate published consumers derive sealed/nested config types with
custom decoders and provision them through DI on all three compilers.

Verification is pending. Items 1a.2, 1a.3, 1a.6, 1a.7 and 1a.10 remain in
progress; no complete-step or final-head evaluation point is closed.

The prescribed generator exits 0; its command/log/completion are
`1a-part2-distage-config-native-generator-first.*`. build.sbt is now
`bd3511e36e5cda9ed49699dcc04cd8cf8f0072015958d831f33c19b3ef5eb1f0`;
the other two generated file hashes remain unchanged. The first matrix freezes
only the actual source/resource trees and five build inputs, not platform
`target/` directories. Scala 3 and 2.13 producer processes exit 0: each Native
lane executes 16 successful cases and each JS lane 13; JVM executes 29 and 30
respectively, including its platform-specific metadata/optional-dependency
suites. No cancellations occur. Scala 2.12 is still running.

The first separate Scala 3 published fixture exits 1 at compilation because
it calls Injector.produce with a ModuleDef alone, while the public overload
requires explicit roots (or a PlannerInput/Plan). Root reads the single E134
overload-mismatch diagnostic before giving the fixture Roots.Everything.
The first build/source/argv/log/completion remain frozen in
`1a-part2-distage-config-native-published-consumer-first/`; the original
template is `1a-part2-distage-config-native-published-probe-first.scala`.
No production change is made for this fixture error. A separate final
consumer driver runs all three compilers and freezes their consumed JAR/POM
bytes after each successful process. Its producer-completion barriers avoid
reading a compiler's publications while that producer writes them.

All three producer processes and the three corrected published consumer
processes complete with actual exits 0. The nine producer lanes execute 175
successful cases: 48 Native, 88 JVM and 39 JS, with no cancellation. Root's
first artifact-audit predicate mistakenly expects the extra Scala 2.13 JVM
case on Scala 2.12 as well. Root reads the actual summaries and the 2.13-only
OptionalDependencyTest213 source, preserves the original auditor, and checks
JVM counts as 29/30/29. The corrected audit exits 0: nine JAR/POM pairs,
1,983 current binary entries, 417 Native IR entries, 354 JS IR entries,
and 18 fresh Compile/Test classpaths. Every JS case is present on Native,
which adds only its three named JSON boundary cases per compiler. Production
Native dependencies contain Circe core/generic, without HOCON, circe-derivation,
Test-only parser, ScalaTest or test fixture entries.

The corrected consumers execute automatic sealed/nested derivation, the custom
decoder, DI provisioning, missing defaults, invalid-value rejection and JSON
fallback merging, each with its compiler-version macro check. Their first full
closure auditor nevertheless rejects a previously published Scala 3 core IR
entry whose bytes differ from the newly compiled output. Their complete JAR/POM
inputs were already frozen per compiler, before any reconciliation. Root's
complete before audit finds 36 consumed producer pairs: all entry sets match,
while ten prerequisite pairs differ in 140 Native IR entries, with no class or
TASTy differences. Eight are Scala 3 core/API and foundation pairs; both Scala
2 BIO pairs differ in one IR entry each. The producer log shows prerequisite
recompilation; it does not establish the cause of those IR byte differences.
No source-change cause is inferred. The before summaries and differing current
compiled bytes are retained under
`1a-part2-distage-config-native-consumed-closure-before-*` and each consumer's
`compiled-before-reconciliation/`; all consumed dependencies remain under
`consumed-dependencies/` with their manifests. Those ten current prerequisites
will be republished with recorded commands, followed by separate consumers.

The ten-pair prerequisite reconciliation completes with actual exit 0 on each
compiler. Root directly compares all ten republished JAR binary entry sets and
bytes with current compiled outputs and finds equality. Separate reconciled
consumers then execute all three public programs with exits 0 and the expected
marker. Their complete audit exits 0: three fresh JAR-only classpaths, 36
consumed producer JAR/POM pairs, 24,056 current binary entries including 15,531
Native IR entries, and 216 frozen consumed JAR/POM references. All required
own production dependencies are in the closure; no ScalaTest, test helper,
ByteBuddy, Cats Effect, ZIO core, zio-interop-cats, Managed/Streams, HOCON,
circe-derivation or Test-only parser appears on those consumers' classpaths.
Required izumi-reflect, zio-stacktracer and zio-interop-tracer auxiliaries remain
present. Circe's required Cats core/kernel dependencies remain present; no
Cats-free claim is made.
Producer and reconciliation completion paths/hashes are checked before each
compiler's consumer build. The first consumers' older inputs remain frozen.

The final prescribed generator exits 0 with identical before/after/current
hashes for all three generated files, recorded in
`1a-part2-distage-config-native-generator-final.*`. Root directly verifies all
75 source/build input pairs against the first producer freeze, then verifies
and freezes all 19 final test report files under
`1a-part2-distage-config-final-report-freeze/manifest.json`. All 175 producer
cases and three final published programs succeed. These are bounded
configuration checks on JDK 21, not full CI/JDK or final-head gates.

Final commands, all with observed exit 0, run from the checkout unless a
captured consumer command names its separate build directory. Script/log
paths below are under `/srv/nvme/tmp/izumi-impl/`.

| Command | Final captured evidence |
| --- | --- |
| `python3 1a-part2-distage-config-native-first-matrix.py` | `1a-part2-distage-config-native-first-snapshot/manifest.json`, `1a-part2-distage-config-native-scala{3,213,212}-first.*` |
| `python3 1a-part2-distage-config-native-first-artifact-audit.py` | `1a-part2-distage-config-native-artifact-summary.json` |
| `python3 1a-part2-distage-config-native-prerequisite-publication-reconcile.py` | `1a-part2-distage-config-native-prerequisite-publication-scala{3,213,212}.*` |
| `python3 1a-part2-distage-config-native-published-consumer-reconciled.py` | `1a-part2-distage-config-native-published-consumer-reconciled/{3.9.0,2.13.18,2.12.21}/` source/dependency manifests, commands, logs and completions |
| `python3 1a-part2-distage-config-native-published-consumer-reconciled-audit.py` | `1a-part2-distage-config-native-published-consumer-reconciled/audit-summary.json` |
| `direnv exec . sh -c 'export JAVA_HOME="$JDK21"; exec bash sbtgen.sc --js --native'` | `1a-part2-distage-config-native-generator-final.*`, idempotent hashes |

The candidate/source freeze is predecessor
`fde7bc046c881f18ed9affb605f5f86805d2fdce` plus this slice's working-tree
changes. The reviewer reports no concrete source defect after reading all
eleven Native files, shared reader/module wiring, dependency targets and docs.
Final evidence review and the local checkpoint commit are pending. The fixed
parent/step/final acceptance statuses remain in progress.

The final read-only review finds no residual concrete defect. It identifies
one ledger wording discrepancy, now corrected: `zio-interop-cats` is absent,
while the distinct `zio-interop-tracer` auxiliary remains present. The complete
report is
`1a-part2-distage-config-native-final-readonly-review/FINAL-REVIEW.md`, SHA256
`6e58e8c35c7867075a6ec126caf91ef8dabfeb5fe2678e9d8a09233556906c28`.
Its reviewed manifest `final-input-manifest.json` has SHA256
`b5e6485785fdb10ed8db3d552cdf618867e7449d7c32e38248aca2e885f7e99c`.
Root reads the complete report and verifies all 146 input/evidence records,
including the current/frozen source pairs, before appending this provenance.
The reviewed ledger hash is
`63a8551132a8cb9e5fd30b0907e190ca4da714d83840f8b5d03a6340722b5de2`;
only this review-provenance record follows it. The reviewer directly checks
current files without rerunning supplied auditors, builds or programs, and
writes only in its new scratch review directory. A reviewer platform-key
assumption (`js` versus `sjs1`) initially fails its checker; the schema
correction passes without product changes. Root's first manifest checker
expects the previous review's input-key names and fails; after reading this
manifest's schema, it verifies all 81 repository and 65 evidence records with
exit 0. These are checker failures, not product failures. This bounded
configuration checkpoint is ready for a local commit; full parent and final
gates remain open.


## Step 1a part 2: Native logging and plugin extensions (2026-10-03)

Configuration is committed as `3924521bd552de88dc2a2d60608bf49908f15df9`;
root observes that HEAD and an empty working-tree status before this slice.
The logging and plugin extension targets become `Targets.cross`. Logging
uses its existing shared production sources and adds two Native cases that
provision and execute suspended LogIO effects through injected Cats IO and ZIO
runners, checking the public alias bindings and sink output without installing
a static router. Four Native plugin cases exercise empty/disabled-only
requests, explicit plugin/merge/override provisioning, a supplied constant
loader and rejection of enabled runtime packages through both default factories.
The Native default loader reuses the audited JS loader with a Native exception
class and diagnostic; source/hash provenance is
`/srv/nvme/tmp/izumi-impl/1a-part2-distage-extensions-native-source-provenance.json`.
Classgraph remains a compiler-host dependency for the shared static macros.
Public documentation distinguishes explicit and compile-time plugins from
unavailable runtime package scanning on JS/Native.

The first prescribed generator exits 0, recorded in
`1a-part2-distage-extensions-native-generator-first.*` under the same scratch
root. build.sbt is `7bbaa07930a514e33422670f5fdf52fb19a3b6a0580f0997bd4aec6ac5662321`;
plugins.sbt and build.properties retain their preceding hashes. The candidate
matrix freezes the two extension modules and all thirteen Native production
prerequisite modules, then requests clean/testFull/publication on Native, JVM
and JS with all three compilers. Its separate per-compiler closure publisher
will publish every current Native prerequisite in dependency order before
any published consumer runs. Separate consumers will scan a compiled dependency
fixture module and execute both explicit and compile-time plugin bindings and
the effect loggers. Verification is pending. Parent 1a.2/3/6/7/10 and final
L1–L6 gates remain open.

The first Scala 3 Native logging run executes all three cases: the inherited
case passes and both new effect cases fail only on their output assertion.
Root reads the diagnostics and Log.Message definition: template is a
StringContext, whose toString returns `StringContext(ArraySeq(...))`, rather
than the message text. The assertions now compare template.parts directly;
no production correction is made. The failed source freeze, commands,
log/completion and two XML reports remain retained, the latter under
`1a-part2-distage-extensions-native-first-report-freeze/`. The candidate
published-consumer driver's producer barrier exits 1 before starting any build;
its launch completion explicitly records that boundary. A separate second
matrix/consumer path will capture the corrected runs without overwriting the
first evidence.

The corrected Scala 3 producer and its fifteen-pair Native closure publication
both exit 0. The second consumer's separately compiled plugin fixture exits 1:
its inherited PluginDef DSL macros explicitly require -Yretain-trees, which the
standalone fixture omitted. Root reads both diagnostics and the repository's
compiler option before adding that option (and the existing max-inline bound)
to a separate third consumer driver. The second fixture's source/build inputs,
commands, log and actual completion remain frozen. No production correction
is made for this fixture precondition.

A direct post-publication audit of all fifteen Scala 3 Native pairs compares
9,527 class/TASTy/NIR entries against current compiled outputs. All entry sets
match, and only fundamentals-bio differs, in two NIR entries (FileLockMutex and
__ZIORaceCompat). Class and TASTy bytes match. This disproves the assumption
that a successful complete closure publication alone establishes byte identity.
The published JAR/POMs and differing current NIR bytes are retained under
`1a-part2-distage-extensions-native-publication-before-audit/`. Cause remains
unestablished. The remaining producer lanes are still running; final consumers
will wait for a verified byte-identical closure.

All three corrected producer matrices and all three complete closure publishers
exit 0. The independent extension artifact auditor exits 0: eighteen JAR/POM
pairs, 1,069 current binary entries including 247 Native IR and 190 JS IR
entries, thirty-six fresh Compile/Test classpaths, and 48 successful cases
(21 Native, 24 JVM, three JS), with no cancellations or failures. Every
registered JS case is present on Native; each compiler adds the six named
Native cases. The plugin JS project compiles its shared fixtures and has zero
registered suites. Production extension POMs preserve the existing boundaries:
logging depends on config/core-api/logstage-core, plugins on core-api plus
compiler-host Classgraph; their concrete effect and test libraries stay Test.
No test fixtures are published.

The complete post-publication comparison checks forty-five Native producer
pairs and 27,764 binary entries. Only the already identified two Scala 3 BIO
NIR entries differ; all Scala 2 pairs match. The complete before JAR/POM inputs
and differing NIR files are frozen under
`1a-part2-distage-extensions-native-complete-publication-before-audit/`.
A single recorded Scala 3 fundamentals-bioNative/publishLocal process exits 0;
the reconciliation driver then directly verifies all fifteen pairs on each
compiler against current compiled entry sets and bytes. Scala 2 reconciliation
runs only the comparator because those publications already match; it does not
claim an extra publication process. All forty-five pairs now match. No source
or dependency change is made for the IR delivery discrepancy; cause remains
unestablished. The third standalone consumer now runs with the required Scala 3
macro options and matching producer/publication/reconciliation completion hashes.

The third separate published consumers complete on all three compilers with
actual exits 0 and the expected NATIVE_PUBLISHED_EXTENSIONS_OK marker. Their
separate fixture project defines a class and object PluginDef; the consuming
project expands both PluginConfig.compileTime and StaticPluginLoader against
that compiled dependency. Each Native executable provisions both plugin
bindings, verifies explicit override precedence and empty compile-time scanning,
rejects runtime package scanning with the Native diagnostic, then provisions
and executes suspended injected IO and Task logging effects and their aliases.
Each fixture also expands ScalaReleaseMaterializer and verifies its compiler
argument. These are execution results, not link-only observations.

The complete final public-consumer audit exits 0: six fresh JAR-only classpaths,
forty-five consumed producer JAR/POM pairs, 27,764 current binary entries including
17,695 Native IR entries, 458 frozen consumed JAR/POM references across the two
classpaths per compiler, and 97 frozen compiled fixture entries. The latter
are the consumer/fixture binaries, not extra producer entries. All resolved
producer bytes and required own POM dependencies match the current compiled
closure; no checkout classes or sources shadow publications. Classgraph remains
present for the static macro host. The plugin-only fixture classpaths contain
no concrete ZIO, Cats Effect or Circe core/generic; the executable consumer
explicitly requests ZIO and Cats Effect and receives the JSON backend through
logging's config dependency. Managed, Streams and zio-interop-cats are absent.
Required reflection/tracer auxiliaries and Circe's Cats core/kernel remain
present. No ScalaTest, TestSink, test helper, HOCON, circe-derivation, Test-only
parser or ByteBuddy contaminates these production consumer classpaths or JARs.

The final prescribed generator exits 0 with identical before/after/current
hashes for all three generated files. Root directly verifies all 901
current/frozen source/build pairs and all 24 final report files against the
artifact-summary hashes, then freezes those reports under
`1a-part2-distage-extensions-final-report-freeze/manifest.json`. These establish
the bounded eighteen-project JDK 21 checkpoint. Full CI/JDK and final-head
gates remain open; no complete parent step is marked done.

Commands below run from the checkout except the captured standalone consumers,
whose commands name their separate build directories. Paths are under
`/srv/nvme/tmp/izumi-impl/` unless stated otherwise. Every final command has
observed exit 0; the retained first assertion failure and second fixture
precondition failure are explicitly historical evidence.

| Command | Final evidence |
| --- | --- |
| `python3 1a-part2-distage-extensions-native-second-matrix.py` | `1a-part2-distage-extensions-native-second-snapshot/manifest.json`, `scala{3,213,212}-second.*` and `scala{3,213,212}-closure-publication.*` under the `1a-part2-distage-extensions-native-` prefix |
| `python3 1a-part2-distage-extensions-native-artifact-audit.py` | `1a-part2-distage-extensions-native-artifact-summary.json` |
| `python3 1a-part2-distage-extensions-native-publication-reconcile.py` | `1a-part2-distage-extensions-native-scala{3,213,212}-publication-reconcile.*`, fifteen compared pairs per compiler |
| `python3 1a-part2-distage-extensions-native-published-consumer-third.py` | `1a-part2-distage-extensions-native-published-consumer-third/{3.9.0,2.13.18,2.12.21}/` source/build/compiled/dependency manifests, commands, logs and completions |
| `python3 1a-part2-distage-extensions-native-published-consumer-third-audit.py` | `1a-part2-distage-extensions-native-published-consumer-third/audit-summary.json` |
| `direnv exec . sh -c 'export JAVA_HOME="$JDK21"; exec bash sbtgen.sc --js --native'` | `1a-part2-distage-extensions-native-generator-final.*`, idempotent hashes |

The candidate is predecessor `3924521bd552de88dc2a2d60608bf49908f15df9`
plus this slice's working changes. The first read-only source/producer review
reports no concrete defect and independently verifies all 901 frozen input
pairs, eighteen extension publications, forty-five reconciled Native pairs,
thirty-six classpaths and all 48 XML cases. Final consumer/evidence review and
the local checkpoint commit are pending. Parent 1a.2/3/6/7/10 and L1–L6
statuses remain unchanged and open.

The final independent read-only review reports no residual concrete defect or
overclaim. Its full report is
`1a-part2-distage-extensions-native-final-readonly-review/FINAL-REVIEW.md`, SHA256
`9dff1d7746c9fdfa9a2f6bcdbd779c666a9177a267023a7b9d56b8c10d02c9d5`;
its final-input-manifest.json SHA256 is
`8ab2d623e66848f0acdc5d06b412b062a5b1a5b26dfb14738adc3886985b1520`.
Root reads the complete report and verifies all 906 repository and 86 evidence
records, including the 901 current/frozen source pairs. The reviewed ledger
SHA256 is `15afd415c653d1440f26cc77fa4903d3be5e8cec1c00919e005bbc080dd67189`;
only this review-provenance record follows that reviewed text. The reviewer
independently reads captured commands/logs, hashes, ZIP bytes and XML, writes
only in its new review directory, and runs no supplied auditor, build,
publisher, generator or target program. The bounded extension checkpoint is
ready for local commit; the whole-step and final-head gates remain open.


## Step 1a part 2: Native framework candidate (2026-10-03)

The logging/plugin checkpoint is committed as
`bdffb423d2810f367ff0c5c305b583e64cd24fc6`; root observes that HEAD and
an empty working-tree status before beginning this slice. No whole-step or
final acceptance gate is completed by that bounded checkpoint. Framework
1a.2/3/6/7/10 and L1–L6 remain in progress.

Before choosing Native framework capabilities,
`/srv/nvme/tmp/izumi-impl/1a-part2-distage-framework-native-capabilities.py`
builds and executes default and embedded variants on Scala 3.9.0/JDK 21/Native
0.5.12. Actual exit is 0 and both expected markers occur once. Both variants
create/write/read/delete an owned UTF-8 JSON file and set/read/restore an owned
system property. The included resource is null with default embedding disabled;
embedding enabled reads the exact UTF-8 bytes through ClassLoader and absolute
Class resource streams. Missing streams return null in both. The report,
source/build/command/completion manifests and pinned NativeConfig/ClassLoader/
runtime Class source provenance are retained under the matching scratch
directory. These are runtime API observations on Scala 3, not framework
implementation or Scala 2 proof. Native uses streams because its ClassLoader
has no getResource URL API.

The unchanged shared JvmExitHookBlockingShutdownStrategy is reproduced on Native
before adding a correction. Final driver
`1a-part2-distage-framework-native-shutdown-before-final.py` copies byte-identical
AppShutdownStrategy.scala and DebugProperties.scala with hash provenance,
compiles them against published prerequisites, and runs a forked JVM process-exit
control before Native link. Actual build exit is 0. The JVM control reaches
await-returned and cleanup-completed markers. The same Native executable's manual
control exits 0 and reaches both markers; its Runtime.exit input prints readiness
and reaches neither marker during a ten-second observation, after which the
owning harness kills and reaps it. This duration is a reproduction observation,
not an acceptance deadline. Exact executable SHA256 is
`5d6961f47a1157d9ece1654635d9423c5069618814b256ced5e3871ff634fb76`.
Root reads the actual logs/completions, not just the driver's aggregate result.
The monitor/join mechanism is supported by the pinned Runtime source and the
earlier independently reproduced Runtime-only deadlock with GDB stacks.

Two retained earlier harness attempts do not establish this Native failure:
`...shutdown-before/` runs Runtime.exit inside SBT's JVM and terminates SBT
before the Native commands; `...shutdown-before-forked/` passes the proper JVM
control but Native link rejects basename native because it conflicts with its
work directory. The final fixture forks the JVM and sets a distinct Native
basename. Neither requires a product change.

Only after reading the expected failure, root adds NativeShutdownStrategy.
It signals the post-cleanup latch before hook removal, retaining the graceful
wait while avoiding the registry-monitor acquisition that prevented cleanup.
Repeated requests use trySuccess; duplicate awaits fail explicitly. The first
after driver builds successfully on Scala 3 and the manual, Runtime.exit,
duplicate-await, early-request and no-await controls all exit 0 with their
expected markers. Its SIGTERM control reaches cleanup but exits 1 with
IllegalMonitorStateException in Thread.join, rather than the expected signal
exit 143. That driver exits 1; Scala 2 lanes have not run in that attempt.
The first sources, dependencies, executable, logs and completions are retained
under `1a-part2-distage-framework-native-shutdown-after-first/`, including an
explicit launch completion. A separate after-second driver will verify the
five non-signal controls on all compilers without overwriting that run.

The signal failure is isolated in
`scala-native-runtime-signal-public-before.py`, with no Izumi dependencies or
source replacements. It builds Native and JVM variants from the same public
source, with Native multithreading enabled; actual build and driver exits are 0.
Every JVM input completes correctly: Runtime.exit gives 0, and five externally
delivered SIGTERM modes give 143, with hook/cleanup markers as applicable.
On Native, Runtime.exit gives 0 and the bare-hook and main-thread-sleep SIGTERM
controls give 143. Main-thread join, latch wait and cleanup-on-main SIGTERM
inputs give 1 with IllegalMonitorStateException in Thread.join; the latter
does not reach its cleanup marker. All twelve owned children are reaped.
These establish a runtime limitation separately from the hook-removal deadlock;
no unsupported claim that Native signal shutdown is fixed is made. The source,
commands, resolved classpaths, twenty frozen JAR/POM references (ten JARs),
actual process logs/completions and pinned runtime sources are retained in that
new public reproduction directory. Tracker search and an unfiled draft report
are being captured; no upstream issue is filed.

The framework candidate targets Targets.cross and adds Native Circe parsing.
Its JSON backend retains the owner's default and JS overlay policy: automatic
system-property/CONFIG_FORCE_ overlays remain unavailable and are documented
with a debug diagnostic. Native FS and embedded-stream loading are implemented
through ConfigSourceReader, preserving file/reference precedence, filtering,
optional missing references and configuration-domain errors for invalid explicit
inputs. Reference names use .json. The schema/HOCON-dependent ConfigWriter fails
explicitly as unavailable. The audited JVM planning options, graph-dump wiring,
ResourceRewriter, boot args/config wiring and synchronous launcher syntax are
ported; the late logger follows the JS implementation without the unavailable
JUL adapter. Native platform helpers exclude classpath introspection and the
JVM-only UUID helper. Copy provenance is
`1a-part2-distage-framework-native-source-provenance.json`.

The first prescribed --js --native generator exits 0, with command/log/hash
records under `1a-part2-distage-framework-native-generator-first.*`. Its new
build.sbt SHA256 is
`a6d772c2d7adc69897c57c2c83b0e701536440ba13a13f1c671d254aa0d2c442`;
plugins.sbt and build.properties retain their preceding hashes. Native Test
embedding is enabled so tests exercise actual bundled JSON. The new contract
suite runs identical behavior against a hand-written reader and real FS/resource
streams. Actual Identity/Cats IO/ZIO task tests check config injection, execution
counts and AutoCloseable acquisition/release. A shutdown case checks concurrent
manual requests and duplicate-await rejection.

`1a-part2-distage-framework-native-first-matrix.py` freezes the complete
eighteen-module Native production closure plus framework JVM/JS/test inputs,
then requests clean/testFull/classpaths/publication on all three platform/compiler
lanes and complete Native closure publication. It is running; tests, delivery
audits, separate published framework/PlanCheck consumers and read-only review
remain pending. New docs describe intended candidate behavior; they do not
establish passing postconditions. Parent/final statuses remain open.

The first framework producer exits 1 after production compilation and before
test execution. Root reads both diagnostics: a wildcard import makes getClass
ambiguous in the FS contract fixture, and the shutdown owner lambda returns
Int | Unit rather than Unit. Tests now use this.getClass and discard the atomic
count result. The first complete source snapshot, argv/log and actual completion
remain retained; no production correction is made. A separate second matrix
freezes the corrected candidate. Native docs now state the shared merger's exact
group ordering (active role inputs before shared/global inputs, explicit before
references within each group); the contract adds a role-reference versus global
explicit key to verify it. No JVM merge policy changes.

The separate after-second shutdown driver completes five controls each on
Scala 3 and 2.13, all with actual exit 0 and expected markers. Its 2.12 build
exits 1 before execution because the standalone fixture omitted the project's
-language:higherKinds flag. Root reads that feature diagnostic. A separate
after-third fixture adds that flag for the remaining 2.12 lane; the first two
passing lanes remain applicable because the production strategy is unchanged.
The after-second driver launch completion explicitly records its exit 1.

The second framework producer compiles both production and tests, then exits 1
at Native link. Root reads all four missing-symbol diagnostics: DatagramChannel
and ServerSocketChannel types/open methods are reached through eager IzSockets
initialization in DistagePlatformModule. This reproduces that helper's unsupported
Native NIO dependency. The Native platform binding/import is removed and the
capability is documented; shared/JVM sources are unchanged. The first two
candidate freezes and actual failures remain retained.

The read-only source reviewer identifies a separate ConfigWriter classification
hypothesis: unlike the JVM writer, the Native writer lacks BundledTask, so the
shared all-custom-tasks autoset may include the unavailable bundled writer.
Before any correction, a new public RoleAppMain case selects all-tasks from
BundledRolesModule alongside a custom task and requires that only the custom
task runs. The third matrix freezes that failing candidate to reproduce the
classification defect after correcting the unrelated linking precondition.

The after-third standalone shutdown fixture completes the remaining five
Scala 2.12 controls with actual exit 0 and expected markers. Thus all fifteen
non-signal controls pass across the three compilers against unchanged production
strategy bytes. These remain standalone checks; actual framework service
process controls are pending.

The Runtime signal public reproduction's independent audit exits 0, verifying
all twelve outcomes and frozen inputs, ten external JAR/POM pairs and eleven
compiled fixture entries. Primary Runtime/Thread/PosixThread/Proxy/ObjectMonitor
source bytes match their pinned source archives. Exact GitHub issue search for
SIGTERM plus IllegalMonitorStateException returns zero results; broader saved
queries return old process/multithreading PRs and unrelated reports, not a
matching titled report. Search requests/responses/hashes are retained.
`scala-native-runtime-signal-public-before/DRAFT-ISSUE.md` records the minimal
public source, exact controls, observed stacks and the signal/parking re-entry
hypothesis with its uncertainty. It is unfiled. The monitor-registry deadlock
and signal-handler failure are distinct: this public signal program never
removes a hook. No signal-runtime correction is made from this hypothesis.

The third producer links and executes all sixteen Scala 3 Native cases. Fifteen
pass; the new all-tasks case fails with exactly the predicted
UnsupportedOperationException from ConfigWriter.start through RunAllTasks.start.
Root reads the failure and complete stack, then freezes all six XML reports
under `1a-part2-distage-framework-native-third-report-freeze/manifest.json`.
The third source snapshot, command, actual log/completion and reports retain
the pre-correction failure. Only then does the Native writer gain the JVM's
BundledTask marker, which excludes it from the shared custom-task autoset.
A separate case requests configwriter directly and requires its explicit
unavailable-facility error. The fourth matrix captures the corrected candidate
without overwriting the reproduced failure.

The fourth producer's Scala 3 Native/JVM/JS lanes and complete Native closure
publication all exit 0: seventeen Native, twenty-three JVM and four JS cases
pass without failure, cancellation, ignored or pending cases. Its 2.13 lane
exits 1 at Test compilation: the test's BundledRolesModule factory sees both
the outer method TagK and RoleAppMain's inherited TagK as ambiguous implicits.
Root reads that exact diagnostic and makes the fixture constructor's TagK
argument explicit. No production source changes. A separate fifth matrix
freezes the corrected test and reruns the nine lanes; all fourth captures are
retained. Final delivery/public-consumer/service proof remains outstanding.

The initial independent source review reads the actual third classification
failure and the corrected writer, platform bindings, merge wording and fixtures.
It finds no additional concrete source defect and explicitly leaves service,
publication and full matrix postconditions unproved. Its updated full report
is `1a-part2-distage-framework-native-initial-readonly-review/UPDATED-SOURCE-REVIEW.md`,
SHA256 `ca8a684c9e7c21b0201a40fb7c90fa4bc67a69469e81aca2e2652a535d7b847b`;
updated-input-manifest.json SHA256 is
`f154a3f550daa4e8932fbbb99b48566293946f795a7006596de7af1856e5b603`.
Root reads the entire report and independently verifies all sixty repository
and twenty-one evidence records, six third-before XML records and six exact
current/fourth-frozen source comparisons before changing the 2.13 fixture.
The reviewed ledger hash is
`2db12bf632c322f26ab6bb4afcd02d8d2764c43ea610368f41ee5038ee70a545`.
This is an initial source review, not completion of any parent/final gate.

The fifth producer exits 1 in Scala 3 Test compilation before execution:
BundledRolesModule's constructor does not accept the extra parameter list used
by the preceding fixture correction. Root reads that exact diagnostic; the
fourth Scala 3 results remain historical observations, not fifth results.
The fixture now creates a constructor function in the outer method's unambiguous
TagK scope and invokes it inside the launcher plugin. A separate sixth matrix
freezes this candidate. No production source changes. The fifth commands,
snapshot, log and actual exit remain retained.

The sixth Scala 3 producer and complete Native closure publication both exit 0,
again passing seventeen Native, twenty-three JVM and four JS cases. Its 2.13
Test compile exits 1 at makeRole[NativeFrameworkTask[F]] with the same outer
versus inherited TagK ambiguity. Moving only BundledRolesModule construction
resolved that call but left the remaining plugin bindings in the ambiguous
scope. Root reads the diagnostic and moves the entire fixture plugin constructor
function into the outer method. A separate seventh matrix freezes and runs this
candidate. All sixth evidence remains retained; production sources are unchanged.

The seventh Scala 3 producer and closure publisher exit 0. Scala 2.13 now passes
the preceding implicit-resolution point but stops at the fixture's final assert:
its ScalaTest Assertion value is discarded by the Unit-returning try/finally,
which the pinned Scala 2 options reject. Root reads that fatal value-discard
warning and explicitly discards the assertion result. The eighth matrix freezes
that single-line test correction; no production changes, and all prior failure
evidence is preserved.

The eighth producer finishes all nine platform/compiler lanes and all three
complete Native closure publications with actual exit 0. Root's own artifact
and report audit exits 0: 132 successful cases (51 Native, 69 JVM, 12 JS),
eighteen fresh Compile/Test classpath blocks, nine framework JAR/POM pairs,
5,074 exact current binary entries including 2,040 NIR and 672 JS IR entries.
All eighteen shared StaticPluginLoader case executions agree across platforms;
Native adds its configuration, resource, launcher and shutdown cases. XML reports
and own published artifacts are frozen under
`1a-part2-distage-framework-native-artifact-audit/`. This tests the eighth
candidate, which still has the newly reproduced launcher shutdown defect.

A separate Scala 3 published preflight verifies all eighteen Native JARs against
current class/TASTy/NIR entries, then compiles a separate fixture dependency and
consumer under strict flags. PlanCheckMaterializer checks a RoleAppMain object
and explicit embedded JSON at compilation; the linked consumer also expands
ScalaReleaseMaterializer. The preflight build exits 0. Identity/IO/ZIO task and
manual-service processes each exit 0 with execution, service and application
resource finalization, and bootstrap finalization markers exactly once.
Its Runtime.exit(0) Identity process exits 0 but omits bootstrap finalization,
so the preflight driver exits 1 at that required postcondition. Build, actual
commands, dependency and compiled-fixture freezes, process outputs/completions
and driver-completion.json remain under
`1a-part2-distage-framework-native-published-preflight-scala3/`.

Before correction, a separate all-controls driver runs the same frozen Native
binary for all nine task/manual/runtime combinations. It exits 0 as a failing
reproduction: all three runtime effects omit bootstrap finalization while every
task/manual combination includes it. All nine children exit 0, have no timeout,
and are reaped; the reproduced defect is lost cleanup, not the process exit code.
Its executable SHA256 is
`d09b01fef1655b52efad3dbb7ba030254954e5aebb5f8e60f8664f4c3f314499`.
The matching published JVM fixture compiles unchanged and runs six real
manual/runtime Identity/IO/ZIO controls, all exit 0 with all five markers once.
Evidence is under `1a-part2-distage-framework-native-published-preflight-scala3-all-controls/`
and `1a-part2-distage-framework-shutdown-jvm-public-before/`. No JVM defect is
established by these controls. The source-backed Native hypothesis is that
finishShutdown releases the runtime hook before outer graphs finalize, then
blocks removing it on Native's held registry monitor; the process can terminate
before those outer finalizers. An independent read-only pre-implementation
review is checking deferral until the launcher's entire bootstrap scope releases.


The complete eighth Native publication auditor's first run exits 1 because its
forbidden-dependency predicate incorrectly treats optional zio-managed as required.
The first script, partial artifact copies and diagnostic are retained; this is an
auditor precondition failure, not a product dependency defect. A separately named
second auditor excludes optional/provided dependencies for that requirement. It
exits 0 and compares all 54 Native JAR/POM pairs: 30,980 binary entries, 19,953 NIR,
zero byte mismatches. The separately named second reconciliation compares the
already matching eighteen-module closure on each compiler without another SBT
publication; all three comparisons exit 0. Evidence prefixes are
`1a-part2-distage-framework-native-complete-publication-before-second-audit/`
and `1a-part2-distage-framework-native-*-publication-reconcile.*`.

The independent pre-implementation review confirms the observed Native launcher
boundary defect and leaves ownership/override/failure controls pending. Root reads
its full report and verifies twelve repository and 360 evidence hashes. The report
is `1a-part2-distage-framework-shutdown-boundary-preimplementation-readonly-review/PREIMPLEMENTATION-REVIEW.md`,
SHA256 `ea65235b3dea4be2ebee7e54c000b592d4fec9d8f552b3460a91fb4755a04096`.
No correction or passing postcondition follows merely from that review.

A new public logstage reproduction proves that outer launcher completion deferral
alone cannot preserve cleanup. It starts the actual published queue, places its
hook at MIN_PRIORITY, and uses a normal-priority independent hook to request main
thread queue.close and wait for completion. JVM manual/runtime and Native manual
complete with actual exit 0. Native runtime prints HIGH_PRIORITY_HOOK_STARTED and
QUEUE_CLOSE_STARTED, stalls through the five-second observation, then is killed
and reaped with -9 by the fixture. This is a failed close postcondition; -9 is
fixture cleanup. Build exits 0. Exact source, classpaths, frozen dependencies,
commands, executable hashes and all four process outcomes are retained under
`1a-part2-logstage-native-close-during-runtime-before/`.

The second read-only review finds explicit manual/resource ownership with
unavailable automatic Native exit-time drain permitted by acceptance 1a.11; 1a.7
is not its justification. The supported managed release must still preserve
runner events/finalizers under 2e.7. Root reads the report and verifies ten
repository and 110 evidence hashes. Report
`1a-part2-logstage-native-managed-queue-policy-readonly-review/POLICY-REVIEW.md`
SHA256 is `1654731e587e67072b7ead9172d2a79a95805c28219d3ca3b66e96340b6d0fbc`;
manifest SHA256 is
`021fd7438aec4c19d585e1c74716f90a817e6115e1f272681c3c27b7e21fcbee`.
The candidate Native queue now installs no automatic Runtime hook; resource/close
still owns synchronous worker join and draining. Its stable Thread-typed lazy
shutdownHook getter fails explicitly only on access. Public docs state the
capability difference. Prior unclosed-queue automatic-drain results are historical
observations of the earlier source, not current capability promises.

Before deferral, a separate published bootstrap-finalizer control calls the actual
injected initiator.releaseAwaitLatch during RouterFactory resource release. All
three Identity/IO/ZIO controls exit 0, including both request boundary markers and
bootstrap cleanup once. Evidence is
`1a-part2-distage-framework-native-boot-request-before/`. The initial candidate
launcher wrapper observes the provisioned strategy without adding a DI root and
defers Native completion until the entire outer produce/use scope releases. JVM
and JS keep their existing synchronous/asynchronous return behavior. The wrapper
is inside the existing failure-handler try so completion precedes an early handler
that may exit. A new focused producer freezes this candidate under
`1a-part2-distage-framework-native-shutdown-boundary-first-snapshot/`; compilation,
queue checks, actual launcher runtime cleanup and bootstrap-finalizer self-wait
controls remain pending. No parent/final acceptance item is marked complete.


The first focused shutdown-boundary producer and eighteen-module Native Scala 3
closure publisher both exit 0. Root freezes all successful XML cases: 107 logstage
and seventeen framework cases. Its separately named complete-publication audit
exits 0 for eighteen JAR/POM pairs, 10,614 binaries and 6,568 NIR, zero byte
mismatches. Evidence is under `shutdown-boundary-first` producer/snapshot/report
prefixes and `complete-publication-shutdown-boundary-first-audit/`.

The new managed queue policy control builds unchanged JVM and corrected Native
published logstage dependencies, then executes manual/runtime close on each.
All four child processes exit 0 without timeout and are reaped. Both Native cases
observe explicit UnsupportedOperationException from shutdownHook and complete
owned close. Native runtime now prints both QUEUE_CLOSED and
HIGH_PRIORITY_HOOK_COMPLETED. This checks the supported policy, not implicit exit
drain. Evidence: `1a-part2-logstage-native-managed-close-during-runtime-after/`.

The separately named published bootstrap-finalizer control then reproduces the
new completion-deferral self-wait on all three effects: Native children reach
BOOT_REQUEST_STARTED after service/application release, omit BOOT_REQUEST_COMPLETED
and BOOT_RELEASED, stall through five seconds, then are killed and reaped (-9).
Root reads each actual completion and the marker sequence before correcting it.
Evidence: `1a-part2-distage-framework-native-boot-request-deferred-before/`.
Only then is deferral changed to record its launcher thread atomically before
running the application; that thread signals a request without waiting for its
own outer cleanup, while other requesting threads still wait for full completion.
The private Native completion protocol preserves standalone finishShutdown and
custom strategy dispatch. Fresh matrix/public controls remain pending.


The second shutdown-boundary Scala 3 producer and complete closure publisher exit
0: 107 Native logstage, twenty Native framework, twenty-three JVM framework and
four JS framework cases, all successful. New Native cases check that launcher
requests return while external requesters wait for the outer scope, duplicate
observation retains first completion without mutating the second strategy, and a
self-contained AppResource runs/finalizes without provisioning an otherwise unused
shutdown strategy. The separately compiled published Scala 3 controls are running
against this source's exact eighteen-module closure; their results remain pending.

The second producer stops at Scala 2.13 production compilation with a fatal
dead-code warning in the synthetic initialization following a directly throwing
lazy shutdownHook val. Root reads that exact diagnostic. The getter now invokes a
Thread-returning private method which throws the same explicit unavailable error,
preserving lazy stable getter semantics without a bottom-typed lazy initializer.
The new Native scope callback also explicitly discards its final ScalaTest
Assertion, consistent with the pinned Scala 2 Unit-returning callback convention
already reproduced earlier. A new third matrix freezes this candidate; second
captures are retained, and no gate is inferred complete from Scala 3 alone.


The second candidate's separate strict published Scala 3 fixture/consumer build
exits 0 and all 24 owned children (eight modes times Identity/IO/ZIO) exit 0
without timeout and are reaped. Root reads the actual marker sequences for
runtime, bootstrap request, held bootstrap cleanup, custom strategy and both
finalizer-failure modes. Runtime includes bootstrap release exactly once;
bootstrap self-requests return and then release; held cleanup witnesses that an
external request has not returned before release. Custom strategy await/finish
are each once. Application and bootstrap finalizer failures remain observable,
with all owned application/service/bootstrap release counts once and requesters
joined. Compile-time PlanCheck/explicit JSON and runtime ScalaRelease checks also
pass. These are source-backed second-candidate controls, retained under
`1a-part2-distage-framework-native-published-shutdown-controls-scala3/`.

An attempted optional freeze of the second candidate's intermediate XML reports
exits 1 because the third producer's clean had already removed the Native logstage
reports. Its directory/completion records zero claimed frozen cases. The second
actual producer logs/completions still retain the 154 successful cases; no XML
freeze result is invented. Final third-candidate XML/artifact audits and all three
compiler public-consumer controls remain pending.


The third producer's Scala 3 and 2.13 lanes/closure publishers exit 0, each
passing 154 cases. Its Scala 2.12 logstage lane passes all 107 cases, then
framework production compilation rejects the covariant MainEffect alias in the
contravariant higher-order run parameter. Root reads that exact diagnostic.
Platform wrappers now name their concrete input result (Unit on Native/JVM,
Future[Unit] on JS), retaining the platform MainEffect return and runtime behavior.
This is a version-compatible signature correction. A fourth matrix captures it.
The reviewer also identifies an overbroad public paragraph: full outer-scope waits
belong to the default Native strategy, while custom strategy policy is untouched.
The paragraph is qualified accordingly before the fourth source freeze.

The third-source published consumer completes 24 controls each on Scala 3 and
2.13, all actual 0/reaped/no timeout. It then waits for the failed 2.12 producer's
publication barrier. Root terminates only that owned idle driver with SIGTERM,
collects tool-observed termination exit 143 (signal 15), and records 48 completed target children with no live
target child. This is not a successful full driver. Captured lane evidence remains
under `published-shutdown-controls-final/`; fresh fourth controls remain pending.

The extended JVM public fixture's first build exits 0, but its new task mode exits
1 with ConfigException.Missing settings: the Native JSON reference filename is
not the JVM conf-reference default. Application and bootstrap release markers are
once. Root reads that diagnostic and creates a separately named second fixture
with the JVM reference .conf name containing the same JSON. No production correction
is made for this missing fixture input. Both captured directories are retained.


The final queue policy control rebuilds against the helper-based Native getter.
Build and all four JVM/Native manual/runtime processes exit 0, with unavailable
Native hook access and completed owned close/drain boundaries; no timeout, all
children reaped. Evidence is `managed-close-during-runtime-final/`.

The second extended JVM fixture builds and passes nine task/manual/runtime
children with all markers once, then its bootstrap repeat-request case fails
with Promise already completed. This exercises the generic JVM async strategy's
existing non-idempotent primaryLatch.success policy. A new baseline driver runs
the same compiled public fixture/probe against the frozen pre-correction JVM
framework and dependency JARs: all three effects reproduce that exact failure
(actual child 1, no timeout, reaped; driver 0 as a failing reproduction).
Evidence is `1a-part2-distage-framework-jvm-repeated-request-baseline/`.
Thus no launcher regression is established by this new Native-specific input.
No unrelated JVM strategy correction is made. The unchanged-policy JVM public
control continues with task/manual/runtime/custom/application-failure/bootstrap-
failure modes; Native additionally verifies its idempotent bootstrap request and
full outer-scope waiting policy. Final fourth producer/public-consumer/artifact
proof and read-only completion review remain pending.


The corrected unchanged-policy JVM public fixture's third build and all eighteen
children exit 0, with task/manual/runtime/custom/application-finalizer-failure/
bootstrap-finalizer-failure modes each on Identity/IO/ZIO. Every required release
marker is once, no timeout, all reaped. Evidence is
`1a-part2-distage-framework-shutdown-jvm-public-after-third/`.

Root reads the entire independent postimplementation report and verifies every
one of its 1,063 repository and 1,922 evidence hash records, with zero mismatches.
Report `1a-part2-distage-framework-shutdown-boundary-postimplementation-readonly-review/POSTIMPLEMENTATION-REVIEW.md`
SHA256 is `0c7bc6584a146f09b3083a4e70f2184724863d8294e749be3e917d6707fdb326`;
final manifest SHA256 is
`f8909f01523377ba99d71342010c404a6b23e3af2a459d241ae315541618c280`.
Root hash audit is retained as
`1a-part2-distage-framework-shutdown-postimplementation-root-hash-audit.*`.
The reviewer finds no further established production defect, confirms the
corrected default-strategy documentation scope, and explicitly leaves fourth
matrix/current-publication/full-consumer/generator closure pending. Its measured
held-bootstrap control concerns explicit requests, not Runtime.exit while held;
process fixtures count application/service/bootstrap resources, not every runtime
executor thread. No stronger coverage or final acceptance closure is claimed.


The fourth producer completes every lane and all three full Native closure
publishers with actual exit 0. Per compiler: 107 Native logstage, twenty Native
framework, twenty-three JVM framework and four JS framework cases, with zero
failure/cancellation/ignored/pending cases. Total: 462 successful cases. Root's
fourth framework artifact/report audit exits 0 for nine JAR/POM pairs, 5,110 exact
binaries (2,070 NIR and 672 JS IR), eighteen fresh Compile/Test classpaths and
141 successful framework cases; the shared StaticPluginLoader identities agree
across platforms. The independent Native queue audit exits 0 with three JAR/POM
pairs, six fresh classpaths and 321 XML cases. All own JARs match current compiled
entry sets and bytes and exclude test artifacts. Its complete eighteen-module
Native publication audit exits 0 for 54 pairs: 31,013 binaries, 19,980 NIR and no
byte mismatches. Reports, artifacts and summary metadata are frozen in the three
separately named `shutdown-boundary-fourth-artifact-audit`,
`complete-publication-shutdown-boundary-fourth-audit`, and
`logstage-native-shutdown-fourth-artifact-audit` directories.

The fourth separate published fixture/consumer builds all exit 0; compile-time
PlanCheck with embedded explicit JSON and runtime ScalaRelease checks pass on
Scala 3/2.13/2.12. All 72 Native children (eight task/service/shutdown/failure modes
per compiler, each Identity/IO/ZIO) exit 0, no timeout, all reaped. All required
application/service/bootstrap release markers are exactly once. These consume
current JARs whose entire eighteen-module closure is compared byte-for-byte before
each build; dependency JAR/POMs and compiled fixtures are frozen. Evidence is
`1a-part2-distage-framework-native-published-shutdown-controls-fourth/`.
The final prescribed generator and final read-only bounded closure review remain
pending. No parent/final acceptance item is marked complete by this checkpoint.


The final prescribed --js --native generator exits 0. All before/after hashes are
identical for build.sbt, project/plugins.sbt and project/build.properties, retaining
the first generator's candidate hashes. Captured argv/log/hash completion is
`1a-part2-distage-framework-native-generator-final.*`. Root's separate final
public-capture audit verifies all 72 Native outcomes, required marker counts and
all recorded fixture/dependency/compiled-fixture hashes; its metadata is
`1a-part2-distage-framework-native-fourth-public-capture-audit/summary.json`.
Final bounded read-only review and local verified commit remain pending.

### Verified framework and managed queue source checkpoint — 2026-10-03

The final bounded read-only report finds no concrete unresolved defect or
overstated checkpoint claim. Root reads the entire report and independently
verifies all 1,063 repository and 2,481 evidence hash records: 3,544 checked,
zero mismatches, actual audit exit 0. The reviewed ledger hash is
`f1824dfb59d0f20cc4ab79576cdafd00296314d6fea15f68fa27150117cf424d`;
this provenance addition follows the review and is not text it reviewed.
Report `1a-part2-distage-framework-shutdown-boundary-final-readonly-review/FINAL-REVIEW.md`
SHA256: `297c864461447d37595cac122bef748caecdddf084e05ab21340738d173ac133`.
Its `final-reviewed-input-manifest.json` SHA256:
`8f8d2bca660aa3983e2d584ebf690d33e4f2f17691b0da64ae0fe95c5fa3d9b9`.
Root audit: `1a-part2-distage-framework-shutdown-final-root-hash-audit.py`
and the separately retained JSON result under `/srv/nvme/tmp/izumi-impl/`.

The local commit containing this entry is the verified source checkpoint for
the Native framework port, the outer default-strategy shutdown completion
boundary, the managed Native logging queue correction, generated build and
public capability documentation. Verification commands are captured exactly
in the named drivers and their commands.json files; all paths below are relative
to `/srv/nvme/tmp/izumi-impl/`.

| Command capture | Observed result |
| --- | --- |
| `python3 1a-part2-distage-framework-native-shutdown-boundary-fourth-matrix.py` | Twelve clean producer/test/publication lanes and three full eighteen-module Native publishers, all actual 0; 462 successful tests. |
| `python3 1a-part2-distage-framework-native-shutdown-boundary-fourth-artifact-audit.py` | Actual 0; nine framework JAR/POM pairs, 5,110 exact compiled binaries, eighteen fresh classpaths and 141 XML cases. |
| `python3 1a-part2-distage-framework-native-complete-publication-shutdown-boundary-fourth-audit.py` | Actual 0; 54 Native pairs, 31,013 exact compiled binaries, 19,980 NIR, zero differences. |
| `python3 1a-part2-logstage-native-shutdown-fourth-artifact-audit.py` | Actual 0; three Native queue pairs, six fresh classpaths, 321 XML cases. |
| `python3 1a-part2-distage-framework-native-published-shutdown-controls-fourth.py` | Three separately published consumer builds and 72 Native children actual 0, no timeout, all reaped. |
| `python3 1a-part2-distage-framework-native-fourth-public-capture-audit.py` | Actual 0; 72 outcomes, required markers and 720 input hashes verified. |
| `python3 1a-part2-distage-framework-shutdown-jvm-public-after-third.py` | Build and eighteen supplemental JVM children actual 0, no timeout, all reaped. |
| `python3 1a-part2-logstage-native-managed-close-during-runtime-final.py` | Build and four JVM/Native manual/runtime children actual 0, no timeout, all reaped. |
| `python3 1a-part2-distage-framework-native-generator-final.py` | Actual 0; prescribed --js --native generation idempotent for all three tracked outputs. |
| `python3 1a-part2-distage-framework-shutdown-final-root-hash-audit.py` | Actual 0; all 3,544 final reviewer input hashes agree. |

The reviewer additionally compares every binary member's literal bytes in all
54 consumed pairs with the frozen producer closure, including exact POM bytes:
31,013 binary members, zero differences. The bounded scope and limits above
remain: held-bootstrap coverage is explicit-request coverage; resource counters
do not enumerate every runtime executor; automatic unclosed Native queue drain
and graceful Native signal completion are not established. JVM repeated-request
baseline failures are retained rather than reported as passing controls.
No parent 1a/O.18/2b/2e item or final-head CI/publication evaluation becomes done
at this source checkpoint. The Native testkit core/runner port proceeds next.

The framework checkpoint commits locally as
`3d00b1e81009fddf4886fbbb12b2a844ddcde0f3` (2026-10-03).
Root verifies its working tree is clean and all 1,059 tested source/build/doc
inputs still match the fourth freeze after commit. Capture:
`/srv/nvme/tmp/izumi-impl/1a-part2-distage-framework-verified-commit.json`.
This is source applicability evidence; generated build-info metadata is not
claimed to have been rebuilt after that commit.

### 1a part 2 — Native testkit core port, in progress (2026-10-03)

Scope: 1a.1–3/6–8, bounded O.26/O.27 audit and supporting L3/L6 evidence.
The Native core uses the audited JVM bootstrap policy with Native JSON
ConfigSourceReader injection, the JVM asynchronous RunnerToF implementation,
and JS's explicit unavailable runtime plugin discovery policy. Its session
fixture retains the JVM-owned executor/await/termination boundary and excludes
only runtime scanning checks, which Native's plugin loader cannot perform.
Original-copy hashes and exact Native bootstrap substitutions are recorded in
`1a-part2-distage-testkit-core-native-source-provenance.json` under
`/srv/nvme/tmp/izumi-impl/`.

New Native engine fixtures reimplement 0b checks using current production
TestkitRunnerModule, SessionTestEnvironment, TestEnvironmentFactory and typed
configuration bindings. One case reads UTF-8 bundled JSON. Four tests in two
suites, with unlimited suite/test parallelism, share one memoized Lifecycle and
meet at a four-party timed barrier; this observes simultaneous body entry rather
than inferring concurrency from thread names. Resource acquisition/release are
counted per invocation; all test IDs, returned successful results, and scope
begin/end are checked. The parallel case also verifies a partial JSON override
with a reference fallback. No new ambient mutable fixture owner is introduced.
Its resource counts do not establish termination of the engine's default
Identity runtime executors; the separately owned session-fixture executor is
awaited and verified terminated.

`python3 1a-part2-distage-testkit-core-native-generator-initial.py` captures the
prescribed `direnv exec ROOT sh -c 'export JAVA_HOME="$JDK21"; exec bash sbtgen.sc --js --native'`
argv and exits 0. build.sbt changes from the prior framework candidate to
`8b8e379a2c4f27dee0acbb8d58a48ff1200f22f57e0692fd0a9c70dbadaa03d6`;
plugins.sbt and build.properties retain their preceding hashes. No generated
file is edited by hand.

The first matrix freezes all inputs before running. Its Scala 3 Native Test
compile exits 1 with E007 at NativeTestkitFixtures:116: the fixture passes
DefaultModule.apply's Module result where SessionTestEnvironment.load requires
the named DefaultModule[Identity] value. Root reads that precise diagnostic;
no runtime defect is established. The fixture is corrected to obtain the
implicit DefaultModule value. First driver, commands, logs, completion and
source freeze remain under `1a-part2-distage-testkit-core-native-*-first*`.
A separately named second matrix captures the corrected input.

The second producer completes the Scala 3 and 2.13 Native/JVM/JS clean/testFull/
publishLocal lanes, all actual 0. Each records Native 103 session contract checks
plus five successful engine bodies, JVM 128 and JS 103 session contract checks;
Native's four parallel bodies meet the barrier with acquired=1/released=1.
The owned Native session executor terminates. Scala 3 Compile/Test additionally
uses strict -Wunused:all. The Scala 3 full nineteen-module Native publisher exits
0. Scala 2.13 publication and Scala 2.12 lanes remain in progress here.

The separate published fixture/consumer first Scala 3 build and target child
exit 0; no timeout, child reaped. PlanCheckMaterializer expands against a
separately compiled fixture with explicit bundled JSON; its runtime check and
ScalaReleaseMaterializer check pass. The consumer uses published core coordinates
without production source shadowing and reruns the typed configuration/four-body
memoized resource controls. Its full nineteen-module consumed Native closure is
compared against current compiled sets and literal bytes before building.
Evidence is `1a-part2-distage-testkit-core-native-published-engine-first/`.
All-compiler producer/consumer completion, frozen artifact audits, final
generator/read-only review and the local core commit remain pending.

All nine second producer lanes and all three nineteen-module Native publishers
finish with actual exit 0. Across the three compilers there are 1,002 successful
session contract checks and fifteen Native engine bodies. The separate contract
identity audit verifies the 103-case Native/JS label multisets are identical and
are contained in each JVM 128-case multiset. All four concurrent-request markers
occur on every platform/compiler. Owned Native/JVM fixture executors terminate;
JS completion is captured. The core artifact auditor exits 0: nine JAR/POM pairs,
3,771 exact compiled binaries including 1,330 NIR and 574 JS IR, eighteen fresh
Compile/Test classpaths; no test fixtures or JSON test resource is published.
Evidence is `second-artifact-audit/` and `second-contract-identity-audit/` under
the `1a-part2-distage-testkit-core-native-` prefix.

All three first separate published engine builds and children exit 0, no timeout,
children reaped; PlanCheck and compiler checks pass. These retain their consumed
inputs as evidence of those executions. Final prescribed generation exits 0,
idempotent for all three tracked outputs. Capture: `generator-final.*` under the
same core prefix.

The complete closure diagnostic needs reconciliation before it can be called
current. Its first audit exits 1 because the copied assertion still expects 54
pairs, while this new nineteen-module graph produces 57. The separately named
second-final diagnostic enumerates all 57 pairs and exits 0, but explicitly
reports one differing Scala 3 fundamentals-bio NIR entry,
`zio/_izumicompat_/__ZIORaceCompat.nir`. Thus that diagnostic's process exit is
not a passing equality postcondition. All other class/TASTy/NIR entry sets and
bytes agree. Published member SHA256 is
`b4f8d804b8af4c4b0baf628f7be7be2cbfe0e5369527677753438428ad864174`;
current captured member SHA256 is
`72fe9ff5524382fb95d4fc1f85a52b874b087d142c21c1a7d2e8dbce354e1ef3`.
The first consumer's preflight checked exact then-current bytes, and its
fundamentals-bio JAR hash still matches the current published JAR. This establishes
a later discrepancy, without identifying its cause. The same kind of IR delivery
discrepancy was retained in the preceding extension checkpoint. No source or
dependency correction is inferred from it.

Both complete diagnostic directories and the differing compiled member are
preserved. The new `publication-reconcile.py` driver republishes only differing
modules and compares every current pair afterward. A fresh strict reconciled
closure audit and separately rebuilt reconciled consumers will establish the
final bounded publication postcondition. The read-only reviewer begins the
semantic/source audit while those captures run; final review/commit remain pending.

The reconciliation driver finishes with actual exit 0. Only Scala 3
fundamentals-bioNative is republished; the two Scala 2 comparisons need no SBT
publication process. Every compiler then compares all nineteen current pairs
and finds exact entry sets and bytes. The new strict reconciled closure auditor
exits 0 for 57 JAR/POM pairs, 32,965 binaries and 21,310 NIR with no differences.
Its frozen metadata is
`1a-part2-distage-testkit-core-native-complete-publication-reconciled-audit/summary.json`.

All three fresh reconciled fixture/consumer builds and Native children finish
with actual exit 0, no timeout, all reaped. Their PlanCheck/compiler checks and
all required configuration/four-body/memoized-resource markers pass. Root's
separate public capture auditor exits 0, verifies 628 fixture/dependency/compiled-
fixture input hashes, and compares every consumed JAR binary entry's literal
bytes and every POM against the frozen reconciled producer closure: all 57 pairs,
32,965 members, zero differences. First consumers and the two earlier diagnostic
audits remain historical captures, not substituted for the reconciled proof.
Root verifies all 1,114 tested source/build/doc input hashes still agree after
publication, consumer execution and generation. Final read-only review and local
verified core commit remain pending. Parent and final acceptance gates remain open.

### Verified Native testkit core source checkpoint — 2026-10-03

The final bounded read-only report finds no concrete residual semantic defect or
overstated checkpoint claim. Root reads the full report and independently verifies
all 1,118 repository and 1,957 evidence hash records: 3,075 checked, zero
mismatches, actual exit 0. Reviewed ledger SHA256:
`882b3d9c6a8322261d109e3f4253c544888afb51eaa5317b2e6f4e8bbde0b9f5`.
This provenance addition follows that review and is not text it reviewed.
Report `1a-part2-distage-testkit-core-native-final-readonly-review/FINAL-REVIEW.md`
SHA256: `546a0226bc910f53ea26d3e6b60086805a9ab69bfa864fc8a770c8b76d4cdf17`.
Its `final-reviewed-input-manifest.json` SHA256:
`90635c7a9fac009454c82d26b13f314288a7d654d5ffaa8163a7b0786c78c1c1`.
Root's separately retained audit is
`1a-part2-distage-testkit-core-native-final-root-hash-audit.py` and its JSON result.

The local commit containing this entry checkpoints the Native core adapters,
Native engine fixtures, generated target and public capability documentation.
All command paths below are relative to `/srv/nvme/tmp/izumi-impl/`; their drivers
and commands.json captures retain exact child argv and observed exit codes.

| Command capture | Observed result |
| --- | --- |
| `python3 1a-part2-distage-testkit-core-native-second-matrix.py` | Nine clean producer lanes and three nineteen-module Native closure publishers, all actual 0; 1,002 session contract checks and fifteen Native engine bodies. |
| `python3 1a-part2-distage-testkit-core-native-second-artifact-audit.py` | Actual 0; nine core JAR/POM pairs, 3,771 exact compiled binaries, 1,330 NIR, 574 JS IR and eighteen fresh classpaths. |
| `python3 1a-part2-distage-testkit-core-native-second-contract-identity-audit.py` | Actual 0; Native=JS 103-label multisets, each contained in JVM128, on all three compilers. |
| `python3 1a-part2-distage-testkit-core-native-publication-reconcile.py` | Actual 0; only Scala 3 BIO requires republication; all nineteen current pairs per compiler compare exactly afterward. |
| `python3 1a-part2-distage-testkit-core-native-complete-publication-reconciled-audit.py` | Actual 0; 57 frozen Native JAR/POM pairs, 32,965 exact compiled binaries and 21,310 NIR, no differences. |
| `python3 1a-part2-distage-testkit-core-native-published-engine-reconciled.py` | Three separate published consumer builds and Native children actual 0, no timeout, all reaped; configuration, parallel memoized resource, PlanCheck and compiler checks pass. |
| `python3 1a-part2-distage-testkit-core-native-reconciled-public-capture-audit.py` | Actual 0; 628 input hashes and literal consumed/producer member and POM equality for all 57 pairs, 32,965 binary members. |
| `python3 1a-part2-distage-testkit-core-native-generator-final.py` | Actual 0; prescribed --js --native generation idempotent for all three tracked outputs. |
| `python3 1a-part2-distage-testkit-core-native-final-root-hash-audit.py` | Actual 0; all 3,075 final reviewer input hashes agree. |

The reviewer independently checks contract label multiplicities and all 57
consumed pairs against frozen reconciled producer bytes. It does not substitute
mutable publication paths or process exit codes for equality checks. The first
fixture type error, inherited 54-pair audit failure and later one-member NIR
discrepancy remain preserved with their observed failures and unestablished
source cause. Engine checks use Identity; they do not establish every runtime
executor's termination or effect-specific cancellation. No parent 1a/O.18/
O.26/O.27 item or final-head evaluation becomes done at this checkpoint.
The higher Native testkit runner port proceeds next.

The core checkpoint commits locally as
`73222877677daa3f636684081492fe8a64d5753c` (2026-10-03).
Root verifies the clean working tree and all 1,114 tested source/build/doc hashes
after commit; capture `1a-part2-distage-testkit-core-native-verified-commit.json`.
Generated build-info metadata is not claimed rebuilt after this commit.

### 1a part 2 — Native higher testkit runner port, in progress (2026-10-03)

Scope: the existing JS module's Native target under 1a.1–3/6–7, bounded reused
adapter audit under O.26/O.27 and supporting 2e/L3/L6 evidence. The protocol and
base runner already have Native targets; this slice enables the higher
`distage-testkit-runner` target. Its Native SessionBootstrapFactory is byte-identical
to JVM and delegates shared bootstrap/config loading to the verified Native core.
The fixture copies the JVM-owned executor and concurrent registration start gate,
with JS's JSON activation configuration and runtime-scanning exclusion.
Original-copy hashes/substitutions are retained in
`1a-part2-distage-testkit-runner-native-source-provenance.json` under
`/srv/nvme/tmp/izumi-impl/`.

The first matrix will freeze the full twenty-three-module Native closure and
run all nine higher runner lanes, plus fresh Native protocol/base-runner tests
on each compiler. The Scala 3 protocol stays on its pinned 3.8.4 compiler; the
higher runner and other Scala 3 libraries stay on 3.9.0. Existing shared fixtures
exercise Identity, Cats Effect and ZIO front ends, interruption and lifecycle
reporting; their actual outcomes and contract identities remain to be checked.
No runtime executor termination or final target integration claim follows from
adding this target.

The required dependency metadata recheck obtains HTTP 200 for both Native Scala 3
interop-cats and interop-tracer metadata, with release/latest 23.1.0.14, matching
the project's pinned released version. XMLs, hashes, exact URLs and timestamp are
retained in `1a-part2-distage-testkit-runner-native-step-boundary-metadata/`.
The web open tool cannot access those XMLs; the direct HTTP captures establish
the observed metadata. No dependency version is changed.

The prescribed initial --js --native generator exits 0. build.sbt becomes
`7ae9abfe3e466881cdd4c4f33d46200a9ab004c53954a90168f6d7d66ec5207a`;
plugins.sbt and build.properties retain their prior hashes. Capture:
`1a-part2-distage-testkit-runner-native-generator-initial.*`.
The first Scala 3 producer and full twenty-three-module Native closure publisher
finish with actual exit 0. Fresh Native protocol/base controls pass 64/436 checks.
Higher Native/JVM/JS fixtures pass 350/432/350 contract checks, with 14 selected
front-end results, twelve successful bodies, three resource acquisitions/releases
and six repeated interruption cases on every platform. These are main-based
contract checks, not a ScalaTest XML case count. Scala 2 producer/publication,
artifact and identity audits, published consumers and final review remain pending.

All nine first higher producer lanes and all three twenty-three-module Native
publishers complete with actual exit 0. Own artifact audit exits 0: nine JAR/POM
pairs, 783 exact compiled binaries including 312 NIR and 102 JS IR, eighteen
fresh Compile/Test classpaths and no production ScalaTest dependency or test
fixture leakage. It records 3,396 higher contract checks, 192 fresh Native protocol
checks and 1,308 fresh Native base-runner checks. The separate identity audit
exits 0: Native=JS 350-label multisets on every compiler, each contained in JVM432.
Evidence is `first-artifact-audit/` and `first-contract-identity-audit/` under
the higher runner prefix.

The complete first Native closure auditor exits 1 for its byte-equality
postcondition. It freezes all 69 pairs, 35,851 binaries and 23,166 NIR, with three
different Scala 3 members: BIO's
`izumi/fundamentals/platform/files/FileLockMutex.nir` and logstage-core's
`logstage/UnsafeLogIO2.nir` / `logstage/UnsafeLogIO3.nir`. All entry sets and other
member bytes agree. Frozen published pairs, differing current members and exact
hashes are retained in
`1a-part2-distage-testkit-runner-native-complete-publication-first-audit/summary.json`.
This audit is a failing delivery comparison, not a successful closure. Its source
cause remains unestablished; no code or dependency correction is inferred.
The separate reconciliation driver republishes only those two Scala 3 modules
and compares every current pair afterward. Consumers have not run yet and will
require the new strict reconciled closure first.

Reconciliation finishes with actual exit 0. Scala 3 BIO and logstage-core are the
only republished modules; the two Scala 2 lanes run comparisons without an SBT
publication process. All twenty-three current pairs per compiler agree exactly.
The new strict reconciled closure audit exits 0: 69 frozen JAR/POM pairs,
35,851 binaries, 23,166 NIR, zero differences. Scala 3 protocol bytes are checked
against their 3.8.4 compiled output, with the other Scala 3 modules against 3.9.0.
Evidence: `1a-part2-distage-testkit-runner-native-complete-publication-reconciled-audit/`.
The separate public fixture/consumer begins only after that equality barrier.

The first Scala 3 public build and Native child exit 0, no timeout, child reaped.
Its separately compiled public fixture registers SpecIdentity, Spec1[IO],
Spec2[zio.IO] and SpecZIO through RunSession. Discovery leaves configuration,
resources and bodies suspended; resolution/planning evaluate four suite
configurations once. Execution injects bundled UTF-8 typed JSON into all eight
bodies and acquires/releases three intended memoized scopes (the two ZIO suites
share their compatible environment). It verifies exact selected terminal IDs,
start/completion event sets, contiguous ordinals and one Finished after all
releases, plus termination of its separately owned fixture executor. PlanCheck
expansion/runtime recheck and ScalaRelease pass. The consumer declares the
published higher runner and pinned released Cats Effect/ZIO coordinates; it
shadows no production sources and uses no producer classes. Its preflight checks
all twenty-three current consumed producer pairs exactly before building.
Capture: `1a-part2-distage-testkit-runner-native-published-spec-first/3.9.0/`.

Final prescribed generation exits 0, idempotent for all three tracked outputs;
`generator-final.*` retains exact argv/log/hash results under the higher runner
prefix. Root verifies all 1,174 tested source/build/doc hashes still agree.
Scala 2 consumers, public capture audit, final read-only review and local higher
runner commit remain pending. Parent and final acceptance gates remain open.

All three separate public builds and Native children finish with actual exit 0,
no timeout, all reaped. Each executes the eight public front-end bodies with
four configuration evaluations and three resource acquisitions/releases; each
checks its own executor termination, PlanCheck and compiler identity. The
independent public capture audit exits 0, verifies 788 source/dependency/compiled-
fixture hashes, and compares literal consumed binary and POM bytes against the
frozen reconciled producer closure: all 69 pairs, 35,851 members, zero differences.
Capture: `1a-part2-distage-testkit-runner-native-first-public-capture-audit/summary.json`.
Root verifies all 1,174 tested source/build/doc hashes after those executions
and generation; `git diff --check` passes. Final bounded read-only review and
local verified higher runner commit remain pending. The measurements do not
establish termination of every engine/runtime executor, active cancellation,
Native assertion-failure fidelity, full custom-loader ownership or the common
host/transport integrations. Parent and final gates remain open.

### Verified Native higher testkit runner source checkpoint — 2026-10-03

The final bounded read-only report finds no concrete residual defect or overstated
checkpoint claim. Root reads the entire report and independently verifies all
1,178 repository and 2,348 evidence hash records: 3,526 checked, zero mismatches,
actual exit 0. Reviewed ledger SHA256:
`c37a3da374ed6c1e0639fadc557d8e80f75af4d821bb305e0cd4cd923ba59fbb`.
This provenance addition follows that review and is not text it reviewed.
Report `1a-part2-distage-testkit-runner-native-final-readonly-review/FINAL-REVIEW.md`
SHA256: `55ff198cd475fc7a36d8e691f4f58e449b90982f7f3bc44be1b3602bde7e90d9`.
Its `final-reviewed-input-manifest.json` SHA256:
`71c35c10284ccaceb3f789e0068d0904e80834fcf71e2168d72acf914f611b19`.
Root audit: `1a-part2-distage-testkit-runner-native-final-root-hash-audit.py`
and the separately retained JSON result.

The local commit containing this entry checkpoints the Native higher runner
adapter/fixture, generated target and public capability documentation. All command
paths below are relative to `/srv/nvme/tmp/izumi-impl/`; named drivers and
commands.json captures preserve exact child argv and observed outcomes.

| Command capture | Observed result |
| --- | --- |
| `python3 1a-part2-distage-testkit-runner-native-first-matrix.py` | Nine clean higher producer lanes and three twenty-three-module Native publishers, all actual 0; 3,396 higher contract checks, 192 Native protocol checks and 1,308 Native base checks. |
| `python3 1a-part2-distage-testkit-runner-native-first-artifact-audit.py` | Actual 0; nine own JAR/POM pairs, 783 exact compiled binaries, 312 NIR, 102 JS IR and eighteen fresh classpaths. |
| `python3 1a-part2-distage-testkit-runner-native-first-contract-identity-audit.py` | Actual 0; Native=JS 350-label multisets, each contained in JVM432, on all three compilers. |
| `python3 1a-part2-distage-testkit-runner-native-publication-reconcile.py` | Actual 0; only Scala 3 BIO/logstage require republication; all twenty-three current pairs per compiler compare exactly afterward. |
| `python3 1a-part2-distage-testkit-runner-native-complete-publication-reconciled-audit.py` | Actual 0; 69 frozen Native pairs, 35,851 exact compiled binaries and 23,166 NIR, no differences. |
| `python3 1a-part2-distage-testkit-runner-native-published-spec-first.py` | Three separate published builds and Native children actual 0, no timeout, all reaped; all four public front ends, typed JSON, measured resource/event checks, PlanCheck and compiler checks pass. |
| `python3 1a-part2-distage-testkit-runner-native-first-public-capture-audit.py` | Actual 0; 788 input hashes and literal consumed/producer binary/POM equality for all 69 pairs, 35,851 members. |
| `python3 1a-part2-distage-testkit-runner-native-generator-final.py` | Actual 0; prescribed --js --native generation idempotent for all three tracked outputs. |
| `python3 1a-part2-distage-testkit-runner-native-final-root-hash-audit.py` | Actual 0; all 3,526 final reviewer input hashes agree. |

The reviewer independently reads every own/reconciled frozen JAR and POM,
extracts all contract label multiplicities, verifies all public input references,
and compares literal consumed member/POM bytes. The first failed complete audit
and three differing NIR members remain historical evidence with their source
cause unestablished. The separately owned fixture executors terminate; the
resource counts and process exits do not enumerate every engine/runtime executor.
This checkpoint closes no parent 1a/O.18/O.26/O.27/2e item or final-head evaluation.
Full Native CI-equivalent lanes proceed next from the committed source checkpoint.

The higher runner checkpoint commits locally as
`bc444f7e6aabc5aa0059c6f56a1e154c2568b88b` (2026-10-03).
Root verifies a clean working tree and all 1,174 tested source/build/doc hashes
after commit; capture `1a-part2-distage-testkit-runner-native-verified-commit.json`.
Generated build-info metadata is not claimed rebuilt after that commit.

### Full Native CI-equivalent lanes, bounded checkpoint — 2026-10-03

`python3 /srv/nvme/tmp/izumi-impl/1a-part2-native-ci-full-first.py` creates a new
detached worktree at the higher checkpoint and requests the exact Native-only
CI :gen/:test actions on JDK21 with Scala 3/2.13/2.12, sequentially. It uses the
loaded dev shell (`--without-nix`) and a fresh worktree-only SBT local cache
directory per compiler, so persistent test history cannot suppress unchanged
suites. The existing, explicitly recorded empty Docker CLI view is reused for
Native's legacy bulk cleanup; it hashes to
`9ab506e2e003dfbe02324ae5393875f13aa127a0dbc52c655677227743b087b5`
and other Docker operations fail. Native tests need no Docker; host containers
are not exposed to that cleanup. No repository production/test source is patched
for the lane. Before-generation tracked inputs and exact argv/cache/wrapper
provenance are frozen in `1a-part2-native-ci-full-first/`.

The Scala 3 command fails during aggregate Native linking. Two JVM thread dumps
each show 32 outstanding Native link task stacks; GC statistics record old
generation occupancy above 99.9%, hundreds of full collections, and prolonged
full-GC time. Eventually Native code generation worker threads report
`OutOfMemoryError: Java heap space`. The separately frozen
`scala-3/oom-before-termination.log` contains 28 such messages and hashes to
`bc0efe3043d06f15024515127a7646cb54853b8bddc6e1a37bf3bf46f7b71f25`.
Root verifies the task JVM's PID, executable arguments, worktree cwd and 6 GiB
heap setting, records them in `owned-jvm-termination-request.json`, and sends
SIGTERM only to that JVM. Its shell exits 143 and mudyla/the driver return actual
1; this is an explicitly stopped failed run, not a naturally completed failed
test suite. Scala 2 lanes do not start. Full executed-suite counts and engine
markers remain unverified. This is a bounded source-head L3/1a.9 check; final
evaluation after all implementation remains open.

The first candidate adds one global Native-link concurrency restriction,
with a named limit of two, when the generator receives `--native`. It uses the
pinned plugin's public `NativeTags.Link`, which the plugin applies to Native
link tasks. This controls simultaneously retained Native IR graphs within the
existing 6 GiB CI heap. It does not change test parallelism or heap capacity.
Pinned primary source and source-JAR captures live in
`native-build-policy-sources-first/`; the
[Native 0.5.12 plugin source](https://github.com/scala-native/scala-native/blob/v0.5.12/sbt-scala-native/src/main/scala/scala/scalanative/sbtplugin/ScalaNativePlugin.scala)
documents the restriction, and its implementation tags the cached link task.
This is a repository resource-policy correction; no upstream memory-leak
diagnosis is established. Generation across Native/non-Native flags and full
clean Native CI reruns follow below.

The first candidate's six Native/non-Native generation modes pass, followed by
prescribed root generation (`1a-part2-native-link-limit-generator-second.py`,
actual 0). The initially executed generator driver fails before generation
because it repeats the entry point's `-o` option; the second driver generates in
its separate output cwd instead. Both scripts and the failure log are retained.
Native modes contain exactly one restriction, non-Native modes none, and only
Native modes load the Native plugin. The candidate changes the generator input
and one generated root setting; plugin and properties outputs remain unchanged.

`1a-part2-native-ci-limited-first.py` requests fresh exact Native-only CI actions
in a second detached worktree at `bc444f7e6`, copying only the candidate generator
input. Scala 3 succeeds in 244.4 seconds, with 98 XML reports containing 795
cases, no XML failures/errors, and all six required protocol/base/core/higher
engine markers exactly once. The reports are frozen before the next compiler.
Scala 2.13 fails naturally with actual 1 during Native linking for
`distage-coreNative` and `distage-testkit-runnerNative`: both report
`java.nio.file.ClosedFileSystemException` while reading JAR members through
`VirtualDirectory`. No heap-exhaustion message appears in that lane. Scala 2.12
does not start. The limit of two is therefore not a verified complete correction.

Pinned tools/util/NIR source JARs and GitHub issue search/comments are captured in
`native-closed-filesystem-investigation-first/`. Native 0.5.12's
`ResourceEmbedder` opens its own `Scope`; `VirtualDirectory.jar` reuses the
JVM-registered ZIP filesystem on `FileSystemAlreadyExistsException`, while both
scopes acquire that filesystem for closure. This permits an embedding operation
to close a filesystem another link still reads. The plugin's shared outer scope
does not protect against the embedding operation's independent scope.
The failure class and serial-link mitigation are reported in
[scala-native#2024](https://github.com/scala-native/scala-native/issues/2024) and
[scala-native#4101](https://github.com/scala-native/scala-native/issues/4101).
Those issues' historical shared-scope fix does not establish that the embedding
path in the pinned version is corrected.

Before changing the limit, a deterministic public API reproduction opens one
JAR in two Native 0.5.12 scopes, reads successfully through each, closes the
inner scope, then fails reading through the outer scope with
`ClosedFileSystemException`. Command:
`python3 /srv/nvme/tmp/izumi-impl/native-closed-filesystem-scope-repro-first.py`,
actual 1, `reproduced=true`. Exact argv, fixture source, util binary, checksums,
log and completion are retained. It reproduces independent-scope ownership
failure without any izumi code; it does not independently reproduce aggregate
link task scheduling.

The next candidate uses a named limit of one Native link. It retains the pinned
dependency and removes simultaneous link operations that expose the scope
defect. The dependency's cross-scope ownership defect remains; the build policy
is an explicit mitigation, not an upstream correction. Generator-mode checks
pass in `1a-part2-native-link-limit-generator-serial-first.py` (actual 0): all
six JVM/JS/Native flag combinations generate successfully, only Native modes
emit one limit of one, and the non-Native outputs match the earlier controls.
The prescribed root `--js --native` generation also succeeds; its build hash is
`fd85dc13a5d0597a0ac46696321110e846e0749b8db6377551e6fbc0a58d9fdd`.
Plugin and properties outputs remain unchanged. Full Native CI verification
completes in the separately captured `1a-part2-native-ci-serial-first/` worktree.

Command: `python3 /srv/nvme/tmp/izumi-impl/1a-part2-native-ci-serial-first.py`,
actual 0. It requests the same exact Native-only JDK21 :gen/:test actions,
sequentially on Scala 3/2.13/2.12, in a fresh detached worktree at `bc444f7e6`,
copying only the serial generator input. Each compiler has a fresh SBT local
cache directory. The source-head manifest, copied input, exact argv and generated
hashes are captured before and after the commands; each lane freezes its own
reports before the next compiler starts.

| Scala axis | Actual exit | XML reports | XML cases | mudyla wall seconds |
| --- | --- | --- | --- | --- |
| 3 | 0 | 98 | 795 | 327.4 |
| 2.13 | 0 | 96 | 758 | 309.1 |
| 2.12 | 0 | 96 | 758 | 304.3 |

The 290 frozen reports contain 2,311 testcase elements, with zero XML failures,
errors or skips. Every lane also emits exactly once each required marker:
protocol 64, base runner 436, Native DI/configuration, four parallel memoized
Lifecycle bodies with one acquisition/release, core session environments 103,
and higher provider 350. Assertion, Cats and BIO portable mains run as part of
the same aggregate task. No heap-exhaustion or ClosedFileSystemException message
appears in these successful logs. Their Native-only generated build hash is
`89dbf652021dd583d70ce883d22500877d8cab483df60a72f2b585ff296bc585`, matching the
separate Native-only generation control.

Independent result command:
`python3 /srv/nvme/tmp/izumi-impl/1a-part2-native-ci-serial-result-audit-first.py`,
actual 0. It rehashes and parses every frozen XML report, checks testcase elements
against declared counts, checks raw command outcomes and marker counts, and
verifies all 1,588 unchanged/copied tracked inputs against both checkout and root.
The four exclusions are the lane-specific generated build/plugin/properties
outputs and the evolving status ledger; generated files are verified separately
against their respective completed generation captures. Current root generator
inputs and prescribed combined JS/Native outputs also match. Evidence and checks
are recorded in the new audit directory. This is a bounded source-head Native
CI correction and checkpoint; whole 1a/L3 and all final-head evaluations remain
open. Read-only review and the local checkpoint commit follow.

The read-only reviewer reports no concrete defect or overclaim in the completed
bounded checkpoint. Final report:
`1a-part2-native-link-limit-final-readonly-review/FINAL-REVIEW.md`, SHA-256
`013ab4085e9d535f9e38ab11603f6ccf4fe2fbed4cded45521653246e532da79`.
Its schema-1 input manifest hashes to
`3c577461c57f1d3baea2878aeed3335375552c706a7fb9a43f9e3a262dd1ed78`.
Root independently rehashes all 1,592 repository and 1,943 evidence inputs:
3,535 records, zero mismatches. The reviewed ledger hashes to
`afe57eb93d80b37c2d3ac40ec2e2367c34763c8b237c81e6529f6cb0f179b629` before this
provenance addition. The earlier preparatory review is retained separately;
root verified its 227 input hashes before the later ledger update. Review limits
retain the captured source head, JDK21/Native-only lanes, per-lane local disk
cache policy, isolated empty Docker view, upstream ownership defect and open
parent/final evaluations. No reviewer reruns a build or mutates existing evidence.

Local checkpoint command:
`python3 /srv/nvme/tmp/izumi-impl/1a-part2-native-link-serial-verified-commit-first.py`.
Its completion capture records the actual commit/date and post-commit source
identity. Build-info metadata is not claimed rebuilt after that commit.

### Active higher-runner cancellation reproduction — 2026-10-03

Before any cancellation correction, a new separate published JVM Scala 3.9
consumer compiles and invokes only public `Spec1[IO]`/`RunSession` APIs. It waits
until an IO body has entered with one acquired memoized resource, calls
`RunSession.cancel()`, and observes a ten-second interval before explicit
fixture cleanup. The execution future does not complete and the resource
remains acquired. Cleanup releases the blocked body, after which the engine
returns its test as `Succeeded` with the run cancellation flag set. The fixture
executor terminates; the intended cancellation assertion then fails.

Command: `python3 /srv/nvme/tmp/izumi-impl/2b-active-cancellation-repro-first.py`,
actual exit 1. Exact argv, source hashes, log and completion live in the matching
directory. Marker: `ACTIVE_CANCELLATION_REPRO observedCompleted=false acquired=1
released=0`. Failure: `RunSession.cancel did not terminate the active IO body
within 10 seconds`. No production cancellation source has changed.

The capture audit initially mistakes SBT's generated consumer classpath JAR for
a dependency requiring a published POM and exits 1. Its separately named second
driver excludes this build's generated outputs and exits 0, freezing all 98
consumed dependency JAR/POM files. All 1,174 checkpoint source hashes agree at
that observation before the Native scheduling edit, and the consumed higher
runner is byte-identical to the checkpoint's frozen JVM artifact and all 55
current compiled binary members. Other dependencies are frozen as consumed;
their entire closure is not claimed byte-identical to current compiled outputs.
The reproduction and audit drivers are preserved unchanged. Active cancellation
remains open under O.18; no parent or final gate closes.
