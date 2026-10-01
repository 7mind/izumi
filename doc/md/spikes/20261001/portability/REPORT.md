# Spike 0b: actual testkit closure portability

2026-10-01, isolated worktree at `4feabed5857067d051e357bc1aa62bbb68c96892`.
The reviewed plan is the file at that revision. No production port was made.

**Gate 0b's Native acceptance passes in the second round** (see
[Second round](#second-round-native05-mode)). The real `TestPlanner` and
`DistageTestRunner` link and run on Scala Native 0.5.12 with Scala 3.9.0. They
execute a DI-backed test with configuration loading and a static, non-scanning
plugin configuration, and a parallel run with a memoized `Lifecycle` resource
acquired and released exactly once. The run depends on fixture-local stand-ins
for the unpublished ZIO interop Native artifacts and on fixture-local Native
platform files, listed below as production port items. The first round, kept
below as history, stopped before compiling the testkit. A
[third round](#third-round-current-compiler-and-scala-2) passes the same two
programs from clean builds on the repository's current Scala 3 compiler, 3.7.4,
and on Scala 2.13.18 and 2.12.21.

## Most consequential results

| Check | Observed outcome |
| --- | --- |
| Scala 3.9.0 producer, `-release:17 -source:3.3`, separate 3.3.0 consumer | Fails loading ordinary API classes: TASTy expected 28.3, found 28.9. Old source dialect and JVM bytecode targeting do not produce old TASTy. |
| Same producer, separate 3.9.0 consumer | Runs ordinary API, inline method, quoted macro; prints `ORDINARY_INLINE_AND_MACRO_CONSUMER_PASS`. `javap` reports JVM major version 61. |
| Actual repository language macros compiled on 3.3.8, packaged into local jars, separate 3.3.0 consumer | Runs source-position macro and prints `LOCAL_ARTIFACT_MACRO_CONSUMED (Consumer.scala:5)`. This proves only the three packaged fundamentals artifacts, not the testkit closure. |
| Unmodified repository testkit closure, 3.3.0/JDK17 | `fundamentals-functoid` cannot compile `Implicits.searchIgnoring` and `Expr.summonIgnoring`; BIO also fails because current generated flags only match exact supported compiler versions. |
| Repository testkit closure, 3.3.7 and 3.3.8/JDK17, compatible compiler flags | Both stop at the same four quoted-reflection API errors in `fundamentals-functoid`. No complete 3.3 testkit artifact can be consumed because none was built. |
| Repository closure, 3.9.0/JDK17, existing flags extended to 3.9 | Compiler crashes in `FunctoidDummyImplicit.scala`, phase `genBCode`, `ArrayIndexOutOfBoundsException: -1`. Later review runs reproduced it as an intermittent compiler race (3 of 8 cold closure builds at backend parallelism 16 with `-explain-cyclic`; 0 of 3 at parallelism 1; 0 of 3 without `-explain-cyclic`; 3.7.4 clean in 3 of 3), reported as [scala/scala3#27209](https://github.com/scala/scala3/issues/27209). |
| Same 3.9 run with backend parallelism reduced from existing 16 to 1 | Functoid compilation passes in this run; next blocker is generated PureConfig dependency gating by exact `3.7.4`, leaving `com.typesafe.config` and PureConfig absent on 3.9. The one control does not prove the compiler crash's root cause. |
| Native 0.5.12 with pinned dependency versions | Dependency resolution fails: Cats Effect 3.6.3 has Native 0.4 publications but no Native 0.5 publication; `zio-interop-cats` and `zio-interop-tracer` 23.1.0.5 have no Native publication checked. |
| Native 3.9 approximation with explicit diagnostic substitutions | 17 repository modules compile; framework stops deriving decoder for `Option[RenderingOptions]` in `LoggerConfigLoader.scala:41`. Testkit/app never compile; native linking never occurs. The decoder stop is a fixture artifact: the fixture omitted the repository's `-Xmax-inlines:64`. The build, with its 3.7.4 Scala 3 options extended to 3.9.0, compiles `distage-frameworkJS` on 3.9.0, and removing only that flag reproduces the identical error. |
| Second round, `native05` mode, Native 0.5.12, Scala 3.9.0 | All 19 closure modules and the application compile; `nativeLink` succeeds after the platform overrides below; the binary prints `TESTPLANNER_AND_DISTAGETESTRUNNER_PASS`, and in `parallel` mode `PARALLEL_MEMOIZED_PASS success=4, failure=0, ended=true, acquired=1, released=1, threads=2`. |
| Third round, `native05` mode, Scala 3.7.4, 2.13.18, 2.12.21 | From clean builds, all 19 modules and the application compile and link, and both programs pass. On Scala 2, Cats Effect 3.7.1 needs its `provided` annotation library on the compile classpath, on the JVM as well ([typelevel/cats-effect#4693](https://github.com/typelevel/cats-effect/issues/4693)). |
| Third round, repository build generated with a Native platform | sbtgen 0.0.122 generates 21 Native projects; the existing ScalaTest suites of `fundamentals-collections` (33 tests) and `fundamentals-language` (1 test) pass on Native on 3.7.4, 2.13.18, and 2.12.21. |

`3.3.8` was the latest stable 3.3 compiler found in Maven metadata during this
spike. Native 0.5.12's `nscplugin_3.9.0` POM exists (HTTP 200). Availability is
confirmed independently of whether this application links.

## Actual closure and platform boundaries

[closure.tsv](closure.tsv) enumerates the proposed Native projects and their
repository dependency edges. The 19 project paths are:

- fundamentals: basics, functional, collections, literals, orphans, language,
  platform, functoid, BIO;
- distage: core API, core, framework API, config extension, plugin extension,
  logstage extension, framework, testkit core;
- logstage: core and circe rendering.

The **existing JVM** closure additionally includes `distage-proxy-bytebuddy` and
Byte Buddy 1.17.7. The Native fixture deliberately uses the existing JS proxy
strategy/bootstrap, so it excludes that JVM-only artifact. The separate
`fundamentals-json-circe` artifact is not in the existing testkit dependency
closure; the chosen configuration backend depends directly on Circe instead.
ScalaTest and Docker are not needed by this fixture. Monix is already disabled
in the project's active `allMonads` dependency definition.

Pinned external dependencies relevant to the source closure are izumi-reflect
3.0.8, collection-compat 2.13.0, Cats 2.13.0, Cats Effect 3.6.3, ZIO and
ZIO Managed 2.1.24, ZIO interop cats/tracer 23.1.0.5, Circe 0.14.14; JVM
configuration uses PureConfig 0.17.10 (2.12 uses 0.17.8), and JVM platform/plugin
scanning references ClassGraph 4.8.181. Scala 2 compiler macros require the
matching scala-reflect compiler artifact and kind-projector 0.13.4.

The generated fixture is a **19-project approximation**, not a new target in the
unmodified production build. To reduce fixture boilerplate it gives every project
an over-approximation of the pinned external dependencies. A dependency-resolution
failure therefore concerns the total candidate closure, not necessarily the
particular low-level project named by SBT.

Platform source inventory includes:

| Boundary | Existing platform-specific files / Native implication |
| --- | --- |
| fundamentals platform | environment/classpath/execution context, SHA-256, files, JVM utilities. JS implementations directly import `scala.scalajs` and MacrotaskExecutor; JVM implementations reference JVM facilities which remain unverified at link time. |
| BIO | runner sync/async interface, CompletionStage adapter, blocking operations, MiniBIO/MiniBIOAsync scheduling, UUID secure randomness. JS variants directly use JS timers/typed arrays; JVM `ZIO.fromCompletionStage` is missing from pinned ZIO Native API. |
| core API/core | mirror provider, proxy bootstrap/strategy, effect platform support modules, graph dump observer. Static, non-proxy Native strategy is needed; choosing no plugin scanning does not remove these boundaries. |
| config | AppConfig representation, sources, readers, metadata, Scala 2/3 derivation. This fixture explicitly chooses existing JS Circe/JSON representation, not HOCON. |
| plugins | existing JS loader handles `PluginConfig.const` and rejects requested package scanning; no runtime classpath scanning in the fixture. |
| logstage | threading log queue and time rendering; Native diagnostic uses JVM files. Threads/time/cleanup behavior is unverified at runtime. |
| framework | platform/bootstrap modules, config loading/merging, role argument handling and startup/config writing. JS config loader plus explicit JSON `TestConfig.configOverrides` supplies configuration. Logger config derivation is in the actual planner path and currently stops compilation. |
| testkit | TestConfig platform defaults, BootstrapFactory, RunnerToF platform bridge. Actual runner also pulls UUID, TestTreeRunner, resource lifetime, activation/configuration and logging services. |

The final diagnostic fixture selects JVM fundamentals platform/BIO/logstage,
JS core/plugin/config/framework/testkit, substitutes only the JVM Cats IO
platform source when compiling against JVM Cats Effect, and retains one tiny
Native CompletionStage-to-ZIO adapter under `fixture/native-adapter/`. This
adapter was introduced only after reproducing the missing ZIO Native method.
Its callback/suspension behavior was not runtime-tested and is not a completed
production implementation.

`-Dspike.compileOnly=true` resolves the unavailable Native Cats Effect and ZIO
interop dependencies as **JVM Provided artifacts**. This is intentionally
unsuitable as a Native runtime classpath. It exposes source-level blockers beyond
publication failure; it does not establish that these dependencies can be linked.
The runtime interop tracer used by BIO needs a real Native implementation or a
properly separated portable core; shipping this diagnostic substitution is not
an option.

## Exact first blockers and bounded next work (first round)

Status after the second round: item 1 remains for the repository build; item 2
is moot because the owner dropped 3.3 consumers; item 3 is an intermittent
compiler race reported upstream, avoided with backend parallelism 1; for item 4,
Cats Effect 3.7.1 resolves the Cats side and the ZIO interop artifacts are pending
upstream; items 5 and 6 are resolved as described in the second round.

1. Fix generated version conditions before interpreting 3.3/3.9 compilation as
   a source compatibility check. The existing PureConfig dependency condition is
   `Seq("2.13.18", "3.7.4") contains scalaVersion.value`. Compiler flag conditions
   also use exact known versions.
2. Retaining 3.3 requires a producer compiled on a 3.3 compiler, including the
   reachable quoted macro API closure. `searchIgnoring`/`summonIgnoring` cannot
   be replaced mechanically with ordinary implicit search: these sites exclude
   dummy implicit symbols to avoid resolving their own placeholder arguments.
   A compatible algorithm or intentional API scope change needs semantic tests.
3. Investigate the reproducible 3.9 backend exception before claiming the
   version upgrade works. First compiler frame after collection operations is
   `dotty.tools.dotc.core.SymDenotations$BaseDataImpl.apply`, then `baseData`,
   `baseClasses`, `BTypeLoader.createClassInfo`. Backend-parallelism=1 got past it
   once; no permanent mitigation was installed.
4. For Native, port/build the required interop tracer and choose a supported
   Cats runtime publication strategy, or split optional runtime interop from
   portable fundamentals/BIO. The latter is a domain boundary, not simply a
   dependency deletion: QuasiIO/QuasiIORunner and other source files reference
   Cats/ZIO APIs even when the selected test effect is Identity.
5. Add explicit Native platform files only where evidence requires them. The
   first source diagnostic failures were JS timers/crypto/environment imports,
   followed by missing ZIO `fromCompletionStage` when reusing JVM BIO.
6. The final framework decoder failure might also affect other Circe/Scala 3.9
   consumers; this spike does not establish that Native alone causes it. Isolate
   that derivation, preserve configuration loading, then compile the actual
   testkit and run the retained DI/config fixture. Linking and runtime remain
   unknown, so no total port estimate follows from these compile attempts.

## Second round: native05 mode

Executed 2026-10-01 against the unchanged repository sources. Every
Native-specific file is fixture-local, and `generate-fixture.py` writes the build.
Run with `-Dspike.native05=true`. Captured outputs are the ignored local
`logs/native05-390-*.txt` and `logs/framework-js-390-*.txt` files.

Changes against the first round:

- Cats Effect 3.7.1 Native 0.5 artifacts. The owner approved the upgrade; 3.7.0 is
  the first line that publishes native0.5 artifacts.
- No `zio-interop-cats` or `zio-interop-tracer`: neither publishes a Native
  artifact up to 23.1.0.13. The owner is addressing the upstream publication;
  until then, `native-standins/` holds three placeholders:
  - `zio.internal.stacktracer.InteropTracer` mirrors the upstream JS implementation.
  - `ZIOCatsEffectInstancesModule` binds nothing. `DefaultModule` reaches it only
    through `zio.interop.CatsIOResourceSyntax` orphan evidence, which cannot
    resolve here, so it is unreachable.
  - `zio.interop.CatsIOResourceSyntax` is given to `fundamentals-orphans` in
    Provided scope only. Downstream modules therefore see no interop, exactly as in
    a build without that optional dependency.
- The repository's semantic Scala 3 flags, including `-Xmax-inlines:64`. The
  first round's decoder stop came from that flag's absence.

Observed sequence:

1. All 19 closure modules and the application compile on 3.9.0.
2. The first `nativeLink` reports 9 unreachable symbols, all from reused JVM
   platform files: `MessageDigest`, `SecureRandom`, `SecurityManager`,
   `System.getSecurityManager`, `URL`, `URLClassLoader`, and
   `RuntimeMXBean.getInputArguments`.
3. With the Native platform files below, 2 unreachable symbols remain. Scala
   Native's own `java.util.UUID.randomUUID` needs `java.security.SecureRandom`,
   which its core javalib lacks. The JVM `QuasiIORunner` calls it to name a thread.
4. With that call routed through `IzUUID`, `nativeLink` succeeds. The binary
   prints `DI_AND_CONFIGURATION_TEST_BODY_EXECUTED` and
   `TESTPLANNER_AND_DISTAGETESTRUNNER_PASS`. With the `parallel` argument, it runs
   four tests in two suites at unlimited parallelism with a memoized `Lifecycle`
   resource and prints
   `PARALLEL_MEMOIZED_PASS success=4, failure=0, ended=true, acquired=1, released=1, threads=2`.

Real ZIO interop artifacts: the owner's
[zio/interop-cats#763](https://github.com/zio/interop-cats/pull/763) (head
`de5d670`, open at the time) cross-builds that project for Native 0.5 on Cats
Effect 3.7.1. Its Native `InteropTracer` is the same no-op as the JS one. Its two
Native artifacts were published locally as `23.1.0.13-native-pr763`. With
`-Dspike.interopVersion=23.1.0.13-native-pr763`, the fixture uses them instead of
all three stand-ins, and the repository's real `ZIOCatsEffectInstancesModule`
compiles. From a clean build, both programs pass again: `parallel` prints
`acquired=1, released=1` with 4 successes. Logs are
`logs/native05-390-pr763-*.txt`.

Clean the fixture when switching modes. Incremental compilation keeps the `.nir`
files of removed sources, and the linker then uses them instead of a
dependency's class: the stand-in `InteropTracer.nir` survived the switch to the
real artifact. A generic reproduction is reported as
[scala-native/scala-native#5084](https://github.com/scala-native/scala-native/issues/5084).
The stand-in results above were re-verified from a clean build
(`logs/native05-390-standins-clean-*.txt`).

Production port items. Each becomes a `.native` source set in its module,
replacing the fixture-local file:

| Module | File | Native behavior exercised by the spike |
| --- | --- | --- |
| fundamentals-platform | `__AbstractIzPlatformPlatformSpecific` | JVM behavior without classpath and JMX introspection |
| fundamentals-bio | `__SecureRandomPlatformSpecific` | `getentropy`, adapted from Cats Effect 3.7.1 |
| fundamentals-bio | `IzUUIDPlatformSpecific` | The JS implementation, unchanged |
| fundamentals-bio | `UnsafeRun2` | The JVM file without the `SecurityManager` lookup (build-time patch) |
| fundamentals-bio | `QuasiIORunner` | The JVM file, naming threads with `IzUUID` instead of `java.util.UUID.randomUUID` (build-time patch) |
| fundamentals-bio | `__PlatformSpecific` | The first round's `CompletionStage` adapter; its callback semantics remain untested |
| distage-core | `CatsIOPlatformDependentSupportModule` | The JVM implementation, unchanged: Cats Effect 3.7.1 on Native has the same runtime constructors |
| fundamentals-platform, fundamentals-bio, logstage-core | remaining platform files | JVM sources, as reached by this program |
| core API, core, plugins, config, framework, testkit | platform files | JS sources: circe JSON configuration and `PluginConfig.const` |

The fundamentals-platform repository tree also holds three stale `.native` files
from older commits (`IzPlatform`, `IzSha256Hash`, `IzJvm`). They predate the
current shared `IzPlatform` structure and are not usable as they stand.

Not verified: ZIO or Cats Effect effect types under the testkit on Native (only
`Identity` ran), logstage file sinks and console color detection, HOCON
configuration, the `CompletionStage` adapter's callbacks, Scala 2.12/2.13 on
Native (run in the third round), macOS and Windows, release or LTO link modes.

The decoder control runs in a disposable worktree at the recorded baseline, with
this directory copied in. The two modes differ only in removing
`-Xmax-inlines:64`:

```sh
python3 doc/md/spikes/20261001/portability/configure-repository-compiler.py scala39-backend1
direnv exec /home/pavel/work/safe/7mind/izumi sbt -java-home "$JDK" -batch '++3.9.0!' 'distage-frameworkJS/compile'  # succeeds
python3 doc/md/spikes/20261001/portability/configure-repository-compiler.py scala39-backend1-no-max-inlines
direnv exec /home/pavel/work/safe/7mind/izumi sbt -java-home "$JDK" -batch '++3.9.0!' 'distage-frameworkJS/compile'  # LoggerConfigLoader.scala:41:89
python3 doc/md/spikes/20261001/portability/configure-repository-compiler.py original
```

```sh
python3 generate-fixture.py
cd fixture
# LLVM toolchain on PATH as in transport/REPORT.md; JDK 17 as above
direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'export PATH="$LLVM_PATH:$PATH"; exec sbt -java-home "$JDK" -batch -Dspike.native05=true "++3.9.0!" app/nativeLink'
./app/target/scala-3.9.0/app
./app/target/scala-3.9.0/app parallel

# Real interop artifacts instead of the stand-ins (zio/interop-cats PR 763, checked out at de5d670):
#   sbt 'set ThisBuild / version := "23.1.0.13-native-pr763"' zioInteropTracerNative/publishLocal zioInteropCatsNative/publishLocal
rm -rf modules/*/target app/target target  # stale .nir files survive mode switches
# then the nativeLink command above with -Dspike.interopVersion=23.1.0.13-native-pr763 added
```

## Third round: current compiler and Scala 2

Executed 2026-10-01 against the unchanged repository sources, from a copy of
this directory whose generated build points `repo` at the main checkout. Apart
from that path, the generated build is identical to the one this directory
produces. Captured outputs are the ignored local `logs/native05-374-*`,
`logs/native05-213-*`, `logs/native05-212-*`, and `logs/jvm-213-*` files.

| Scala | ZIO interop | Clean build | Programs |
| --- | --- | --- | --- |
| 3.7.4, the repository's current compiler | Local publish of zio/interop-cats#763 | All 19 modules and the application compile, with no stand-in project; `nativeLink` succeeds | Both pass; `parallel` prints `success=4, failure=0, ended=true, acquired=1, released=1, threads=2` |
| 2.13.18 | Stand-ins; the local publish has Scala 3 artifacts only | All 19 modules, the stand-in project, and the application compile; `nativeLink` succeeds | Both pass with the same counts |
| 2.12.21 | Stand-ins | As for 2.13.18 | Both pass with the same counts |

The first Scala 2 attempts stopped on fixture omissions, which the generator now
corrects; their outputs were not retained:

- The fixture passed only `-Xsource:3` and kind-projector on Scala 2. On 2.13 the
  `-Xsource:3` migration messages about case-class `copy` access became errors,
  because only the build's 2.13 options silence those categories. On 2.12, BIO's
  higher-kinded calls failed without `-Ypartial-unification`. `scala2Flags` now
  carries the build's semantic Scala 2 options.
- The fixture read neither `scala-2.12` nor `scala-2.13` source directories, and
  read `scala-2.13+` on every version. It now follows the binary version.
- `PortabilityMain` relied on Scala 3 inference for the type argument of
  `loadEnvironment`; it now passes `[Identity]`.

One failure does not come from the fixture. With Cats Effect 3.7.1,
`fundamentals-bio` fails on Scala 2 at `CatsConversions.scala:103` with
`Symbol 'type org.typelevel.scalaccompat.annotation.package.unused' is missing
from the classpath`, required by `Async.class`. Cats Effect 3.7.0 annotated a
parameter of `Async#syncStep` with that library's `@unused` and declares the
library `provided`. The JVM build fails identically when only `cats_effect`
changes to 3.7.1 (`logs/jvm-213-ce371-bio-compile.txt`). With 3.6.3 the library
is absent from that compile classpath (`logs/jvm-213-ce363-bio-classpath.txt`),
and the module compiles. The defect is reported upstream as
[typelevel/cats-effect#4693](https://github.com/typelevel/cats-effect/issues/4693).
The fixture adds the library's JVM artifact in `Provided` scope on Scala 2, which
is also how Cats Effect's own Native POM declares it; the library publishes no
Native artifact.

Not verified in this round: the PR #763 artifacts on Scala 2, which need a
Scala 2 local publish; anything beyond the two programs; and the other
second-round gaps above.

```sh
python3 generate-fixture.py
cd fixture
rm -rf modules/*/target app/target target
# LLVM toolchain and JDK 17 as in the second round
direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'export PATH="$LLVM_PATH:$PATH"; exec sbt -java-home "$JDK" -batch -Dspike.native05=true -Dspike.interopVersion=23.1.0.13-native-pr763 "++3.7.4!" app/nativeLink'
direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'export PATH="$LLVM_PATH:$PATH"; exec sbt -java-home "$JDK" -batch -Dspike.native05=true "++2.13.18!" app/nativeLink'
direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'export PATH="$LLVM_PATH:$PATH"; exec sbt -java-home "$JDK" -batch -Dspike.native05=true "++2.12.21!" app/nativeLink'
./app/target/scala-3.7.4/app && ./app/target/scala-3.7.4/app parallel  # likewise scala-2.13 and scala-2.12

# JVM control, in a disposable worktree: set `cats_effect = "3.7.1"` in project/Versions.scala, then
direnv exec /home/pavel/work/safe/7mind/izumi sbt -batch '++2.13.18' 'fundamentals-bioJVM/compile'  # CatsConversions.scala:103:6
```

### Generated repository build

[native-targets.patch](native-targets.patch) adds a Native `PlatformEnv` to
`Targets.cross` in `sbtgen/Deps.scala` and pins Scala Native 0.5.12; sbtgen
0.0.122 otherwise defaults to 0.5.10. Without the patch, `--native` generates no
Native project. With it, in a disposable worktree, `bash sbtgen.sc --js --native`
generates 21 Native projects. They cover every cross-built module, the legacy
`distage-testkit-scalatest` included, and use the SBT 2 plugins
`sbt-scala-native` 0.5.12 and `sbt-scala-native-crossproject` 1.4.0.

Native tests need one more setting. ScalaTest 3.3.0-alpha.2 depends on Scala
Native's `test-interface` 0.5.8 and ScalaCheck 1.18.1 on 0.5.5. That artifact's
POM declares the `strict` version scheme, so `update` rejects the 0.5.12 the
plugin requires. The target-side test interface has to match the plugin's host
adapter, so the patch accepts that eviction through `libraryDependencySchemes`.
The rule names `test-interface_native0.5`, like the build's existing `_sjs1`
circe entries; with the unsuffixed name, `update` still failed.

With the patch, the existing ScalaTest suites of the two modules without
platform-specific sources run as Native test binaries from the generated build.
On 3.7.4, 2.13.18, and 2.12.21, `fundamentals-collections` passes 33 tests in 6
suites and `fundamentals-language` passes 1 test
(`logs/realbuild-native-test-*.txt`). Modules with platform-specific sources do
not compile for Native until they have the `.native` source sets listed above.

```sh
# disposable worktree at the current commit, JAVA_HOME set for sbtgen
git apply doc/md/spikes/20261001/portability/native-targets.patch
bash sbtgen.sc --js --native
# LLVM toolchain and JDK 17 as in the second round
direnv exec /home/pavel/work/safe/7mind/izumi sh -c 'export PATH="$LLVM_PATH:$PATH"; exec sbt -java-home "$JDK" -batch fundamentals-collectionsNative/test fundamentals-languageNative/test "++2.13.18" fundamentals-collectionsNative/test fundamentals-languageNative/test "++2.12.21" fundamentals-collectionsNative/test fundamentals-languageNative/test'
```

## Reproductions and evidence

Captured compiler output is in the ignored local `logs/` directory; it is not
versioned or required by the fixtures. Run the repository compiler
configuration script only in an isolated worktree: it writes that worktree's
generated `build.sbt`. Compiler captures use `.txt`.
[generate-fixture.py](generate-fixture.py) generates
`fixture/build.sbt` from explicit module edges. Source fixtures and methodology
are versioned; generated build outputs can be recreated with these commands. Packaged
probe jars remain in the task-specific scratch `local-artifacts` directory.
[configure-repository-compiler.py](configure-repository-compiler.py) reproduces
spike-only flag changes and has `original`, `baseline33`, `scala39`, and
`scala39-backend1` modes. The repository build was restored to its original
contents after the experiments.

Run repository compiler experiments in a disposable git worktree at the recorded
baseline, with the retained fixture directory copied into it. The compiler
configuration script deliberately rewrites `build.sbt` from that baseline and
rejects the main checkout. Run tools through
`direnv exec /home/pavel/work/safe/7mind/izumi`. SBT is batch
mode. Literal 3.3.0 needs `-java-home` explicitly: setting JAVA_HOME alone still
launched JDK25 through this shell's launcher. JDK25 produced an unrelated
`ElementType.class` constant-pool error; the successful JDK17 startup removed
that environment confounder before source claims were made.

```sh
# From repository root, JDK17:
JDK=/nix/store/3vdm9yjwmr09idlj42lld65wmhq7q0w3-openjdk-headless-17.0.17+10
D=doc/md/spikes/20261001/portability
python3 "$D/configure-repository-compiler.py" baseline33
direnv exec /home/pavel/work/safe/7mind/izumi sbt -java-home "$JDK" -batch \
  '++3.3.8!' 'distage-testkit-coreJVM/compile'
# Run original 3.3.0 first with mode original for initial-failure evidence.
# Use mode scala39 or scala39-backend1 and ++3.9.0! for the 3.9 controls.
python3 "$D/configure-repository-compiler.py" original

# Native approximation, from fixture directory after generation:
python3 ../generate-fixture.py
# Unmodified candidate dependencies: sbt -batch app/compile
# Diagnostic progression uses explicit unavailable-dependency substitutions:
direnv exec /home/pavel/work/safe/7mind/izumi sh -c '
  export PATH=/nix/store/sqlnjj8c3n3si3sjnadhdbcwgrk97g2w-clang-wrapper-21.1.2/bin:/nix/store/b5bmnvk17mq8qm5b8bpi9fkyr5g2d2m4-llvm-21.1.2/bin:/nix/store/nla41igcnyykzirqfs1fi167ssxhc37p-lld-21.1.2/bin:$PATH
  sbt -batch -Dspike.compileOnly=true -Dspike.mixedPlatform=true \
    -Dspike.nativeAdapter=true -Dspike.catsJvmAdapter=true "++3.9.0!" app/nativeLink'
```

Compiler-output compatibility probe:

```sh
# From producer directory: compiles using -release:17 and -source:3.3.
direnv exec /home/pavel/work/safe/7mind/izumi sbt -java-home "$JDK" -batch package
# Copy its target/scala-3.9.0/portability-producer_3-0.1.0.jar into a task-local ARTIFACTS directory.
# From separate consumer directory; run again with consumerScala=3.9.0 for success control:
direnv exec /home/pavel/work/safe/7mind/izumi sbt -java-home "$JDK" -batch \
  -Dspike.compilerProbe=true -Dspike.consumerScala=3.3.0 \
  -Dspike.localArtifactDir="$ARTIFACTS" compile
```

To reproduce the narrower 3.3 language-artifact consumer, package the successful
basics/literals/language class outputs using
`package-language-artifacts.py 3.3.8 /absolute/task-local/output`, omit
`-Dspike.compilerProbe`, set `localArtifactDir` to that output, and run the
consumer on 3.3.0. No source from the producer is recompiled in that consumer.

Primary source references: [Native 0.5.12 release/toolchain table](https://scala-native.org/en/latest/changelog/0.5.x/0.5.12.html),
[Scala binary/TASTy compatibility](https://docs.scala-lang.org/scala3/reference/language-versions/binary-compatibility.html),
[Cats Effect 3.6.3 build](https://github.com/typelevel/cats-effect/blob/v3.6.3/build.sbt),
[ZIO interop 23.1.0.5 build](https://github.com/zio/interop-cats/blob/v23.1.0.5/build.sbt),
[izumi-reflect 3.0.8 Native targets](https://github.com/zio/izumi-reflect/blob/v3.0.8/build.sbt).
The execution output, not these version labels, establishes the results above.
