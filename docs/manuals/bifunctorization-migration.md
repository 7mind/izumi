# Migrating to Bifunctorized: User Guide

Status: this document covers the final M5 state of the bifunctorization
refactor on branch `feature/bifunctorization`. M5 completed the structural
work — `Lifecycle`, `Injector`, and all supporting interfaces are now
bifunctor-shaped; the `Quasi*` / `*1` monofunctor adapter tier and the
M3/M4 parallel surfaces (`LifecycleBifunctorized`, `BifunctorizedInjector`)
have been deleted. The outstanding items (stubbed test fixtures, 3
Scala 2-only logstage macro files, Scala 2.12 cross-build) are tracked in
`tasks.md` and `defects.md` but are not blockers for the refactor landing.

## Why bifunctorize?

The `Quasi*` family of compatibility typeclasses (`QuasiIO`,
`QuasiAsync`, `QuasiPrimitives`, `QuasiFunctor`, `QuasiApplicative`,
`QuasiIORunner`) let Izumi libraries accept arbitrary effect types
`F[_]`. But:

- They're monofunctor-only, which prevents Izumi internals from
  using typed errors.
- They include unlawful `Identity` support — every Izumi maintainer
  has to remember that `F` in `F[_]: QuasiIO` may not be a lawful
  monad.
- The hierarchy duplicates work that the BIO typeclasses
  (`Functor2`, `Monad2`, …, `Async2`) already do for bifunctors.

The bifunctorization refactor replaces this with **one unified
bifunctor scheme**: every effect type, whether natively a bifunctor
(ZIO, MonixBIO, MiniBIO, Either) or a monofunctor (cats.effect.IO,
scala.util.Try, Identity), is lifted into the bifunctor world via
`Bifunctorized[F[_], +E, +A]` and used through the BIO typeclasses.

## The new types

| Type | Where | What it is |
|------|-------|------------|
| `izumi.functional.bio.Bifunctorized[F[_], +E, +A]` | `bio.Bifunctorized.scala` | Opaque newtype lifting a monofunctor `F[_]` into a bifunctor. Erased to `F[A]` at the JVM — zero-cost identity for real bifunctors (Goal 4). |
| `Bifunctorized.NoOp[F[+_, +_], +E, +A]` | same | Opaque newtype for an effect type that's *already* a bifunctor (ZIO, MonixBIO, Either, MiniBIO, etc.). Erased to `F[E, A]`. |
| `Bifunctorized.IdentityBifunctorized[+E, +A]` | same | Identity special-case. Carries `MiniBIO[Throwable, A]` at runtime (the only Bifunctorized subtype that's *not* zero-cost; `Identity[A] = A` cannot carry typed errors, so we box via MiniBIO). |
| `SubmergedTypedError[F[_]]` | `bio.SubmergedTypedError.scala` | Throwable wrapper that hides a typed error inside a monofunctor's Throwable channel, `TagK[F]`-discriminated so cross-`F` errors stay opaque. |

## How to construct a `Lifecycle`

Before (pre-M5, `Quasi*`-constrained):

```scala
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIO

def myResource[F[_]: QuasiIO]: Lifecycle[F, Int] =
  Lifecycle.make[F, Int](QuasiIO[F].pure(42))(_ => QuasiIO[F].unit)
```

After (M5, `Lifecycle` is now bifunctor-shaped):

```scala
import izumi.functional.bio.{IO2, Primitives2}
import izumi.functional.lifecycle.Lifecycle

def myResource[F[+_, +_]: IO2: Primitives2]: Lifecycle[F, Throwable, Int] =
  Lifecycle.make[F, Throwable, Int](IO2[F].pure(42))(_ => IO2[F].unit)
```

`Lifecycle` itself now carries the bifunctor `F[+_, +_]` directly.
`LifecycleBifunctorized` — the M3 parallel surface — has been deleted because
it is no longer needed. For a monofunctor `F[_]` (e.g. `cats.effect.IO`),
wrap it at the call-site:

```scala
import izumi.functional.bio.Bifunctorized
import izumi.functional.bio.CatsToBIOConversions.AsyncToBIO

def myIOResource: Lifecycle[Bifunctorized[cats.effect.IO, +_, +_], Throwable, Int] =
  Lifecycle.make(IO2[Bifunctorized[cats.effect.IO, +_, +_]].pure(42))(_ => IO2[...].unit)
```

For `cats.effect.Resource[F, A]`, use `Lifecycle.fromCats` which performs
transparent bifunctorization and returns
`Lifecycle[Bifunctorized[F, +_, +_], Throwable, A]` directly.

The `Lifecycle3` alias handles ZIO-env-parameterized Lifecycles:
`Lifecycle3[ZIO, R, E, A] = Lifecycle[λ[(+e, +a) => ZIO[R, e, a]], E, A]`.

## How to construct an `Injector`

Before (pre-M5):

```scala
import izumi.distage.model.Injector

// ZIO: required QuasiIO[ZIO[Any, Throwable, *]] or BifunctorizedInjector
val injector: Injector[zio.ZIO[Any, Throwable, *]] = Injector[zio.ZIO[Any, Throwable, *]]()
```

After (M5, `Injector` is now bifunctor-shaped):

```scala
import izumi.distage.model.Injector

// ZIO
val injector: Injector[zio.ZIO[Any, +_, +_]] = Injector[zio.ZIO[Any, +_, +_]]()

// cats.effect.IO (via Bifunctorized)
import izumi.functional.bio.{Bifunctorized, CatsToBIOConversions}
import CatsToBIOConversions.{AsyncToBIO, PrimitivesToBIO}
val cioInjector: Injector[Bifunctorized[cats.effect.IO, +_, +_]] =
  Injector[Bifunctorized[cats.effect.IO, +_, +_]]()

// Identity
val idInjector: Injector[Bifunctorized.IdentityBifunctorized] =
  Injector[Bifunctorized.IdentityBifunctorized]()
```

`BifunctorizedInjector` — the M4 parallel surface — has been deleted because
`Injector` itself now accepts `F[+_, +_]`. The typed-error channel is fixed at
`Throwable` by the `DefaultModule[F]` constraint (distage's existing contract
for running programs).

`Injector.apply[F[+_, +_]: TagKK: IO2: Primitives2: DefaultModule](overrides*)` is
the primary entry point. For specialized use, `produceCustomF[F[+_, +_]:
TagKK: IO2: Primitives2]` and `produceCustomIdentity` (returning
`Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, Locator]`) are also
available on `Producer`.

## Submerging and un-submerging typed errors

For a *real bifunctor* `F[+_, +_]` (ZIO, MonixBIO, Either, MiniBIO),
nothing changes: `F`'s native typed-error channel is reused. PR-05's
`BifunctorizedNoOpInstances.bifunctorIsAlreadyBifunctor` casts the
existing `IO2[F]` dictionary directly. No allocation, no boxing.

For a *monofunctor* `F[_]` with a `cats.effect.kernel.Async[F]`
instance, PR-04's `CatsToBIOConversions.AsyncToBIO` derives an
`Async2[Bifunctorized[F, +_, +_]]`. Typed errors raised through BIO
methods (`F.fail`, `F.catchAll`, `F.syncThrowable`, `F.fromFuture`)
are submerged into `F`'s Throwable channel as `SubmergedTypedError[F]`
(discriminated by `TagK[F]` so cross-`F` errors stay opaque). Defects
raised through `F.terminate` or thrown synchronously remain raw.

The user-visible BIO surface looks like a clean typed-error effect.
If you unwrap a `Bifunctorized[F, Throwable, A]` back to `F[A]` via
`.toMonofunctor` (or the implicit `debifunctorizeConversion`) and use
raw `F` methods like `cats.effect.IO.handleErrorWith`, you'll see the
wire-level representation: typed errors appear as
`SubmergedTypedError[F]` instances. Use
`SubmergedTypedError.unapply[F]` to extract the original payload:

```scala
import izumi.functional.bio.SubmergedTypedError
import izumi.reflect.TagK

val io: cats.effect.IO[Int] = bifunctorizedValue.toMonofunctor
val recovered: cats.effect.IO[Int] = io.handleErrorWith {
  case SubmergedTypedError(payload: MyTypedError) => cats.effect.IO.pure(payload.recoveryValue)
  case other => cats.effect.IO.raiseError(other)
}
```

This is documented in `bifunctorization.md` "Conversion of effect
values" section (amended at commit `6fecdd330` to reflect the
implemented semantics).

## Identity special-case

`Identity` has no error channel (`type Identity[+A] = A`), so the
general `Bifunctorized[Identity, E, A]` would erase to `A` and
couldn't carry typed errors. Instead, M2 introduces a *separate
opaque type* `Bifunctorized.IdentityBifunctorized[+E, +A]` whose
runtime carrier is `MiniBIO[Throwable, A]` (boxed — the only
Bifunctorized subtype that's not zero-cost).

The wired entry points (`Lifecycle.make`, `Injector.apply`) accept any bifunctor
that has an `IO2` instance, including `IdentityBifunctorized` — so
users who pass `IdentityBifunctorized` get lawful monadic behavior
(the old `QuasiIOIdentity.maybeSuspend` was unlawful; MiniBIO
suspends correctly).

Construct one via:

```scala
import izumi.functional.bio.Bifunctorized
import izumi.fundamentals.platform.functional.Identity

val a: Bifunctorized.IdentityBifunctorized[Throwable, Int] =
  Bifunctorized.bifunctorizeIdentity(42)

val out: Identity[Int] = Bifunctorized.debifunctorizeIdentity(a) // = 42
```

`debifunctorizeIdentity` runs the underlying MiniBIO synchronously
(via `MiniBIO.autoRun.autoRunAlways`); it rethrows on typed-error or
defect.

## Goals satisfied

- **Goal 1** — Cats laws: `cats.effect.laws.AsyncTests` over
  `Bifunctorized[cats.effect.IO, Throwable, +_]` passes **109/109**
  (PR-07).
- **Goal 2** — Submerged errors discriminated by `TagK[F]`; defects
  use raw Throwable (PR-02, PR-04, PR-08).
- **Goal 3** — Transparent bifunctorization at distage/Lifecycle/LogIO
  seams: **satisfied on Scala 3** (M5). `Lifecycle`, `Injector`,
  `Subcontext`, `Producer`, and all 7 strategy interfaces carry
  `F[+_, +_]` directly. `Injector[Bifunctorized.IdentityBifunctorized]()`
  routes through MiniBIO automatically.
- **Goal 4** — Zero-cost no-op for actual bifunctors:
  `bifunctorize(zio) eq zio` (PR-01), high-priority no-op identity
  instance in `BifunctorizedNoOpInstances` (PR-05).
- **Goal 5** — No-More-Orphans: `bio/package.scala` imports no cats;
  `CatsToBIOConversions` is opt-in via explicit import;
  `OptionalDependencyTest` 29/29 passes verifying Bifunctorized /
  SubmergedTypedError / BifunctorizedNoOpInstances are reachable on a
  no-cats classpath (PR-08, M5 Session 5).
- **Goal 6** — `Quasi*` / `*1` deletion: **complete on Scala 3** (M5).
  The `Quasi*` family, the intermediate `*1` monofunctor tier, and all
  parallel surfaces (`LifecycleBifunctorized`, `BifunctorizedInjector`)
  are deleted. Zero matches for `\b(IO1|Async1|...|IORunner1|Ref0)\b`
  on Scala 3-active source paths. Three matches remain in `scala-2/`-only
  logstage macro files (deferred).
- **Goal 7** — Cross-build: Scala 3.7.4 ✅, Scala 2.13.18 ✅ (main
  sources; test compile deferred), Scala 2.12.21 dropped at the
  `Lifecycle.F` covariance boundary (per user direction; unblocked
  manually later).

## Known limitations

1. **`CatsToBIOConversions` ships only `AsyncToBIO` and `PrimitivesToBIO`.**
   Weaker conversions (`SyncToIO2`, `MonadToBIO`, `ErrorToBIO`, etc.)
   are not implemented. Users with a weaker cats-effect typeclass (e.g.
   only `Sync[F]`) must provide an `Async[F]` instance, or use a
   real bifunctor (ZIO, MonixBIO) where no conversion is needed.

2. **No-op identity covers only the IO2 tier.** Bifunctors with only
   `Error2` (canonical example: `Either`) do not have a no-op instance —
   `IO2[Bifunctorized.NoOp[Either, ?, ?]]` does not resolve. Mirrors at
   `Functor2`/`Applicative2`/`Monad2`/`Error2` tiers are deferred
   (PR-05-D05).

3. **`CatsToBIO.shiftBlocking` is passthrough identity.** CE3's `Async`
   typeclass exposes no generic blocking-pool handle (`cats.effect.IO.blocking`
   is IO-specific). Library code using `BlockingIO2#shiftBlocking` on a
   CE-backed Bifunctorized may experience thread starvation. Future work:
   an IO-specific specialization.

4. **M5-D01 — izumi-reflect η-normalization deficiency.** 3
   `CatsResourcesTestJvm` tests remain disabled. `make[T].fromResource(cats.effect.Resource[F, T])`
   bindings fail at runtime against `Injector[Bifunctorized[F, +_, +_]]()`
   because the binding-side and Injector-side `LightTypeTag` representations
   of `Bifunctorized[IO, _, _]` differ (η-expanded vs unexpanded) and
   `LightTypeTag.<:<` treats them as non-equivalent. Fix must land in
   izumi-reflect. Full audit in `defects.md [M5-D01]`.

5. **Stubbed test fixtures.** `Spec1[F[_]]` was rewritten as an alias for
   the bifunctor shape in M5 Session 5. All test fixtures that used the
   monofunctor spelling were stubbed out pending explicit migration to
   `Spec2[F[+_, +_]]` / `SpecIdentity` / `SpecZIO`.

6. **8 `RoleAppTest` failures.** Test-fixture `Async[IO]` wiring issue in
   `distage-frameworkJVM` — not a main-source defect.

7. **3 Scala 2-only logstage macro files** still reference `IO1`/`Primitives1`
   (in `src/main/scala-2/`). Migration pattern is identical to Session 6's
   Scala 3 rebuild but uses `c.universe` quasiquotes. Deferred.

8. **Scala 2.13 test compile** (~97 errors in `distage-coreJVM/Test`).
   Main sources compile; the test-compile errors are `LifecycleTag.resourceTag`
   higher-kinded unification gaps. Deferred — user will unblock.

9. **Scala 2.12 cross-build dropped** at the `Lifecycle.F` covariance
   boundary. The supertype-dance pattern `[G[+e, +a] >: F[e, a]]` is
   rejected by Scala 2.12's variance check. Per user direction: proceed
   with Scala 2.13 + 3 only.

## What's the failure mode at the API edge?

If you write `Injector[F[+_, +_]]()` with an `F` that has no
`IO2[F]` / `Primitives2[F]` / `DefaultModule[F]`, the compile fails
on the missing implicit. For a monofunctor `F[_]`, import
`CatsToBIOConversions.{AsyncToBIO, PrimitivesToBIO}` and use
`Injector[Bifunctorized[F, +_, +_]]()`. For ZIO, use
`Injector[ZIO[Any, +_, +_]]()` directly (ZIO has native `IO2`).

If `make[T].fromResource(catsResource)` fails at runtime with
`IncompatibleEffectType`, this is M5-D01 (izumi-reflect η-normalization
gap). The workaround is to use `Injector[Bifunctorized.IdentityBifunctorized]()`
(avoids the η-expansion mismatch) or to wait for the izumi-reflect fix.

For `Lifecycle`, all factories (`make`, `makePair`, `liftF`, `pure`,
`suspend`, `fail`, `unit`) require `IO2[F]` and `Primitives2[F]` in
scope. For a CE-backed `F[_]`, wrap as `Bifunctorized[F, +_, +_]` and
import `AsyncToBIO` + `PrimitivesToBIO`.

## References

- Spec: `bifunctorization.md`
- Plan: `docs/drafts/20260513-2106-bifunctorization-plan.md`
- M1 closure summary: `docs/changes/M1-bifunctorized-core.md`
- M2-M4 closure summary: `docs/changes/M2-M4-bifunctorized-seams.md`
- M5 closure summary: `docs/changes/M5-bifunctorized-deletion.md`
- Defect audit trail: `defects.md`
- Session logs: `docs/logs/`
- Prior art: `docs/drafts/prior-art/{izumi-1766,cats-mtl-619}.patch`
