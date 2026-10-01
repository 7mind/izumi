# Implementation brief: native distage test library and runner

For the agent implementing [the plan](20261001-distage-native-testkit-plan.md).
The plan is the specification; this brief adds execution order, environment
facts, and working rules. The plan's owner decisions take precedence over this
brief. Resolve any other conflict in the plan text, with evidence, and note it in
the status ledger.

## Definition of done

Every step from 1a to 5 in the plan's
[delivery table](20261001-distage-native-testkit-plan.md#delivery-sequence-and-acceptance-gates)
passes its observable acceptance on the head of
`wip/distage-test-runner-and-scala-native`. Each gate item has evidence in the
status ledger from a command run against the current code. The local equivalents
of the CI lanes pass on JDK 17: every platform with Scala 2.12, 2.13, and 3,
using `:coverage` where CI does, plus `:site-test`. The JVM lanes also pass on
JDK 25. Steps 0a–0c are the executed spikes. A step is done when its gate checks
ran and passed, not when its code exists.

## Inputs

Read before changing code:

- The plan. Owner decisions are fixed; open decisions proceed with their stated
  defaults.
- The spike reports, with measured mechanisms and exact commands:
  [JVM SBT](spikes/20261001/sbt/REPORT.md),
  [portability](spikes/20261001/portability/REPORT.md),
  [JS/Native transport](spikes/20261001/transport/REPORT.md), and the
  [spikes README](spikes/20261001/README.md). Spike code is evidence and
  reference, not production code; reimplement it in production modules.
- `sbtgen/Deps.scala`, `project/Versions.scala`, `.mdl/defs/actions.md`,
  `.github/workflows/build.yml`, and `flake.nix`.

## Order

Follow the plan's order, but never wait on an external dependency while
independent work remains.

| Work | Its gate needs | External dependency |
| --- | --- | --- |
| 1a part 1: Native for the fundamentals modules that do not depend on `fundamentals-orphans` (basics, functional, collections, literals, language, platform, functoid, json-circe); Cats Effect 3.7 on every platform; clang in the dev shell; CI Native lanes | — | — |
| 1a part 2: Native for `fundamentals-orphans`, `fundamentals-bio`, and every logstage and distage module except the legacy ScalaTest adapter | 1a part 1 | A zio-interop-cats release with Native artifacts |
| 1b, 1c | 1a part 1, for their Native lanes | — |
| 1d | 1c; 1a part 2 for its Native BIO checks | As 1a part 2 |
| 2a | — | — |
| 2b | 1c | — |
| 2c | 2b | — |
| 2d | 2c | — |
| 2e | 2d, 1a part 2 | As 1a part 2 |
| 3 | 2d; 2e for its JS and Native combinations | — |
| 4 | 2d | — |
| 5 | 1d, 2e, 3 | — |

[zio/interop-cats#763](https://github.com/zio/interop-cats/pull/763) adds the
Native builds and was merged on 2026-10-01. No release had Native artifacts then;
the latest release was 23.1.0.13. At each step boundary, check
`https://repo1.maven.org/maven2/dev/zio/zio-interop-cats_native0.5_3/maven-metadata.xml`
and the matching `zio-interop-tracer` metadata. Until a release appears, part 2
may be verified against a `publishLocal` of that project's `main` branch, but
never commit a build that resolves a local or snapshot version. The pending
release counts as the goal's blocker only once all independent work in the table
is done. The fallback, if no release appears, is the owner's choice (the plan's
"Interop release" open decision).

## Working rules

- **Commits.** Work on `wip/distage-test-runner-and-scala-native`. Commit each
  verified sub-step locally, with a message that states what was verified. Do
  not push, open pull requests, force-push, rewrite history, or change
  `develop`.
- **Generated build.** Never hand-edit `build.sbt` or `project/plugins.sbt`.
  Change `sbtgen/Deps.scala`, `project/Versions.scala`, or
  `project/project/PluginVersions.scala`, regenerate with `bash sbtgen.sc` and
  the platform flags (`JAVA_HOME` set; the dev shell exports `$JDK17`, `$JDK21`,
  and `$JDK25`), and commit inputs and outputs together. The committed files are
  generated with `--js` today. Once Native lands, generate them with
  `--js --native`, as JS is included today, and record that choice under the
  plan's open decisions.
- **Commands.** Run tools in batch mode through the dev shell:
  `direnv exec /home/pavel/work/safe/7mind/izumi sbt -batch ...`. A CI lane
  runs locally as
  `direnv exec . mdl -u platform:<jvm|js|js-nojvm> -u java_version:<17|21|25> -u scala_version:<2.12|2.13|3> :gen :test`,
  with `:coverage` instead of `:test` where CI uses it and the Native platform
  values that step 1a adds.
- **Native toolchain.** Scala Native needs clang on `PATH`, which the dev shell
  lacks; step 1a adds it to `flake.nix`. Until then, use the clang, LLVM, and lld
  store paths listed in `spikes/20261001/transport/REPORT.md`. Clean Native
  outputs whenever a source moves or is deleted
  ([scala-native/scala-native#5084](https://github.com/scala-native/scala-native/issues/5084)).
- **Caches.** SBT 2 caches task results on disk. For cold-compile measurements,
  use a fresh `Global / localCacheDirectory`.
- **Scala 3.9.** Builds on 3.9.0 use `-Ybackend-parallelism 1` until a compiler
  release fixes
  [scala/scala3#27209](https://github.com/scala/scala3/issues/27209).
- **Scratch.** Use `/srv/nvme/tmp/izumi-impl/`. Run experiments that rewrite
  tracked or generated files in a disposable `git worktree`. Give each
  concurrently editing subagent its own worktree, and remove worktrees afterwards.
- **Defects.** Reproduce before fixing. For a third-party defect, search its
  tracker, then cite the existing issue, or record a minimal public reproduction
  and a draft report in the ledger. Do not file issues yourself.
- **Plan maintenance.** When evidence contradicts the plan, correct the plan text
  in the same commit and cite the evidence. Do not change owner decisions. If one
  proves infeasible, record the evidence under open decisions and continue with
  independent work. Record any new owner-level choice under open decisions, with
  the default you proceeded under.
- **Review.** Before marking a step done, have a read-only reviewer subagent
  audit its gate against the code and the ledger evidence, and resolve its
  findings.

## Status ledger

Create and maintain `doc/md/20261001-distage-native-testkit-status.md`. For each
step, record its state (not started, in progress, waiting on a named external
condition, or done). For each gate item, record the commands that verify it and
their observed results, such as counts and exit codes, with the commit and date.
Keep captured logs in an ignored directory and refer to them by path. Update the
ledger in the commit whose work it records, and read it first after a context
reset.

## Step notes

Facts from the spikes that the plan states only briefly or that are easy to miss.

### 1a

- Start from
  [native-targets.patch](spikes/20261001/portability/native-targets.patch). It
  adds a Native `PlatformEnv` to `Targets.cross` and pins Scala Native 0.5.12
  (sbtgen 0.0.122 defaults to 0.5.10). It also adds the `test-interface_native0.5`
  eviction rule: ScalaTest 3.3.0-alpha.2 and ScalaCheck were built against older
  `test-interface` releases, whose version scheme is `strict`. Until the interop
  release, restrict Native to the part-1 modules, for example through
  per-artifact `platforms`.
- `.native` source sets replace the portability fixture's platform files and
  build-time patches, which the portability report's port-item table lists.
  Audit any reused JVM or JS platform code. Replace the three stale `.native`
  files in `fundamentals-platform`.
- Native variants need counterparts of the eight `Scope.*.js` dependency
  declarations in `sbtgen/Deps.scala`: circe for config and framework, and
  scala-java-time. The JS-only macrotask executor and the legacy adapter's
  portable-scala-reflect need none.
- Shared sources reference the Java-only classgraph in compile-time macro code
  and in `PluginLoaderClassgraphImpl`. Give Native its own default plugin loader,
  as `.js` has, so that linking never reaches classgraph.
- On Scala 2, Cats Effect 3.7.0 and 3.7.1 need `scalac-compat-annotation` (the
  JVM artifact, provided scope) wherever a module subclasses `Async`
  ([typelevel/cats-effect#4693](https://github.com/typelevel/cats-effect/issues/4693));
  the JVM build fails without it too.
- Open-decision defaults: `distage-testkit-scalatest` stays on JVM and JS, and
  Native configuration uses the JS circe JSON implementation.
- `fundamentals-json-circe`'s Scala 2 derivation macros expand to
  circe-derivation, which has no Native artifact. On Native Scala 2, the
  facility either works without it or is documented as unavailable there.
- Add Native tests to `distage-testkit-core` that reproduce the 0b engine checks
  in the portability fixture's `PortabilityMain`:
  - a DI and configuration test;
  - four parallel tests sharing one memoized `Lifecycle` resource, acquired and
    released exactly once.
- CI:
  - mudyla's `platform` axis and `gen` action gain Native values: `--native`, and
    `--nojvm` for a Native-only lane;
  - `.github/workflows/build.yml` gains Native jobs for each Scala version;
  - the publish job generates with `--js --native`;
  - the `gen` action's `else` branch exits 0 for an unknown platform
    (`.mdl/defs/actions.md`), so make it fail, and check that each Native lane
    actually ran Native test projects;
  - CI caches no `target/` directories, so its builds are clean.

### 1b–1d

- Assertion artifacts depend only on fundamentals modules, never on
  `org.scalatest` or `org.scalactic` (gate 1c); the effect adapters sit above
  BIO and Cats Effect.
- New Scala 3 macros use only the public `scala.quoted` API.

### 2a

- Generator conditions that match the exact version 3.7.4 must cover 3.9.0. They
  include the PureConfig dependency and the compiler-option blocks, which carry
  `-Xmax-inlines:64`.
- The SBT 2 plugin and the portable protocol module stay on SBT 2's Scala 3.8.4.
- Consumer fixtures expand `PlanCheckMaterializer` and `ScalaReleaseMaterializer`
  on every Scala 3 lane, unless their compiler-internal references are removed.

### 2b–2e

- Runner layers and the portable protocol module: the plan's "Runner layers" and
  "Scala 3 publishing baseline" sections.
- 2b keeps the JS and Native lanes of the `fundamentals-*-test` suites running
  until 2e takes them over. There are two ways to do this: leave those suites on
  ScalaTest until the base runner supports their platforms, or select each
  suite's base class per platform. The owner decision makes the plain front end
  source-compatible with `AnyWordSpec`. Record the choice in the ledger.
- 2d: the JVM SBT report's measured seams.
  - Substitute in `loadedTestFrameworks`, with `Def.uncached` on SBT 2.
  - Use a target-side bootstrap for forks. First run the in-process matrix with
    only that bootstrap registered, as the plan requires.
  - Ordinary per-suite tasks project one application run per group.
  - Apply the conservative SBT 1 `testQuick` policy.
  - Add DI digests through `extraTestDigests` or `definedTestDigests` on SBT 2.
- 2e: the transport report's measured design, a host projection over the
  platform test adapters
  (`spikes/20261001/transport/fixture/project/TransportProjection.scala`). The
  items the plan lists as still uncovered for it are 2e work.

### 3–5

- Coverage starts with Scoverage, per the plan's coverage table.
- IntelliJ starts with the JVM. The adapter reads the explicit-channel
  protocol, never stdout.
- The plan's migration inventory lists every ScalaTest facility to replace.
  Reconcile each module's discovered suite and test counts before and after its
  migration.
