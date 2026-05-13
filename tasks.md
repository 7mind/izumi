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
- [ ] **PR-02** — `SubmergedTypedError[F]`: TagK-discriminated submarine throwable + companion `apply`/`unapply` (idempotent).
- [ ] **PR-03** — `Exit.Trace` documentation note for `SubmergedTypedError`; no new trace subtype unless PR-04 proves a structural need.
- [ ] **PR-04** — CE→BIO conversion ladder (`MonadToBIO`…`AsyncToBIO`) in `CatsToBIOConversions.scala` + impl in `impl/CatsToBIO.scala`. Core of M1.
- [ ] **PR-05** — `BifunctorizedNoOpInstances`: high-priority no-op identity instances so `bifunctorize(zio) eq zio` holds (Goal 4).
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
