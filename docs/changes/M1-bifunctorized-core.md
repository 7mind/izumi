# M1 — Bifunctorized Core & CE→BIO Conversion (closed 2026-05-13)

Replaces the early stages of the `Quasi*` family of compatibility typeclasses
with a unified bifunctor scheme via `Bifunctorized[F[_], +E, +A]`, an opaque
newtype that lifts a monofunctor effect type `F[_]` into a bifunctor while
preserving runtime identity (`bifunctorize(zio) eq zio` for real bifunctors —
Goal 4). Typed errors raised through BIO methods on `Bifunctorized[F, _, _]`
are submerged into `F`'s Throwable channel as `SubmergedTypedError[F]`,
TagK-discriminated so cross-`F` errors cannot intercept each other.

Goals 1, 2, 4, 5, 7 of `bifunctorization.md` are met by M1. Goals 3 (Identity
special-case, distage/Lifecycle/LogIO seam migration) and 6 (Quasi* deletion)
are M2-M5 work.

## What ships in M1

| PR | Commit | Files | Key contract |
|----|--------|-------|--------------|
| PR-01 | `05d0b2af0` | `Bifunctorized.scala`, `package.scala`, `BifunctorizedTypeTest.scala` | Opaque newtype; companion `bifunctorize`/`debifunctorize`/syntax/`ClassTag` shim. Zero-cost (no `AnyVal` wrapper, no Scala-3 `opaque type`, single abstract type member). |
| PR-02 | `5e337be76` | `SubmergedTypedError.scala`, `SubmergedTypedErrorTest.scala` | TagK-discriminated submarine throwable. `apply` is idempotent for same-`F`; cross-`F` wrapping is intentional opacity. `writableStackTrace=false`. Deliberate departure from cats-mtl PR-619's per-region marker design. |
| PR-03 | (folded into PR-04) | `Exit.scala` scaladoc note | `Exit.Trace.ThrowableTrace` documented to cover `SubmergedTypedError`. No new trace subtype. |
| PR-04 | `c97139dc3`, `6fecdd330` (follow-up), `37b7b1d46` (race+Clock2+never fixes) | `impl/CatsToBIO.scala`, `CatsToBIOConversions.scala`, `CatsToBIOTest.scala`, `Exit.scala` (note) | `asyncToBIO[F]` factory returning `Async2 & Temporal2 & Fork2 & BlockingIO2 & Primitives2 & Clock2`. Submerging at `fail`/`catchAll`/`syncThrowable`/`fromFuture`/etc. `sync` and `terminate` keep throwables raw (defects). |
| PR-05 | `5bc08745f` | `Bifunctorized.scala` (+`NoOp` type + `BifunctorizedNoOpOps.unwrap`), `BifunctorizedNoOpInstances.scala`, `BifunctorizedNoOpTest.scala` | `Predefined.Of[IO2[NoOp[F, +_, +_]]]` for any bifunctor `F` already carrying an `IO2`. Outranks the CE→BIO submerging path. Identity-via-cast, zero allocation. |
| PR-06 | `075f73de2` | `PrimitivesFromBIOAndCats.scala` (annotated), `PrimitivesLocalFromCatsIO.scala` (annotated), `OptionalDependencyTest.scala` (`@nowarn`) | Deprecation only; deletion in M5. |
| PR-07 | `37b7b1d46` | `laws/CatsLawsTest.scala`, `laws/env/CatsTestEnv.scala` + `impl/CatsToBIO.scala` race/Clock2/never fixes | Goal-1 acceptance: cats-effect `AsyncTests` 109/109 pass over `Bifunctorized[cats.effect.IO, Throwable, +_]`. CE → BIO → CE round-trips without law violation. |
| PR-08 | `8d1b3178f` | `OptionalDependencyTest.scala` (extension), `CatsToBIOTest.scala` (+3 cases) | Goal-5 hardening (Bifunctorized/SubmergedTypedError/NoOpInstances reachable on no-cats classpath); PR-04-D03 fold-in (syncThrowable/syncBlocking/fromFuture round-trip tests). |
| PR-09 | this commit | M1 changelog | No-source-change. Documents M1 closure. |

## Verification at close-of-M1

- `fundamentals-bioJVM` `Test/testOnly izumi.functional.bio.*` — 42 tests pass (11 BifunctorizedTypeTest + 8 SubmergedTypedErrorTest + 6 BifunctorizedNoOpTest + 10 CatsToBIOTest + 7 other regression-checked sets). 109/109 `CatsLawsTest` laws.
- `distage-extension-configJVM` `Test/testOnly izumi.distage.impl.OptionalDependencyTest` — 8/8 pass (was 7/7 before PR-08 added the Bifunctorized reachability block).
- Cross-build: green on Scala 3.7.4, 2.13.18, 2.12.21. The historic 2.12 variance pain point did not surface (the abstract-type representation honors `+E, +A` covariance on 2.12 without needing a `widen` helper).
- Goal 5 ("No-More-Orphans"): `bio/package.scala` imports no cats; `Bifunctorized.scala` and `SubmergedTypedError.scala` import no cats; `BifunctorizedNoOpInstances.scala` imports no cats. Cats-touching code is confined to `impl/CatsToBIO.scala` and `CatsToBIOConversions.scala` (opt-in via explicit `import izumi.functional.bio.CatsToBIOConversions.*`).

## Design decisions locked in M1 (load-bearing for M2-M6)

The full audit trail lives in `defects.md`. Decisions that future PRs must respect:

1. **`Bifunctorized.bifunctorize` and `debifunctorize` are type-level identity** (PR-04-D01, Option B). Submerging happens inside BIO instance methods (`fail`, `catchAll`, `syncThrowable`, etc.), not at the conversion seam. Users who use BIO methods see clean typed-error semantics; users who unwrap and use raw `F` methods see `SubmergedTypedError[F]` in the Throwable channel. Spec amended at `bifunctorization.md` "Conversion of effect values" section to document this.

2. **`Bifunctorized.NoOp` is an abstract type member**, NOT a transparent alias (PR-05-D01). The alias `type NoOp[F[+_, +_], +E, +A] = Bifunctorized[F[E, *], E, A]` fails on all three Scala versions with a covariance error (covariant `E` in invariant `F[E, *]` slot). Future maintainers: do not "simplify" this back to an alias.

3. **`BifunctorizedNoOpInstances` is mixed into `object Bifunctorized`** (the companion of `NoOp`), NOT into `bio/package.scala` (PR-05-D02). Mixing into the package object breaks 13 sites across `SyntaxTest` and `ZIOWorkaroundsTest` because the no-op factory greedily satisfies unbound `IO2[X]` searches with deeply-nested `NoOp[NoOp[ZIO, _, _], _, _]` chains.

4. **`getClassTag` uses `implicit ClassTag[F[A]]`**, not `ClassTag.AnyRef` or `implicitly[ClassTag[Any]]` (PR-01-D14). The runtime value of `Bifunctorized[F, E, A]` IS an `F[A]`; treating it as `Object` lies about the runtime class when `F[A]` is a primitive (e.g. `Identity[Int] = Int`). Defensive scaladoc on the method documents the `Identity[Int] = Int` motivation.

5. **`SubmergedTypedError` discriminator is `LightTypeTag`** (structural equality via izumi-reflect), NOT a per-region `AnyRef` marker (deliberate departure from cats-mtl PR-619; PR-02 design §3.2). Do not "simplify" to instance identity — that regresses to the rejected algebraic-effects scoping.

6. **`CatsToBIO.asyncToBIO` includes `Clock2` in its intersection type** (PR-07 fix). Without `Clock2`, `CatsConversions.BIOToAsync` falls back to a real-time `Clock1.Standard` and breaks cats-effect-laws tests that rely on the `Ticker` virtual clock. The override uses `F.realTime`/`F.monotonic` (cats-effect Async's native methods, Ticker-aware in test mode).

7. **`CatsToBIO.race` is derived from `racePairUnsafe`** with explicit `Exit` pattern-match + loser interruption (PR-07 fix). The previous `F.map(F.race(...))(fold)` implementation failed the "race derives from racePair" cats-effect law.

8. **`CatsToBIO.never` overrides `F.never[Nothing]` directly** rather than letting `WeakAsync2.never` default-route through `async_` (PR-07 fix). CE3's `async_` produces an uncancelable fiber; cats-effect's native `never` is cancelable. Without the override, race-with-never laws hang in virtual time.

## Known limitations carried into M2

- **PR-05-D05**: `Bifunctorized.NoOp[Either, ?, ?]` does NOT resolve via the no-op ladder. PR-05 ships only the `IO2` tier; Either has only `Error2` (not `IO2`). Plan §3.3 sketched mirrors at `Functor2`/`Applicative2`/`Monad2`/`Error2` tiers — opening for a follow-up PR before M5 ships. Goal 4 is satisfied for ZIO/MiniBIO/MonixBIO but not for Either.
- **PR-04 `shiftBlocking` is passthrough identity** on CE-backed `Bifunctorized` (CE3's `Async` typeclass exposes no blocking-pool handle; `cats.effect.IO.blocking` is IO-specific). Documented in-code; M6 migration guide will flag this for library authors relying on `BlockingIO2#shiftBlocking` semantics.
- **`CatsToBIOConversions` ships only `AsyncToBIO`** (no weaker `MonadToBIO`/`ErrorToBIO`/etc.). Each would require a separate factory paralleling `asyncToBIO`. Plan §5 [QUESTION] flagged this; deferred to follow-up.
- **The implicit landing pad exposes only `Async2[Bifunctorized[F, ?, ?]]`** to implicit search. Callers needing `BlockingIO2[Bifunctorized[F, ?, ?]]` etc. must cast from the Async2 instance (which IS the full intersection at runtime). UX wart of the single-implicit ladder; addressing it requires either multiple landing-pad implicits or a richer summon helper. Deferred.

## What comes next

- **M2** — Identity special-case: `Bifunctorized.IdentityBifunctorized` going through `MiniBIO[Throwable, _]` interpretation. Identity becomes a lawful monad (with suspension) under the bifunctor view, unlike the unlawful `QuasiIOIdentity`.
- **M3** — Lifecycle bifunctorization. Replace `QuasiIO`/`QuasiPrimitives`/`QuasiFunctor`/`QuasiApplicative` constraints on `Lifecycle` combinators with BIO hierarchy on `Bifunctorized[F, Throwable, ?]`.
- **M4** — Distage `Injector` / LogStage `LogIO` seams.
- **M5** — `Quasi*` deletion sweep across all 9 sub-projects that reference it.
- **M6** — Microsite, migration guide, release notes.
