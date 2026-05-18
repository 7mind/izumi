# M2–M4 — Bifunctorized seams (closed 2026-05-14, M5 deferred)

Builds on M1 (`docs/changes/M1-bifunctorized-core.md`) by adding the
Identity special-case (M2), a parallel BIO surface for `Lifecycle`
(M3), and a parallel BIO surface for `Injector` (M4). M5 (wholesale
deletion of `Quasi*`) is explicitly deferred to a user-supervised
session.

## What ships

| Milestone | Commit | Files | Key contract |
|-----------|--------|-------|--------------|
| M2 | `e39f22592` | `Bifunctorized.scala` (+50 LoC), `BifunctorizedNoOpInstances.scala` (+20 LoC), `BifunctorizedIdentityBridgeTest.scala` (new) | `Bifunctorized.IdentityBifunctorized[+E, +A]` abstract type, MiniBIO-carrier (boxed — only non-zero-cost Bifunctorized subtype). `bifunctorizeIdentity`/`debifunctorizeIdentity` constructors. `Predefined.Of[IO2[IdentityBifunctorized]]` factory sourcing from `MiniBIO.IOForMiniBIO`. 8/8 PR-M2 tests pass on Scala 3.7.4, 2.13.18, 2.12.21. |
| M3 | `fcb370399` | `LifecycleBifunctorized.scala` (new, 105 LoC), `LifecycleBifunctorizedTest.scala` (new) | Parallel BIO surface to `Lifecycle` — 7 factories (`make`, `makePair`, `liftF`, `pure`, `suspend`, `fail`, `unit`) constrained on `F[+_, +_]: IO2[Bifunctorized.NoOp[F, +_, +_]]: TagKK`. Bridges via existing `QuasiIO.fromBIO` derivation at `QuasiIO.scala:201` — a single `asInstanceOf` cast (4 lines). `Lifecycle.scala` UNCHANGED. 7/7 PR-M3 tests + 572/572 `fundamentals-bioJVM` regression pass. |
| M4 | `b10409187` | `BifunctorizedInjector.scala` (new, 60 LoC), `BifunctorizedInjectorTest.scala` (new) | Parallel BIO surface to `Injector` — `apply` and `inherit` factories accepting `F[+_, +_]: IO2[Bifunctorized.NoOp[F, +_, +_]]: TagKK` plus `DefaultModule[F[Throwable, _]]`, producing `Injector[F[Throwable, _]]`. Same `QuasiIO.fromBIO` bridge as M3. `Injector.scala` UNCHANGED. 4/4 PR-M4 tests + 404/404 `distage-coreJVM` regression pass. |
| M5 | (deferred) | — | Wholesale `Quasi*` deletion across ~106 call-sites in 9 sub-projects. Deferred to a user-supervised session — autonomous mode lacks context for per-file API/test decisions. Infrastructure for M5 is fully in place from M1–M4. |
| M6 | this commit | `docs/manuals/bifunctorization-migration.md`, this changelog | User-facing migration guide + M2–M4 closure summary. Microsite SVG updates (graphical asset) skipped. |

## Verification at close-of-M4

- `fundamentals-bioJVM/test`: 572/572 pass on Scala 3.7.4, 2.13.18, 2.12.21.
- `distage-coreJVM/test`: 404/404 pass on Scala 3.7.4.
- `OptionalDependencyTest` (Goal 5 sanity): 8/8 pass on Scala 3.7.4 (8/9 on 2.13.18 with the Scala 2.13-only `Test213` block).
- All M2/M3/M4 PR-specific tests (8 + 7 + 4 = 19) pass on all three Scala versions.

## Design decisions locked in M2–M4 (load-bearing for future PRs)

1. **`IdentityBifunctorized` is a separate abstract type from `Bifunctorized[Identity, E, A]`.** PR-01's invariant ("Bifunctorized[F, E, A] erases to F[A] at the JVM") commits the general type to a zero-cost identity representation. For `F = Identity`, `F[A] = A`, which cannot carry typed errors. The Identity special-case therefore breaks the zero-cost invariant intentionally: every `IdentityBifunctorized[E, A]` is a boxed `MiniBIO[Throwable, A]` at runtime. This is the only Bifunctorized subtype that allocates. Do NOT "simplify" this to a transparent alias.
2. **`IO2[IdentityBifunctorized]` sourced via static reference to `MiniBIO.IOForMiniBIO`** (cast to `IO2[Bifunctorized.IdentityBifunctorized]`). No `Predefined.Of[IO2[MiniBIO]]` wrapper exists in `Root.scala`, and a direct static reference avoids potential implicit-search cycles with the CE→BIO ladder.
3. **`Bifunctorized.IdentityBifunctorized` is nested-only** (`Bifunctorized.IdentityBifunctorized`, no top-level alias in `bio/package.scala`). Resolves the plan §5 [QUESTION] in favor of nested access; promoting to top-level would mislead users about parity with `Bifunctorized[Identity, ...]` (which the implementation does NOT deliver).
4. **`LifecycleBifunctorized` and `BifunctorizedInjector` bridge via `QuasiIO.fromBIO`** (`QuasiIO.scala:201`) plus a single `asInstanceOf` cast. The pre-existing derivation is reused; no hand-rolled `QuasiIO[F]` adapter, no extra implicit on user call-sites. Total bridging code in each parallel surface: ~4 lines.
5. **`Lifecycle.scala` and `Injector.scala` are UNCHANGED.** Parallel surfaces only — Quasi*→BIO migration of internal call-sites is folded into M5 (where Quasi* deletion happens anyway). Hundreds of existing distage/Lifecycle test sites remain unaffected.
6. **`BifunctorizedInjector` accepts the *bifunctor* parameter shape** `F[+_, +_]` (not the typed-error-fixed `F[Throwable, *]`). The produced injector type is `Injector[F[Throwable, _]]` — matches the existing distage contract that the running-program error channel is `Throwable`. `TagKK[F]` → `TagK[F[Throwable, _]]` derivation works automatically via izumi-reflect.

## Known limitations carried into M5+ (audit trail in defects.md)

- **Subcontext / Producer / strategy interfaces** (`OperationExecutor`, `PlanInterpreter`, the five Strategy traits in `distage-core-api`) are NOT migrated. They still take `QuasiIO[F]`. `BifunctorizedInjector` bridges via `QuasiIO.fromBIO` internally, so the existing strategy machinery continues to work — but downstream code that needs to use these directly with a BIO F still needs to thread `QuasiIO.fromBIO` itself.
- **LogIO seam** (`LogIO`, `LogIOModule`, `LogIO2Module`, `LogIO3Module`) is NOT migrated. No `BifunctorizedLogIO` parallel surface ships in M4. Plan's PR-M4-03 deferred.
- **No `BifunctorizedIdentityBifunctorized` Goal-3 transparency**: users who write `Injector[Identity]` (or `BifunctorizedInjector[IdentityBifunctorized]`?) don't get an automatic dispatch to MiniBIO. The Identity special-case requires explicit use of `Bifunctorized.bifunctorizeIdentity` / `LifecycleBifunctorized` / `BifunctorizedInjector` with `IdentityBifunctorized` parameters. Goal 3's "transparently" qualifier is partial.
- **`CatsToBIOConversions` ladder coverage** (M1 known limitation): only `AsyncToBIO` ships; weaker conversions deferred.
- **`Either` Error2 mirror** (M1 known limitation, PR-05-D05): only IO2-tier no-op ships; Either is uncovered.
- **`CatsToBIO.shiftBlocking` is passthrough** (M1 known limitation): CE3's Async typeclass exposes no blocking-pool handle.

## What's left for M5 (the deferred deletion sweep)

The plan §2 M5 specifies:
- **PR-M5-01**: codemod across all ~106 call-sites that reference `Quasi*`. Sub-module order: `fundamentals-bio` → `distage-core-api` → `distage-core` → `distage-framework` → `distage-framework-docker` → `distage-extension-config` → `distage-testkit-core` → `distage-testkit-scalatest` → `logstage-core`.
- **PR-M5-02**: delete `fundamentals/fundamentals-bio/src/main/scala/izumi/functional/quasi/*.scala` (the entire `quasi/` package). Delete `.jvm/.../quasi/QuasiIORunner` and `.jvm/.../quasi/__QuasiAsyncPlatformSpecific`. Same for `.js/`.
- **PR-M5-03**: update `OptionalDependencyTest` to reflect the post-Quasi shape (every Quasi reference replaced by its BIO/Bifunctorized equivalent, but the test's no-cats-classpath intent preserved).
- **PR-M5-04**: cross-Scala compile lock + microsite generation.

Why this is deferred: each of the 106 call-sites needs a per-file decision (which BIO entry point to use, whether the surrounding code's typeclass constraints can be loosened, whether downstream tests still compile). Autonomous mode cannot make these judgments at scale without ballooning the risk of breaking distage's public API. A user-supervised session with focused review per sub-module is the safer path.

## What's left for M6 microsite

- Update `bio/media/bio-hierarchy.svg` (graphical asset; out of autonomous scope).
- Add a "Bifunctorized" page to the microsite navigation.
- Release notes for the version that ships M1–M4.
