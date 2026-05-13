# Izumi — Bifunctorization Task Ledger

Authoritative ledger of planned and completed work for the bifunctorization
refactor. Scope and goals are defined in `./bifunctorization.md`; detailed
plans live under `./docs/drafts/` (see per-milestone breakdown sections).

Status: `[ ]` planned · `[~]` in progress · `[x]` done · `[!]` blocked

---

## Milestones (high-level)

- [~] **M1** — Bifunctorized core + CE→BIO conversion ladder + cats laws (Goals 1, 2, 4, 5, 7).
- [ ] **M2** — Identity → MiniBIO bridge + `Bifunctorized.Identity` alias (Goals 3, 4, 7).
- [ ] **M3** — Lifecycle bifunctorization, replace `QuasiIO/QuasiPrimitives/QuasiFunctor/QuasiApplicative` constraints (Goals 3, 6, 7).
- [ ] **M4** — Injector / Subcontext / Producer / LogIO seams accept `F[+_, +_]: IO2` with monofunctor overload (Goals 3, 6, 7).
- [ ] **M5** — `Quasi*` sweep + deletion across the 9 sub-modules that currently reference it (Goals 6, 7).
- [ ] **M6** — Microsite, migration guide, release notes (Goal 7).

---

## Milestone 1 — PR breakdown

Detail in `./docs/drafts/20260513-2106-bifunctorization-plan.md` §2. Prior
art is fetched in `./docs/drafts/prior-art/{izumi-1766,cats-mtl-619}.patch`.
One line per PR here; sub-task detail stays in the plan doc.

- [x] **PR-01** — `Bifunctorized` opaque type & companion: `bifunctorize`/`debifunctorize`, implicit conversions, `toMonofunctor` syntax. Pure plumbing, no CE instances yet.
- [x] **PR-02** — `SubmergedTypedError[F]`: TagK-discriminated submarine throwable + companion `apply`/`unapply` (idempotent).
- [x] **PR-03** — `Exit.Trace` documentation note for `SubmergedTypedError`; no new trace subtype unless PR-04 proves a structural need. **Deferred into PR-04 scope** — the plan explicitly says "default: do not add a new trace type" and "leave the decision to PR-04 author". The doc note will be added in PR-04 where the actual `Exit.Trace` wiring for `SubmergedTypedError` lands.
- [x] **PR-04** — CE→BIO conversion ladder (`MonadToBIO`…`AsyncToBIO`) in `CatsToBIOConversions.scala` + impl in `impl/CatsToBIO.scala`. Core of M1. (Also absorbs PR-03's `Exit.Trace.ThrowableTrace` scaladoc note.) Design question PR-04-D01 resolved via Option B in autonomous continuation — spec amended; current zero-cost implementation kept.
- [x] **PR-05** — `BifunctorizedNoOpInstances`: high-priority no-op identity instances so `bifunctorize(zio) eq zio` holds (Goal 4). IO2-tier only; Error2 mirror for Either deferred (PR-05-D05).
- [ ] **PR-06** — Deprecate `PrimitivesFromBIOAndCats` and `PrimitivesLocalFromCatsIO`; forward to new ladder. Delete deferred to M5.
- [ ] **PR-07** — Cats `AsyncTests` laws suite against `Bifunctorized[cats.effect.IO, Throwable, _]`. Goal 1 acceptance test.
- [ ] **PR-08** — Extend `OptionalDependencyTest` to guard that `Bifunctorized` resolution does not require cats on the classpath. Goal 5 protection.
- [ ] **PR-09** — Cross-Scala compile lock: `sbt clean +Test/compile +test` green on 2.12.21, 2.13.18, 3.7.4.

---

## Cross-cutting architectural notes (locked)

Detail and rationale live in `./docs/drafts/20260513-2106-bifunctorization-plan.md` §3.

- [x] **Bifunctorized representation** — abstract type member in object companion, erased to `Any` via `asInstanceOf`; no `AnyVal`, no Scala-3 `opaque type` (cross-build symmetric with 2.12/2.13).
- [x] **Submerge discriminator** — `SubmergedTypedError[F]` carries `LightTypeTag` (not full `TagK`) plus `Any` payload; identity-based equals; `writableStackTrace=false`; idempotent `apply`. Discriminates by `LightTypeTag` equality, *not* per-region marker (deliberate departure from cats-mtl PR 619).
- [x] **No-op for actual bifunctors** — two-instance ladder via `Predefined.Of[…]`: high-priority no-op (when an `IO2[F]` exists for bifunctor `F`) vs. low-priority CE-mediated path. `eq`-identity holds because wrapper is unboxed.
- [x] **Identity special-case** — `Identity → MiniBIO[Throwable, _] → Bifunctorized[Identity, Throwable, _]`. Dedicated high-priority instance ahead of any cats-effect `Sync[Identity]` path. Implementation in M2, not M1.
- [x] **Implicit-search surface** — BIO syntax flows through existing `Syntax2` once `IO2[Bifunctorized[F, +_, +_]]` is summonable. CE→BIO ladder is in a separate `CatsToBIOConversions.scala`, opt-in via explicit import (not aggregated into `bio` package object) so Goal 5 holds.
- [x] **Package layout** — new files under `./fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/{Bifunctorized,SubmergedTypedError,BifunctorizedNoOpInstances,CatsToBIOConversions}.scala` + `impl/CatsToBIO.scala`. `bio/package.scala` adds only the alias re-export.
- [ ] **[QUESTION] `Bifunctorized.Identity` namespace** — top-level alias `type IdentityBifunctorized` vs. nested-only access. **Default: top-level alias** (parity with `Identity2`). Decide before M2-PR-01.
- [ ] **[QUESTION] `Async#cont` implementation** — use `defaultCont` from cats-effect or hand-roll? **Default: defaultCont**, revisit if PR-07 laws fail.
- [ ] **[QUESTION] `Injector.apply` overload signature** — take `TagK[F]`, `TagKK[Bifunctorized[F, *, *]]`, or both? **Default: both**, derive second from first. Decide in M4-PR-01.

---

## Completed

- **PR-01** (2026-05-13) — Introduced `izumi.functional.bio.Bifunctorized` opaque-type wrapper and companion machinery (`assert` (`private[bio]`), `bifunctorize`, `debifunctorize`, implicit conversions, `toMonofunctor`/`unwrap` syntax, `getClassTag` shim). Three files: new `fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala` (72 lines, no cats imports — Goal 5 protected), one-line type alias added to `package.scala`, new JVM-only `fundamentals/fundamentals-bio/.jvm/src/test/scala/izumi/functional/bio/BifunctorizedTypeTest.scala` (96 lines, 11 cases covering identity-eq, round-trip, variance, implicit conversions, syntax, ClassTag soundness, and real-bifunctor (ZIO) no-op). Verification: `sbt --batch '++2.13.18!' 'project fundamentals-bioJVM' 'Test/testOnly izumi.functional.bio.BifunctorizedTypeTest'` → 11/11 pass (also 2.12.21 and 3.7.4, total 411 ms test run on 2.13).

  Notes / surprises (load-bearing for future PRs):
  - `getClassTag`'s body went through 4 review rounds before settling on the sound form: `(implicit underlying: ClassTag[F[A]]) = underlying.asInstanceOf[...]`. Naive forms (`ClassTag.AnyRef`, `implicitly[ClassTag[Any]]`) lie about the runtime class when `F[A]` is a primitive (e.g. `Identity[Int] = Int`, carrying `Integer.TYPE`, not `Object`). Future maintainers: do NOT "simplify" this body without running the full 11/11 suite on all three Scala versions — both naive forms were empirically refuted at different review rounds.
  - `Bifunctorized.assert` is `private[bio]` to prevent users from minting arbitrary `Bifunctorized[F, MyError, A]` from raw `F[A]` and bypassing the submerging invariant that PR-04 will establish. PR-04's `impl/CatsToBIO.scala` retains access via package-privacy.
  - The test lives in `.jvm/` because the Goal-4 verification ("`bifunctorize(zio) eq zio`") uses `zio.ZIO`, which is JVM-only on this sub-module's classpath.
  - `DummyF`/`DummyBox`/`dummyFClassTag` are `private` members of the test class (no companion object required despite an intermediate round suggesting otherwise — empirically refuted in round 3).
  - 18 defects opened and resolved across 3 review rounds; all minor or nit, no major. See `./defects.md` for the full audit trail. D17 flags an implicit-search regression risk that may surface in PR-02..PR-08: any helper parameterised on `F[_]` that previously summoned `ClassTag[Bifunctorized[F, E, A]]` may now need an additional `ClassTag[F[A]]` constraint threaded through.

- **PR-02** (2026-05-13) — Introduced `izumi.functional.bio.SubmergedTypedError[F[_]]`: a Throwable wrapper used to submerge typed errors of arbitrary payload type into a monofunctor `F[_]`'s Throwable channel, discriminated by `TagK[F].tag` (a `LightTypeTag` value). Two files: new `SubmergedTypedError.scala` (55 lines — class + companion with idempotent `apply` and `unapply`; `writableStackTrace=false` for cheap construction) and `SubmergedTypedErrorTest.scala` (8 cases — same-`F` round-trip, cross-`F` isolation, idempotency, cross-`F` nesting, non-Throwable payloads, Throwable-cause chaining, empty stack trace, `getMessage` format). Verification: `sbt --batch '++2.12.21!' 'project fundamentals-bioJVM' 'Test/testOnly izumi.functional.bio.SubmergedTypedErrorTest izumi.functional.bio.BifunctorizedTypeTest'` → 19/19 pass (also 2.13.18 and 3.7.4). No regression in PR-01 tests.

  Notes / surprises:
  - **Discriminator: `LightTypeTag` (not `TagK`).** The captured field is `tag.tag` from `izumi.reflect.TagK[F].tag`, exploiting izumi-reflect's structural-equality contract on `LightTypeTag` (cached/interned). This is the **load-bearing departure from cats-mtl PR 619**, which discriminates by per-region `AnyRef` marker — see `bifunctorization.md`'s prior-art note and plan §3.2. Future maintainers: do NOT switch the discriminator to `AnyRef` instance identity ("for performance") — that would silently regress to cats-mtl algebraic-effects scoping, breaking the "same-F handlers compose" invariant.
  - **Idempotent `apply`.** Same-`F` `SubmergedTypedError` wrapping returns the existing instance unchanged (verified by `eq` in test 3). Different-`F` wrapping does NOT collapse — that's the discriminator working as intended (test 4).
  - **Wildcard `[_]` (not `[?]`)** in pattern matches. The codebase mixes both styles; the `_` form works on all three Scala versions without a deprecation warning.
  - **PR-01-D16 echo (decorative companion object) recurred** as PR-02-D01 and was fixed the same way — fixtures inlined as class-body `private trait`s. Future PRs: do NOT move test fixtures to companion objects unless empirically required (the reviewer's pre-validation confirmed inlining works on 2.12/2.13/3 here as well).

- **PR-04** (2026-05-13) — CE→BIO conversion factory + implicit landing pad + PR-03 fold-in. Four files changed across two commits (`c97139dc3` PR-04 proper, follow-up commit for design-decision spec amendment): new `impl/CatsToBIO.scala` (325 lines — full `asyncToBIO[F]` factory: `Async2 & Temporal2 & Fork2 & BlockingIO2 & Primitives2` over `Bifunctorized[F, +_, +_]` from a single `cats.effect.kernel.Async[F]` plus `TagK[F]`; all prior-art `???` stubs filled in), new `CatsToBIOConversions.scala` (40 lines — opt-in implicit landing pad with `AsyncToBIO` instance; weaker conversions deferred), new `CatsToBIOTest.scala` (94 lines, 7 cases). One scaladoc edit on `Exit.scala` (`ThrowableTrace` covers `SubmergedTypedError`). Verification: 26/26 tests pass on Scala 3.7.4, 2.13.18, 2.12.21 (`CatsToBIOTest` + `BifunctorizedTypeTest` + `SubmergedTypedErrorTest`); `OptionalDependencyTest` 7/7 (Goal 5 sanity).

  Notes / surprises (load-bearing for future PRs):
  - **Submerging semantics are operation-internal, not type-level.** Spec was amended (D01 / Option B) to make this explicit: `bifunctorize`/`debifunctorize` are type-level identity (Goal 4 zero-cost preserved), and submerging happens inside BIO methods (`fail`, `catchAll`, `syncThrowable`, `fromFuture`, etc.). Users who use BIO methods see clean typed-error semantics; users who unwrap and use raw `F` methods see the wire-level shape (`SubmergedTypedError[F]` in the Throwable channel) and extract via `SubmergedTypedError.unapply`.
  - **`sync` vs `syncThrowable` distinction**: `sync` has typed channel `Nothing` — any thrown exception is a defect (raw Throwable). `syncThrowable`/`syncBlocking`/`syncInterruptibleBlocking`/`fromFuture`/`fromFutureJava` have typed channel `Throwable` — any caught Throwable IS submerged into `SubmergedTypedError[F]`. Plan §2 PR-04 was corrected to clarify (was previously imprecise).
  - **`shiftBlocking` is passthrough identity** on CE-backed `Bifunctorized` — CE3's `Async` typeclass does not expose a blocking-pool handle (`cats.effect.IO.blocking` is IO-specific). Documented in-code; M6 migration guide will flag this for library authors relying on `BlockingIO2#shiftBlocking` semantics.
  - **`PR-04-D03` (missing tests for `syncThrowable`/`syncBlocking`/`fromFuture` round-trips) remains open** — should be picked up in a small follow-up or before M1 closes, but is not blocking on the implementation.
  - **Implicit-search hygiene**: `CatsToBIOConversions.AsyncToBIO` returns `NotPredefined.Of[…]` so predefined BIO instances (ZIO, MiniBIO) win against the cats-mediated fallback. PR-05 will add no-op identity instances at the very top of the priority ladder for any `F[+_, +_]` already carrying an `IO2`.

- **PR-05** (2026-05-13) — High-priority no-op identity instance for `Bifunctorized.NoOp[F[+_, +_], +E, +A]` when `F` already has an `IO2[F]` instance (ZIO, MiniBIO, MonixBIO, …). Three files modified: `Bifunctorized.scala` gets a new abstract type alias `type NoOp[F[+_, +_], +E, +A]` and a `BifunctorizedNoOpOps.unwrap: F[E, A]` extension class, plus `extends BifunctorizedNoOpInstances` on its companion object (scoped mixin); new `BifunctorizedNoOpInstances.scala` (~25 lines, single `Predefined.Of[IO2[NoOp[F, +_, +_]]]` factory casting from `IO2[F]`); new `.jvm/BifunctorizedNoOpTest.scala` (6 cases). 32/32 PR-01..PR-05 tests pass on Scala 3.7.4, 2.13.18, 2.12.21; `OptionalDependencyTest` 7/7 (Goal 5 sanity).

  Notes / surprises (load-bearing for future PRs):
  - **`NoOp` is an abstract type member**, NOT a transparent alias as the plan §3.3 sketched. Reviewer empirically verified that `type NoOp[F[+_, +_], +E, +A] = Bifunctorized[F[E, *], E, A]` fails on ALL three Scala versions with a covariance error — `+E` ends up in an invariant slot of `F[E, *]`. Switching `E` to invariant makes the alias compile but breaks downstream `NoOp[F, +_, +_]` partial applications. The abstract-type form preserves declared variance; runtime representation is still `F[E, A]` via `asInstanceOf`. Future maintainers: do NOT "simplify" this to a transparent alias.
  - **`BifunctorizedNoOpInstances` is mixed into `object Bifunctorized`** (the companion of `NoOp`), NOT into the `bio` package object as plan §3.3 implied. Reviewer empirically verified that the package-object mixin BREAKS 13 sites across `SyntaxTest` and `ZIOWorkaroundsTest` (the no-op factory greedily satisfies unbound `IO2[X]` searches via `F = NoOp[NoOp[ZIO, _, _], _, _]` deeply-nested chains). Mixing into the companion scopes the implicit to `NoOp[…]` searches only — Goal 4 still satisfied via implicit-scope-of-RHS-of-alias.
  - **`BifunctorizedNoOpOps.unwrap` returns `F[E, A]`** (binary), distinct from `BifunctorizedOps.unwrap`'s `F[A]` (unary). The two extensions coexist because their target types are distinct (`NoOp[F, E, A]` vs `Bifunctorized[F, E, A]`).
  - **Either coverage deferred (PR-05-D05)**: Goal 4 names Either as a real bifunctor, but Either has only `Error2`, not `IO2`. Current PR-05 IO2-only factory does NOT serve `IO2[NoOp[Either, ?, ?]]`. Mirrored Functor2/Applicative2/Monad2/Error2 factories would address this; deferred to a follow-up before M1 closes. Empirically: `assertCompiles("implicitly[Error2[Bifunctorized.NoOp[Either, ?, ?]]]")` currently fails.
  - **Cycle prevention**: the executor initially constrained the factory input to `Predefined.Of[IO2[F]]` claiming implicit-search recursion; reviewer empirically falsified that claim. Reverted to plain `implicit F: IO2[F]` per spec — no recursion, no ambiguity, on all 444 fundamentals-bioJVM tests. Future maintainers: the false rationale lives in PR-05-D03's audit trail; do not reintroduce the `Predefined.Of` constraint based on the "recursion" reasoning.
