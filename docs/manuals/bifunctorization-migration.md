# Migrating to Bifunctorized: User Guide

Status: this document covers what shipped through M1–M4 of the
bifunctorization refactor (commits `05d0b2af0` … `b10409187` on branch
`feature/bifunctorization`). M5 (`Quasi*` deletion across ~106
call-sites) is deferred to a user-supervised follow-up; until then,
both the new BIO entry points and the existing `Quasi*`-based ones
coexist.

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
| `LifecycleBifunctorized` | `functional.lifecycle.LifecycleBifunctorized.scala` | Parallel BIO surface to `Lifecycle` (`make`, `liftF`, `pure`, `suspend`, `fail`, `makePair`, `unit`). |
| `BifunctorizedInjector` | `distage.model.BifunctorizedInjector.scala` | Parallel BIO surface to `Injector` (`apply`, `inherit`). |

## How to construct a `Lifecycle` via the BIO surface

Before (existing, `Quasi*`-constrained):

```scala
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIO

def myResource[F[_]: QuasiIO]: Lifecycle[F, Int] =
  Lifecycle.make[F, Int](QuasiIO[F].pure(42))(_ => QuasiIO[F].unit)
```

After (new, BIO-constrained):

```scala
import izumi.functional.bio.{IO2, Bifunctorized}
import izumi.functional.lifecycle.{Lifecycle, LifecycleBifunctorized}

def myResource[F[+_, +_]](
  implicit F: IO2[Bifunctorized.NoOp[F, +_, +_]]
): Lifecycle[F[Throwable, _], Int] =
  LifecycleBifunctorized.make[F, Int](F.pure(42))(_ => F.unit)
```

The new surface produces `Lifecycle[F[Throwable, _], A]` (the same
shape distage's `Injector[F[Throwable, _]]` expects). Internally the
BIO instance is bridged to a `QuasiIO[F[Throwable, _]]` via the
existing `QuasiIO.fromBIO` derivation (see `QuasiIO.scala:201`), so
the existing `Lifecycle` infrastructure is reused unchanged.

## How to construct an `Injector` via the BIO surface

Before:

```scala
import izumi.distage.model.Injector
import izumi.functional.quasi.QuasiIO

val injector: Injector[zio.ZIO[Any, Throwable, *]] = Injector[zio.ZIO[Any, Throwable, *]]()
```

After:

```scala
import izumi.distage.model.BifunctorizedInjector

val injector: Injector[zio.ZIO[Any, Throwable, _]] = BifunctorizedInjector[zio.ZIO[Any, +_, +_]]()
```

The bifunctor type parameter takes the *real* bifunctor shape (`ZIO[Any, +_, +_]`,
not the typed-error-fixed `ZIO[Any, Throwable, *]`). The injector
produced still has the typed-error channel fixed at `Throwable` (per
distage's existing contract — Throwable is the failure channel of a
running program).

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

The wired entry points (currently
`LifecycleBifunctorized`/`BifunctorizedInjector`) accept any bifunctor
that has an `IO2` instance, including the IdentityBifunctorized — so
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
  seams: partial. `LifecycleBifunctorized` and `BifunctorizedInjector`
  provide BIO entry points (M3, M4). Full transparency (where the
  user-visible `Injector[Identity]` automatically routes through
  IdentityBifunctorized) is M5/M6 work — until M5, the user
  explicitly uses the BIO surface.
- **Goal 4** — Zero-cost no-op for actual bifunctors:
  `bifunctorize(zio) eq zio` (PR-01), high-priority no-op identity
  instance in `BifunctorizedNoOpInstances` (PR-05).
- **Goal 5** — No-More-Orphans: `bio/package.scala` imports no cats;
  `CatsToBIOConversions` is opt-in via explicit import;
  `OptionalDependencyTest` 8/8 passes verifying Bifunctorized /
  SubmergedTypedError / BifunctorizedNoOpInstances are reachable on a
  no-cats classpath (PR-08).
- **Goal 6** — `Quasi*` deletion: deferred to a user-supervised M5
  session. M3/M4 ship *parallel* BIO surfaces without modifying the
  existing `Lifecycle.scala` / `Injector.scala`; the wholesale
  Quasi*→BIO migration of ~106 call-sites awaits user review.
- **Goal 7** — Cross-build green on Scala 3.7.4, 2.13.18, 2.12.21
  through M1–M4.

## Known limitations

1. **`CatsToBIOConversions` ships only `AsyncToBIO`.** Weaker
   conversions (`SyncToIO2`, `MonadToBIO`, `ErrorToBIO`, etc.) are
   plumbed in the plan §5 [QUESTION] but not implemented. Users with
   a weaker cats-effect typeclass (e.g. only `Sync[F]`) cannot use the
   BIO entry yet. Workaround: provide an `Async[F]` instance if your
   monad has one.
2. **No-op identity covers only the IO2 tier.** Bifunctors with only
   `Error2` (the canonical example: `Either`) do not have a no-op
   instance — `IO2[Bifunctorized.NoOp[Either, ?, ?]]` does not
   resolve. The plan §3.3 sketched mirrors at `Functor2` / `Applicative2`
   / `Monad2` / `Error2` tiers; deferred (PR-05-D05).
3. **`CatsToBIO.shiftBlocking` is passthrough identity.** CE3's
   `Async` typeclass exposes no generic blocking-pool handle
   (`cats.effect.IO.blocking` is IO-specific). Library code using
   `BlockingIO2#shiftBlocking` on a CE-backed Bifunctorized may
   experience thread starvation. Future work: an IO-specific
   specialization.
4. **`BifunctorizedInjector` / `LifecycleBifunctorized` are parallel
   surfaces.** `Lifecycle.scala` and `Injector.scala` are unchanged
   — both Quasi*-constrained and BIO-constrained APIs coexist. M5
   removes the Quasi* path once user-reviewed.
5. **`Subcontext` / `Producer` / strategy interfaces / `LogIO`** are
   not yet migrated to BIO. The current `BifunctorizedInjector`
   bridges to `QuasiIO[F[Throwable, _]]` internally, so the existing
   strategy/Subcontext machinery continues to work — but downstream
   library code that needs to use these directly with a BIO `F`
   still needs to go through `QuasiIO.fromBIO`. Plan's PR-M4-02/03
   migrations folded into the M5 deletion sweep.

## What's the failure mode at the API edge?

If you write `Injector[F]` with a non-Identity `F` that has no
`QuasiIO[F]` (and you didn't switch to `BifunctorizedInjector`), the
compile fails with the usual "no implicit `QuasiIO[F]`" error. Either
switch to `BifunctorizedInjector` (preferred), or add a `QuasiIO[F]`
to scope. The `QuasiIO.fromBIO` derivation in the codebase makes this
automatic if you have a `BIO[F]` typeclass.

If you write `BifunctorizedInjector[F]` with an `F` that has no
`IO2[Bifunctorized.NoOp[F, +_, +_]]`, the compile fails on the
implicit summon. Most modern bifunctors (ZIO, MonixBIO, MiniBIO) and
all `cats.effect.Async`-backed monofunctors-via-Bifunctorized are
supported. Either is currently unsupported (Goal 4 not yet satisfied
for Either; see limitation #2).

## When will M5 ship?

M5 (Quasi* deletion) requires a user-supervised session because it
touches ~106 call-sites across 9 sub-projects. The codemod is
mechanical (`QuasiIO[F]` → `IO2[Bifunctorized.NoOp[F, +_, +_]]` and
`Lifecycle.make[F]` → `LifecycleBifunctorized.make[F]`), but every
test that uses `Injector[Identity]` or `Lifecycle[F]` will need to
adopt the new entry point. The infrastructure for this is in place
from M1–M4; the migration awaits the user's go-ahead.

## References

- Spec: `bifunctorization.md`
- Plan: `docs/drafts/20260513-2106-bifunctorization-plan.md`
- M1 closure summary: `docs/changes/M1-bifunctorized-core.md`
- Defect audit trail: `defects.md`
- Session logs: `docs/logs/`
- Prior art: `docs/drafts/prior-art/{izumi-1766,cats-mtl-619}.patch`
