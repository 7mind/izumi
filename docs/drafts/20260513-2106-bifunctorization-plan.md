# Bifunctorization implementation plan

Replace the `Quasi*` family of compatibility typeclasses with a unified
bifunctor scheme via an opaque-type wrapper `Bifunctorized[F[_], +E, +A]`,
plus CE→BIO conversion typeclasses, plus transparent
bifunctorization/de-bifunctorization at distage / BIO Lifecycle / logstage
entry points. End state: `Quasi*` removed, BIO hierarchy used everywhere, on
all three Scala versions (2.12.21, 2.13.18, 3.7.4).

## 1. Milestones (high-level)

1. **M1 — Bifunctorized core & CE→BIO conversion (Goals 1, 2, 4, 5, 7).** Introduce `Bifunctorized` opaque type, `SubmergedTypedError` (TagK-discriminated), and the full CE→BIO conversion ladder (`MonadToBIO`, `ErrorToBIO`, …, `AsyncToBIO`). Land cats-effect laws suites against the Bifunctorized form for cats.effect.IO, plus laws for ZIO/MiniBIO unchanged.
2. **M2 — Identity special-case & MiniBIO bridge (Goals 3, 4, 7).** Make `Identity` go through `Bifunctorized[Identity, ?, ?] ↔ MiniBIO[Throwable, ?]` (round-trip). Add no-op conversions for already-bifunctor types so `bifunctorize(zio) eq zio`. Add `Bifunctorized.Identity` alias.
3. **M3 — Lifecycle bifunctorization (Goals 3, 6, 7).** Replace `QuasiIO/QuasiPrimitives/QuasiFunctor/QuasiApplicative` constraints on `Lifecycle` combinators with BIO hierarchy on `Bifunctorized[F, Throwable, ?]`. Keep monofunctor public usage ergonomic via implicit `bifunctorize`/`debifunctorize`.
4. **M4 — Distage Injector & LogStage seams (Goals 3, 6, 7).** Switch `Injector.apply`, `Injector.inherit*`, `Subcontext`, `Producer`, `LogIO`, `LogIOModule` to accept `F[+_, +_]: IO2: …`, with monofunctor overloads via `Bifunctorized`. Migrate `distage-core-api` strategies to BIO.
5. **M5 — Remove `Quasi*` (Goals 6, 7).** Codemod-style sweep across all 9 sub-projects that reference `Quasi*`. Delete `quasi/` package, `QuasiIORunner`, `QuasiAsync`, etc., once 0 references remain. Verify `OptionalDependencyTest` still asserts the same shape (test file is itself rewritten in this milestone).
6. **M6 — Documentation, microsite, post-condition gates (Goal 7).** Update microsite docs; remove `Quasi*` mentions; add a "monofunctor-to-bifunctor migration" page; confirm cross-build passes on all three Scala versions and Scala.js.

Each milestone leaves master green via `sbt +Test/compile` and `sbt +test`. Milestone 1 alone moves Goal 1, 2, 5, much of 4; the remaining Goals 3, 6 are completed by M2–M5. Goal 7 is verified continuously and explicitly at the close of every milestone.

## 2. PR breakdown for milestone 1

Milestone 1 is everything in `fundamentals-bio` plus the cats-laws environment. It deliberately stops *before* touching `Lifecycle`/`Injector`/`LogIO` so that the diff is reviewable and so the existing `Quasi*` hierarchy keeps the library shippable should M1 ship alone.

### PR-01 — `Bifunctorized` opaque type & companion

**Scope.** Introduce the type-level wrapper `Bifunctorized[F[_], +E, +A]` and its zero-cost companion machinery: `Bifunctorized.assert`, `bifunctorize`, `debifunctorize`, implicit conversions, and `toMonofunctor` syntax. Pure plumbing, *no* CE typeclass instances yet. Goal 4's no-op identity is implemented here (`eq` preservation), Goal 5 is preserved by keeping `bifunctorize` independent of cats imports. Out of scope: any `MonadToBIO`/`ErrorToBIO`/conversion instances (PR-02), submerge error type (PR-03 details), BIO syntax glue (PR-04).

**File-level changes.**
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala` — new file. Defines `type Bifunctorized[F[_], +E, +A]` (Scala-3 `opaque type`; Scala-2.x cross-built using the same "abstract type + `asInstanceOf` newtype" pattern as the prior-art `CatsToBIO` (lines 154–168 of `izumi-1766.patch`)), plus companion: `assert`, `unwrap`, `bifunctorize`, `debifunctorize`, `bifunctorizeConversion`, `debifunctorizeConversion`, `BifunctorizedSyntax.toMonofunctor`. Companion `ClassTag` shim like prior art `getClassTag` for use sites that need it.
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/package.scala` — re-export `Bifunctorized` at the `izumi.functional.bio` package level (single `type Bifunctorized[F[_], +E, +A] = izumi.functional.bio.Bifunctorized.Bifunctorized[F, E, A]` alias so existing `import izumi.functional.bio.*` users pick it up automatically).
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/BifunctorizedTypeTest.scala` — new tests asserting: identity-eq (`val z: ZIO[Any, Throwable, Int] = ???; bifunctorize(z) eq z` — needs CE/ZIO classpath available — guarded behind a JVM-only test), and the round-trip `debifunctorize(bifunctorize(fa)) eq fa` when the inner `F` is already a bifunctor.

**Success criterion.** `sbt "fundamentals-bioJVM/Test/compile"` and `sbt "fundamentals-bioJVM/Test/testOnly izumi.functional.bio.BifunctorizedTypeTest"` pass on 2.12.21, 2.13.18, 3.7.4. Demonstrates progress toward Goal 4 ("Bifunctorization should be a no-op for real bifunctors, that is, `bifunctorize(f: ZIO[ArbiraryEnv, Throwable, A]) eq f` should hold. There should be no error submerging performed for effect types that already support typed errors.") and Goal 7 ("The project compiles and all tests pass on Scala 2.13, Scala 3 and Scala 2.12.").

**Dependencies.** None (first PR).

**Risks/assumptions.** Variance bookkeeping for `+E, +A` is the main hazard. On Scala 2 the "abstract-type-in-an-object" form has historically *lost* covariance on widening in some inference paths. We assume the safer pattern is to keep `Bifunctorized[F, +E, +A]` *unboxed* (no `extends AnyVal` wrapper, just an abstract type bound to `Any` via `asInstanceOf`) so erasure matches `F[A]` exactly. This is identical to the prior-art `CatsToBIO` choice (`type Bifunctorized[F[_], +E, +A]`, lines 154–157 of `izumi-1766.patch`) — we are not innovating here.

### PR-02 — `SubmergedTypedError`: TagK-discriminated submarine error

**Scope.** Introduce the throwable wrapper used to submerge typed errors into a monofunctor `F[_]`'s Throwable channel, discriminated by `TagK[F]`. Includes catch-only/extractor pattern utilities consumed in PR-04. Out of scope: any instance of `Error2` (PR-04), the no-op path for bifunctors (PR-01 already covers identity, PR-04 covers wiring), and integration with `Exit.Trace` (assumed already covered by `Exit.Trace.ThrowableTrace`).

**File-level changes.**
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/SubmergedTypedError.scala` — new file. Defines:
  ```scala
  final class SubmergedTypedError[F[_]] private[bio] (
    val tag: izumi.reflect.LightTypeTag,  // captured from TagK[F] for cheap equality, not the full TagK
    val payload: Any,
  ) extends RuntimeException(
    s"Submerged typed error of class=${payload.getClass.getName}: $payload",
    payload match { case t: Throwable => t; case _ => null },
    /* enableSuppression */ true, /* writableStackTrace */ false,
  )
  object SubmergedTypedError {
    def apply[F[_]](payload: Any)(implicit tag: izumi.reflect.TagK[F]): SubmergedTypedError[F] =
      payload match {
        case existing: SubmergedTypedError[?] if existing.tag == tag.tag => existing  // idempotent
        case _ => new SubmergedTypedError[F](tag.tag, payload)
      }
    def unapply[F[_]](t: Throwable)(implicit tag: izumi.reflect.TagK[F]): Option[Any] =
      t match {
        case s: SubmergedTypedError[?] if s.tag == tag.tag => Some(s.payload)
        case _ => None
      }
  }
  ```
  `equals`/`hashCode` policy: identity-based (default) — different instances with the same payload are not equal. `getMessage`: as above, includes the runtime class of the payload. `fillInStackTrace`: disabled via `writableStackTrace=false` to keep the per-throw allocation cheap (this is the same trick `cats.mtl.Handle.Submarine` uses via `NoStackTrace`).
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/SubmergedTypedErrorTest.scala` — assert:
  - same-`TagK` round-trip extracts payload,
  - different-`TagK` extraction returns `None` (uses two distinct `F` declarations e.g. `trait A[X]; trait B[X]`),
  - nested `SubmergedTypedError` is collapsed (idempotency, mirrors prior-art `PrivateTypedError` companion at lines 175–180 of `izumi-1766.patch`),
  - `SubmergedTypedError` is not a `Throwable`-only — payload may be `Any`.

**Success criterion.** `sbt "fundamentals-bioJVM/Test/testOnly izumi.functional.bio.SubmergedTypedErrorTest"` and `+fundamentals-bioJVM/Test/compile` pass. Demonstrates Goal 2 ("Bifunctorized Submerged errors are discriminated by TagK[F] of its monofunctor. Terminates/defects use monofunctor's raw Throwable") in isolation.

**Dependencies.** PR-01 must land (uses package-level access to `bio` package).

**Risks/assumptions.** The cats-mtl prior art uses per-`allow`-region `Marker = new AnyRef`; we deliberately reject that. The lurking risk is that two *different* opaque types `Bifunctorized[F, ...]` and `Bifunctorized[G, ...]` end up sharing `TagK` if `F` and `G` happen to be the same monofunctor type (e.g. two distinct `F[_]` aliases that resolve to `cats.effect.IO`) — this is *intentional*: handlers for the same monofunctor effect must compose. We assume `izumi.reflect.LightTypeTag` equality is the right discriminator (cheap to compare, cached, already on the BIO classpath). We do *not* assume `TagK` instance identity — only its `.tag: LightTypeTag` value.

### PR-03 — `Exit.Trace.SubmergedTrace` & integration with `Exit`

**Scope.** Add a small bridge in `Exit.scala` so that converting a `SubmergedTypedError[F]` into an `Exit.Error[E]` carries a sensible trace (akin to prior-art `Exit.Trace.CatsTrace` at `izumi-1766.patch` lines 119–129, but renamed and generalized). Out of scope: instances using the trace (PR-04 onwards).

**File-level changes.**
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Exit.scala` — Reuse the existing `Exit.Trace.ThrowableTrace` for the wrapper; no new trace subtype required (the prior art's `CatsTrace` was later renamed to `ThrowableTrace` in the same diff at lines 491–501, which is already merged in the current tree). The only edit is a documentation note on `Exit.Trace.ThrowableTrace` clarifying that it covers `SubmergedTypedError`. If a structured trace turns out to be needed (e.g. to discriminate "this trace came from a submerged error" without unwrapping the exception), add a `Exit.Trace.SubmergedTrace[E](payload: E, throwable: SubmergedTypedError[?])` — leave the decision to PR-04 author; default: do not add a new trace type.
- [ ] No new test file; PR-04 will cover this transitively.

**Success criterion.** `sbt "+fundamentals-bio/Test/compile"`. No new test class needed in isolation.

**Dependencies.** PR-02.

**Risks/assumptions.** Risk of bloating `Exit.Trace` with a near-duplicate of `ThrowableTrace`. Mitigation: don't, unless PR-04 proves a structural need.

### PR-04 — CE→BIO conversion typeclasses (the core of M1)

**Scope.** Port the prior-art `CatsToBIO.asyncToBIO[F]` (and its weaker siblings) into the izumi tree, but renamed/restructured to the conversion ladder mandated by the spec: `MonadToBIO`, `ErrorToBIO`, `BracketToBIO`, `PanicToBIO`, `IOToBIO`, `WeakAsyncToBIO`, `AsyncToBIO`, plus `BlockingIOToBIO`, `TemporalToBIO`, `ParallelToBIO`, `Primitives2ToBIO`, `Fork2ToBIO`. Each produces a `<TypeClass>2[Bifunctorized[F, +_, +_]]` from a cats-effect typeclass on `F`. Submerging happens in `fail`/`catchAll`/`catchSome`/`leftFlatMap`/`sandbox`/`redeem`/`attempt`/`tapError` — i.e. anywhere a typed error crosses the bifunctor seam. `sync`/`syncThrowable` do *not* submerge (defects stay raw, per Goal 2). Out of scope: any wiring at distage / Lifecycle seams (M3+). Out of scope: the no-op bifunctor instances (PR-05).

**File-level changes.**
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/impl/CatsToBIO.scala` — new file. Mostly a polished, completed version of prior-art `izumi-1766.patch` `impl/CatsToBIO.scala`. Key differences from the patch:
  - `PrivateTypedError` is replaced by `SubmergedTypedError[F]` from PR-02; `TagK[F]` is required as an implicit on each `<X>ToBIO[F]` factory method (this is the load-bearing departure from cats-mtl).
  - Hand-written stubs for `mkRef`/`mkPromise`/`mkSemaphore` (`???` in the prior-art) are implemented by delegating to `cats.effect.kernel.{Ref, Deferred, Semaphore}` via the existing `BIOCats*` patterns in `CatsConversions.scala` (mirror, in inverse direction).
  - `race` is implemented in terms of `racePairUnsafe` rather than `???`.
  - `shiftBlocking` delegates to `cats.effect.Async#evalOn` on the cats `executionContext`.
  - `fromFutureJava` is implemented via `cats.effect.kernel.Async#fromCompletableFuture`-style adapter (no `???`).
  - `outcomeToExit` unchanged from prior-art lines 199–204 (uses `Exit.Trace.ThrowableTrace`).
  - The factory exports an *intersection* return type (mirroring prior-art `def asyncToBIO`: `Async2 & Temporal2 & Fork2 & BlockingIO2 & Primitives2`). This keeps a single instance covering most surface area.
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/CatsToBIOConversions.scala` — new file. The implicit-discovery layer paralleling `CatsConversions` (BIO→CE) but in the *opposite* direction. Defines the ladder `MonadToBIO`/`ErrorToBIO`/`BracketToBIO`/`PanicToBIO`/`IOToBIO`/`WeakAsyncToBIO`/`AsyncToBIO`/`TemporalToBIO`/`ParallelToBIO`/`Primitives2ToBIO`/`Fork2ToBIO` as `Predefined.Of[...]` low-priority instances, using `cats.Monad`, `cats.ApplicativeError`, `cats.effect.kernel.MonadCancel`, `cats.effect.kernel.Sync`, `cats.effect.kernel.Spawn`, `cats.effect.kernel.GenConcurrent`, `cats.effect.kernel.Async` — *all* gated behind the no-more-orphans `cats.*.kernel.*` type providers from `OrphanDefs.scala`. The factory in `impl/CatsToBIO.scala` is the implementation; this file is just the implicit landing pad.
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/CatsToBIOTest.scala` — small unit tests proving:
  - `fail(e: E)` followed by `catchAll(_ => pure(0))` returns `pure(0)`,
  - `fail(e: E)` un-caught propagates through `unwrap` as `SubmergedTypedError[F]`,
  - `terminate(t: Throwable)` un-caught propagates through `unwrap` as `t` (raw, *not* submerged) — this is Goal 2's defect rule.

**Success criterion.** `sbt "+fundamentals-bioJVM/Test/testOnly izumi.functional.bio.CatsToBIOTest"`. Demonstrates Goal 2 ("Bifunctorized Submerged errors are discriminated by TagK[F] of its monofunctor. Terminates/defects use monofunctor's raw Throwable") end-to-end, and is the necessary precondition for Goal 1 ("Bifunctorized effect types must pass cats laws suites using CatsConversions instances for their Bifunctorized forms. That is, a CE->BIO->CE conversion must come at no loss of correctness with respect to cats effect laws.").

**Dependencies.** PR-01, PR-02, PR-03.

**Risks/assumptions.** Two notable risks:
1. **Implicit search cycles.** `CatsConversions` (BIO→CE) is currently `@inline implicit final def`-based; adding the inverse `CatsToBIOConversions` (CE→BIO) creates a roundtrip risk: an `F[+_, +_]: IO2` can be summoned to `cats.effect.Async[F[Throwable, _]]`, which can be summoned back to `IO2[Bifunctorized[F[Throwable, _], …]]`. We block this with the `Predefined.Of[…]` priority pattern already used in `Root.scala` (lines 18–112) — every `<X>ToBIO` instance is marked `NotPredefined` so it's only used when a `Predefined` BIO instance isn't already available.
2. **`PrimitivesFromBIOAndCats` already exists.** Inspect `impl/PrimitivesFromBIOAndCats.scala` and `impl/PrimitivesLocalFromCatsIO.scala` — these are partial CE→BIO derivations already shipping in master. Our PR-04 must not collide with them; expected outcome is that PR-04 subsumes them and the duplicates are deprecated/removed in a later PR within M1 (PR-06 below).

### PR-05 — No-op identity instances for actual bifunctors

**Scope.** Provide the highest-priority instances such that when `F[+_, +_]` is *already* a bifunctor with an `IO2[F]` instance (e.g. `zio.ZIO[Any, +_, +_]`, `MonixBIO`, `Either`), `Bifunctorized[F[E, _], E, A]` is treated as `F[E, A]` directly with zero submerging. This makes `bifunctorize(zio) eq zio` hold (Goal 4). Out of scope: the `Identity` special case (PR-07 / M2).

**File-level changes.**
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/BifunctorizedNoOpInstances.scala` — new file. Provides:
  ```scala
  trait BifunctorizedNoOpInstances {
    @inline implicit final def bifunctorIsAlreadyBifunctor[F[+_, +_]](implicit F: IO2[F]): Predefined.Of[IO2[Bifunctorized.NoOp[F, +_, +_]]] = ???
    // …Functor2, Applicative2, Monad2, Error2, …, Async2 mirrors
  }
  ```
  where `Bifunctorized.NoOp[F[+_, +_], +E, +A]` is an alias `Bifunctorized[F[E, *], E, A]` — but the implicit instance trusts the user's `F` and *skips submerging*. Concretely, `fail` is `F.fail` (typed), not `F.terminate(SubmergedTypedError(...))`. Identity in the type sense (`eq`) comes for free because the wrapper is unboxed.
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/BifunctorizedNoOpTest.scala` — assert `bifunctorize(zio: ZIO[Any, Throwable, Int]) eq zio.asInstanceOf[AnyRef]` and verify no `SubmergedTypedError` is thrown on `fail`/`catchAll` round-trips when `F` is `ZIO`.

**Success criterion.** `sbt "+fundamentals-bioJVM/Test/testOnly izumi.functional.bio.BifunctorizedNoOpTest"`. Demonstrates Goal 4 ("Bifunctorization should be a no-op for real bifunctors, that is, `bifunctorize(f: ZIO[ArbiraryEnv, Throwable, A]) eq f` should hold. There should be no error submerging performed for effect types that already support typed errors.").

**Dependencies.** PR-01, PR-04.

**Risks/assumptions.** Implicit priority is the only sharp edge here. We follow the existing `Root.scala` `RootInstancesLowPriority1..N` ladder: `BifunctorizedNoOpInstances` is *higher* priority than `CatsToBIOConversions` (PR-04). Both lower-priority than predefined `IO2[ZIO]`/`IO2[MiniBIO]`/`Error2[Either]`. Open Question: should the no-op factory be hidden behind a `[QUESTION] BifunctorizedIsNoOp[F]` marker trait (analogous to `NotPredefined`) so users can't accidentally summon a CE-based instance for ZIO? Defaults: yes, gated.

### PR-06 — Deprecation of `PrimitivesFromBIOAndCats` & `PrimitivesLocalFromCatsIO`

**Scope.** Mark the two existing partial CE→BIO derivations `@deprecated`, forwarding to the new ladder from PR-04. Drop test references that exercise them as standalone constructors. Out of scope: outright deletion (deferred to M5 along with `Quasi*`).

**File-level changes.**
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/impl/PrimitivesFromBIOAndCats.scala` — add `@deprecated("Use izumi.functional.bio.impl.CatsToBIO for full CE→BIO derivations", "1.3.0")` to the public class.
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/impl/PrimitivesLocalFromCatsIO.scala` — same.
- [ ] `/home/kai/src/izumi/distage/distage-extension-config/.jvm/src/test/scala/izumi/distage/impl/OptionalDependencyTest.scala` — update the two `new PrimitivesFromBIOAndCats()(using null, null).discard()` / `new PrimitivesLocalFromCatsIO(...)` sites to `@nowarn("cat=deprecation")` so the test still asserts the No-More-Orphans property without failing on deprecation.

**Success criterion.** `sbt "+distage-extension-config/Test/testOnly izumi.distage.impl.OptionalDependencyTest"`. Goal 5 ("No More Orphans trick keeps working, users are not forced to have cats on their classpath, test distage/distage-extension-config/.jvm/src/test/scala/izumi/distage/impl/OptionalDependencyTest.scala keeps passing.") is preserved.

**Dependencies.** PR-04.

**Risks/assumptions.** Risk: deprecating things shipped at 1.3.0-SNAPSHOT before any release. Acceptable — they're internal `impl/` classes.

### PR-07 — Cats laws environment for `Bifunctorized[cats.effect.IO]`

**Scope.** Add the cats-effect `AsyncTests` law suite against `Bifunctorized[cats.effect.IO, Throwable, _]`, using `CatsConversions.BIOToAsync` (BIO→CE) composed with `CatsToBIO.asyncToBIO` (CE→BIO). This is the Goal 1 acceptance test. Out of scope: extending laws to ZIO/MiniBIO (those keep their existing tests unchanged).

**File-level changes.**
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/.jvm/src/test/scala/izumi/functional/bio/laws/CatsLawsTest.scala` — new file, ported from prior-art `izumi-1766.patch` lines 17–44. Adjust to the renamed `Bifunctorized` (no longer under `CatsToBIO.Bifunctorized`).
- [ ] `/home/kai/src/izumi/fundamentals/fundamentals-bio/.jvm/src/test/scala/izumi/functional/bio/laws/env/CatsTestEnv.scala` — new file, ported from prior-art lines 50–108. Wires `Arbitrary[Bifunctorized[IO, Throwable, A]]`, `Cogen`, `Eq`, `Order` over `Bifunctorized`. The commented-out `clock2` block in prior art remains commented out — `cats.effect` brings its own `Clock`/`Ticker` instance.

**Success criterion.** `sbt "+fundamentals-bioJVM/Test/testOnly izumi.functional.bio.laws.CatsLawsTest"`. Goal 1 quoted verbatim is the spec: "Bifunctorized effect types must pass cats laws suites using CatsConversions instances for their Bifunctorized forms. That is, a CE->BIO->CE conversion must come at no loss of correctness with respect to cats effect laws."

**Dependencies.** PR-04 (must exist), PR-05 (no-op path mustn't intercept), PR-06 (deprecated paths still resolve).

**Risks/assumptions.** Two laws are known to be subtle:
- **`evalOn local pure`** — ZIO doesn't satisfy it; the laws environment uses `Eq.allEqual` for `ExecutionContext` to paper over (see `ZIOTestEnv.scala` line 27). Cats.effect.IO does satisfy it; we don't need the workaround for the IO-based test. Confirm no regression on the ZIO laws test — Goal 1 doesn't add ZIO laws, the existing tests must keep passing.
- **Cancellation semantics** — `CatsToBIO` maps `Outcome.Canceled` to `Exit.Interruption(Nil, Exit.Trace.forUnknownError)` (matches prior-art line 532). Re-mapping back via `BIOToAsync` must yield `Outcome.Canceled` again. Verify via the `AsyncTests` `bracketRelease` family.

### PR-08 — Test scaffolding: assert `Bifunctorized[F, _, _]` resolution doesn't pull cats

**Scope.** Add a test in `OptionalDependencyTest` style proving the `Bifunctorized` opaque type and the no-op identity path do *not* require `cats.*` on classpath. Goal 5 protection.

**File-level changes.**
- [ ] `/home/kai/src/izumi/distage/distage-extension-config/.jvm/src/test/scala/izumi/distage/impl/OptionalDependencyTest.scala` — append a new `in` block: "Bifunctorized resolution does not require cats on classpath." Inside it, reuse the existing pattern:
  ```scala
  And("Can construct Bifunctorized without cats")
  trait SomeF[+E, +A]
  val _ = izumi.functional.bio.Bifunctorized   // forces companion load
  ```
  And `assertDoesNotCompile("CatsToBIOConversions.AsyncToBIO[SomeF[Throwable, *]]")` (because `Async[SomeF]` doesn't exist), but the *use* of the conversion type doesn't drag cats onto the public classpath when the user doesn't ask for it.

**Success criterion.** `sbt "+distage-extension-config/Test/testOnly izumi.distage.impl.OptionalDependencyTest"`. Goal 5 ("No More Orphans trick keeps working, users are not forced to have cats on their classpath, test distage/distage-extension-config/.jvm/src/test/scala/izumi/distage/impl/OptionalDependencyTest.scala keeps passing.") protected against M1 regressions.

**Dependencies.** PR-01 through PR-06.

**Risks/assumptions.** The test runs in a Scala-2/3 cross-build subset where `cats-*` is intentionally absent from the test compile classpath. Adding *any* `import cats.*` in the new path turns the test red. The class to be added must avoid such imports.

### Milestone-1 closing PR — PR-09 — Cross-Scala compile lock

**Scope.** A no-source-changes PR whose CI matrix runs `+test` on 2.12.21, 2.13.18, 3.7.4, plus a `scalafmt` and `scalafix` pass over the new files. If it goes red, M1 doesn't merge.

**File-level changes.** None functional. Optional: a `bifunctorization/M1.md` micro-changelog at `/home/kai/src/izumi/docs/changes/M1-bifunctorized-core.md`.

**Success criterion.** `sbt clean +Test/compile +test` green on all three Scala versions. Goal 7 ("The project compiles and all tests pass on Scala 2.13, Scala 3 and Scala 2.12.") attested per milestone.

**Dependencies.** PR-01..PR-08.

**Risks/assumptions.** Scala 2.12 implicit search regressions are the historical pain point. The prior-art patch is Scala-2.13/3 only — no 2.12 evidence. Allow time for 2.12-only fixes (likely additional `Predefined.Of` wrappers, occasional explicit `using` parameters).

## Later milestones (one-line scopes)

### Milestone 2 — Identity special-case + MiniBIO bridge

- **PR-M2-01.** Define `Bifunctorized.IdentityBifunctorized[+E, +A] = Bifunctorized[Identity, E, A]` alias and an instance `IO2[Bifunctorized.IdentityBifunctorized]` that internally evaluates via `MiniBIO`'s interpreter (the conversion path is `Identity → MiniBIO[Throwable, A] → Bifunctorized[Identity, Throwable, A] → back`).
- **PR-M2-02.** Add `Bifunctorized.toMiniBIO` and `Bifunctorized.fromMiniBIO` syntax for `Identity`-rooted Bifunctorized values.
- **PR-M2-03.** Tests proving (a) `Bifunctorized[Identity, Throwable, A]` is law-abiding as a `MonadError` (cats-laws), and (b) Identity-special-case is implicit-resolved transparently in distage entry points (sketched against `Injector.apply()` no-args).
- **PR-M2-04.** Cross-Scala compile lock.

### Milestone 3 — Lifecycle bifunctorization

- **PR-M3-01.** New file `LifecycleBifunctorized.scala` providing `Lifecycle.bifunctorize`/`Lifecycle.debifunctorize` plus equivalents of `make`, `makePair`, `liftF`, `pure`, `suspend`, `flatMap`, `map`, `catchAll`, `evalMap`, `evalTap`, `wrapAcquire`, etc., constrained on the BIO hierarchy over `Bifunctorized[F, Throwable, _]`. Out of scope: deleting the `QuasiIO`-constrained originals — they're kept and forwarded.
- **PR-M3-02.** Switch all `def …[G[x] >: F[x]: QuasiX]` definitions in `Lifecycle.scala` (lines 239–272 etc.) to use the BIO variant where `QuasiX` is removable; keep `QuasiX` versions as deprecated forwarders.
- **PR-M3-03.** Migrate `LifecycleMethodImpls`, `LifecycleAggregator` over.
- **PR-M3-04.** Cross-Scala compile lock.

### Milestone 4 — Distage Injector + LogStage seams

- **PR-M4-01.** `Injector.apply[F[+_, +_]: IO2: TagKK: DefaultModule](...)` becomes the primary signature; add a `Bifunctorized`-mediated overload `apply[F[_]: TagK: DefaultModule](...)(implicit F: IO2[Bifunctorized[F, +_, +_]]): Injector[Bifunctorized[F, ?, ?]]`. Same treatment for `inherit`, `inheritWithNewDefaultModule`, `providedKeys`.
- **PR-M4-02.** `Subcontext`, `Producer`, `OperationExecutor`, `PlanInterpreter` and the five `…Strategy` interfaces in `distage-core-api` swapped from `QuasiIO[F]` to `IO2[F]: TagKK`. Their implementations in `distage-core` follow.
- **PR-M4-03.** `LogIO`, `LogIOModule`, `LogIO2Module`, `LogIO3Module` migrated to BIO-based signatures with a `Bifunctorized` overload at the constructor seam.
- **PR-M4-04.** Cross-Scala compile lock.

### Milestone 5 — Remove `Quasi*`

- **PR-M5-01.** Codemod: replace all remaining call sites — `QuasiIO[F]` → `IO2[Bifunctorized[F, +_, +_]]`, etc. Across all 106 files identified by `git grep`. Strategy: deprecate-and-forward in M3/M4, then delete in M5 — no big-bang.
- **PR-M5-02.** Delete `quasi/` package, `QuasiIORunner`, `QuasiAsync`, `QuasiIO`, `LowPriorityQuasiIORunnerInstances`, `__QuasiAsyncPlatformSpecific` (JVM + JS variants).
- **PR-M5-03.** `OptionalDependencyTest` updated to reflect the new shape: every reference to `QuasiIO`, `QuasiFunctor`, `QuasiApplicative`, `QuasiPrimitives`, `QuasiIORunner` is replaced by its BIO/Bifunctorized equivalent, but the test's *intent* (no-cats classpath) is preserved.
- **PR-M5-04.** Cross-Scala compile lock + microsite generation.

### Milestone 6 — Documentation

- **PR-M6-01.** Update `bio/media/bio-hierarchy.svg` and the long Scaladoc in `bio/package.scala` to include `Bifunctorized` as an entry point.
- **PR-M6-02.** Migration guide at `/home/kai/src/izumi/docs/manuals/bifunctorization-migration.md`.
- **PR-M6-03.** Release notes.

## 3. Cross-cutting architectural decisions (locked)

### 3.1 Representation of `Bifunctorized[F[_], +E, +A]`

**Decision.** Abstract type member in an object (`type Bifunctorized[F[_], +E, +A]` defined inside `object Bifunctorized` and inside-erased to `Any` via `asInstanceOf`). No `extends AnyVal`, no Scala-3 `opaque type` on Scala 3 (to keep cross-build symmetric). The same approach as prior-art `izumi-1766.patch` line 155.

**Rationale.** (1) Variance is straightforward — the abstract type carries `+E, +A` directly. (2) Erasure: `Bifunctorized[F, E, A]` erases to `Object`, identical to `F[A]`'s erasure when `F[_]` itself erases to `Object`, so identity-eq (Goal 4) is preserved automatically. (3) Cross-build: the same syntax works on 2.12/2.13/3 without source-level forks. (4) Allocation: zero (no wrapper class). The trade-off rejected: Scala-3 `opaque type` would be marginally safer (prevents accidental `asInstanceOf` outside the companion), but forces a 2.x shim with a different access pattern, which we judge more burdensome than its safety upside.

### 3.2 Submerge discriminator type

**Decision.**
```scala
final class SubmergedTypedError[F[_]] private[bio] (
  val tag: izumi.reflect.LightTypeTag,
  val payload: Any,
) extends RuntimeException(
  s"Submerged typed error of class=${payload.getClass.getName}: $payload",
  payload match { case t: Throwable => t; case _ => null },
  /* enableSuppression = */ true,
  /* writableStackTrace = */ false,
)
```
- Construction: `SubmergedTypedError[F](payload)(implicit tag: TagK[F])` (idempotent: if `payload` is already a `SubmergedTypedError[F]` *with the same tag*, return it as-is).
- Discrimination: by `LightTypeTag` equality (cheap, cached, already on classpath).
- `equals`/`hashCode`: identity-based (RuntimeException default). Two instances with the same payload are *not* equal, which is the right call for exceptions (they may have distinct stack traces).
- `getMessage`: includes payload class for debuggability. Cause-chained to payload if payload `<: Throwable`.
- `fillInStackTrace`: disabled (writableStackTrace=false). Same cost-saving trick as `cats.mtl.Handle.Submarine`'s `NoStackTrace`.
- `catchAll[E]` discrimination (in `CatsToBIO`):
  ```scala
  F.recoverWith(r.unwrap) {
    case SubmergedTypedError(payload) => f(payload.asInstanceOf[E]).unwrap
    // un-matched Throwables propagate as defects/Termination
  }
  ```
- `catchSome` is implemented as `catchAll` with a `PartialFunction.applyOrElse` over the recovered payload.
- `leftFlatMap` is implemented in terms of `redeem` ↑ `flatMap` ↑ `fail`, no new discriminator path needed.

**Rationale.** This is the *load-bearing departure from cats-mtl-619.patch*. cats-mtl uses a per-region `Marker = new AnyRef` so each `Handle.allow` creates a distinct discriminator (algebraic-effects-region semantics). The izumi spec rejects that explicitly — we want handlers for the same monofunctor `F` to compose, but handlers for *different* `F`s to be mutually opaque. `TagK[F]` is the natural carrier of "what monofunctor did this error originate from", and `LightTypeTag` is the right equality value (already used throughout izumi-reflect, cheap, structural).

### 3.3 No-op for actual bifunctors

**Decision.** Two-instance ladder, mediated by `Predefined.Of[…]`:
- **High priority** (in `BifunctorizedNoOpInstances`): `bifunctorIsAlreadyBifunctor[F[+_, +_]](implicit F: IO2[F]): Predefined.Of[IO2[Bifunctorized[F[ε, _], +_, +_]]]` — uses `F` directly, no submerging.
- **Low priority** (in `CatsToBIOConversions`): `AsyncToBIO[F[_]](implicit F: cats.effect.Async[F], tag: TagK[F]): NotPredefined.Of[Async2[Bifunctorized[F, +_, +_]]]` — does submerge.

Type-level identity: when `F` is already a bifunctor, `Bifunctorized[F[E, _], E, A] =:= F[E, A]` holds at the *runtime* level (both erase to `Object`), but *not* at the source-type level — there's no `=:=` exposed publicly. The user-facing guarantee is `bifunctorize(f) eq f` for any actual bifunctor `f`, which is sufficient for Goal 4.

**Rationale.** Replicates the proven `Root.scala` `RootInstancesLowPriority1..10` pattern. The `Predefined`/`NotPredefined` marker traits are precisely the tool to break ambiguity between two competing typeclass instances at differing priorities.

### 3.4 Identity special-case

**Decision.** `Identity` goes through a `MiniBIO[Throwable, _]`-based interpreter. The conversion path is:
- `Identity[A] → MiniBIO[Throwable, A]`: wrap in `MiniBIO.Sync(() => Success(a))`, catching any thrown exception into `Termination`.
- `MiniBIO[Throwable, A] → Identity[A]`: run via `MiniBIO.autoRun.autoRunAlways` (rethrows on failure).
- `Bifunctorized[Identity, +_, +_]`: an `IO2`-class instance whose underlying carrier is `MiniBIO[Throwable, _]`. Effectively `Bifunctorized.Identity` is a type alias for `Bifunctorized[Identity, Throwable, _]`, and `IO2[Bifunctorized.Identity]` delegates to `MiniBIO`'s `IO2` instance.

Mechanism: a dedicated high-priority implicit instance in `BifunctorizedNoOpInstances` for `F = Identity` (placed *above* the cats-effect-mediated path so cats-effect's `Sync[Identity]` (if present) doesn't intercept).

**Rationale.** Goal 3 requires this exact path: "Identity is special-cased and goes through a bifunctorization/debifunctorization cycle to MiniBIO and back, transparently to the user." Using MiniBIO over Identity gives Identity *lawful* monadic behavior (with suspension), unlike `QuasiIOIdentity` which is unlawful (no actual suspension). The reverse direction (Identity → user-visible) is via `debifunctorize` which runs MiniBIO synchronously.

### 3.5 Implicit-search surface

**Decision.**
- `Bifunctorized` companion exposes `bifunctorize` / `debifunctorize` as methods (callable explicitly), and `bifunctorizeConversion` / `debifunctorizeConversion` as implicit conversions (auto-applied at expected-type sites).
- `BifunctorizedSyntax(.toMonofunctor)` provides the dotted syntax `value.toMonofunctor`. Lives in the companion object so it's imported alongside the type.
- BIO syntax (`flatMap`, `map`, `catchAll`, …) on `Bifunctorized[F, E, A]` flows through the existing `Syntax2` machinery automatically — *because* the implicit `IO2[Bifunctorized[F, +_, +_]]` from PR-04/PR-05 is available, the existing `Syntax2.ImplicitPuns` pick it up. No new syntax file required for BIO ops on Bifunctorized.
- CE→BIO ladder (PR-04) lives in `bio/CatsToBIOConversions.scala`. It is *not* mixed into the `bio` package object — users opt in with `import izumi.functional.bio.CatsToBIOConversions.*` or `import izumi.functional.bio.catz_to_bio.*` (analogous to existing `catz`). Rationale: keeping it out of the auto-imported set protects Goal 5 — the package object stays cats-import-free.
- Priorities, top to bottom (most specific first): predefined `IO2[ZIO]`, predefined `IO2[MiniBIO]`, predefined `Error2[Either]`, predefined `Monad2[Identity2]`, no-op bifunctor identity, CE→BIO conversion ladder, fallback (none — fail to find).

**Rationale.** The recurring BIO pain point is implicit-search cycles between BIO instances and CE instances (`Async2[F]` → `cats.Async[F[Throwable, _]]` → `Async2[F]`). The `Predefined`/`NotPredefined` markers in `PredefinedHelper.scala` already break the obvious cycles for BIO→CE; the symmetric `NotPredefined.Of` constraint in PR-04 closes the new cycle CE→BIO→CE. Manual imports for `CatsToBIOConversions` (versus pun-imports for `IO2`) keep the search surface small.

### 3.6 Package layout

**Decision.**
- `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala` — opaque type & companion (PR-01).
- `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/SubmergedTypedError.scala` — discriminator (PR-02).
- `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/BifunctorizedNoOpInstances.scala` — high-priority no-op identity (PR-05).
- `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/CatsToBIOConversions.scala` — CE→BIO implicit ladder (PR-04).
- `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/impl/CatsToBIO.scala` — implementation of the ladder (PR-04, ported from prior art).

The package object `bio/package.scala` *does not* import any `cats.*`. The opaque type is exported there only via `type Bifunctorized = …` alias. All cats-effect-touching code stays in `CatsToBIOConversions.scala` and `impl/CatsToBIO.scala`, which depend on cats-effect transitively *only when imported by the user*. `OptionalDependencyTest` is traced in PR-08 to verify the no-cats classpath build still resolves `Bifunctorized` and `SubmergedTypedError`.

**Rationale.** Goal 5 is structural — cats imports must stay out of the public-import path. The package-layout split mirrors the existing one (`catz.scala` is opt-in, `CatsConversions` is not in the package object).

## 4. Risks and assumptions

1. **CE laws subtleties around defects vs typed errors.** After submerging, a `fail(e: E)` becomes `F.raiseError(SubmergedTypedError(e))`. A user-written `cats.handleErrorWith` on `Bifunctorized[F, Throwable, A]` *via* `BIOToMonadError` will catch the submerged error — exactly the law-abiding behaviour. But a user-written `F.handleErrorWith` directly on the underlying CE form (escaped via `unwrap` / `toMonofunctor`) will also catch it, exposing the submerged shape. This is acceptable per the spec ("the Throwable error must be Submerged, converted into a typed error during bifunctorize") but should be documented in M6.
2. **Variance on Scala 2.12.** The opaque-via-abstract-type pattern can confuse 2.12's inference for `Bifunctorized[F, +E, +A]` when the value site widens `A`. Mitigation: add a `widen` helper in the companion (`def widen[F[_], E, A1, A2 >: A1](b: Bifunctorized[F, E, A1]): Bifunctorized[F, E, A2] = b`). If 2.12 still misbehaves, demote to `Bifunctorized[F, E, A]` invariant in `E, A` and rely on `Functor2#widen` for upcasts — a measurable usability cost but recoverable.
3. **Implicit-resolution cycles.** `CatsConversions.BIOToAsync` plus `CatsToBIOConversions.AsyncToBIO` create a potential round-trip if both sides resolve unconditionally. Mitigation: the `NotPredefined.Of` marker on the new direction (PR-04) plus the existing `Predefined.Of` discipline on `Root.scala`. Verification: `sbt "+fundamentals-bioJVM/Test/testOnly izumi.functional.bio.CatsToBIOTest"` runs an implicit-summon torture test ("can I summon `IO2[Bifunctorized[ZIO[Any, *, *], +_, +_]]`? It must resolve to the no-op identity, not to a CE-mediated path").
4. **No-More-Orphans regression.** Adding `import cats.…` to any file reachable from the public package object kills `OptionalDependencyTest`. Mitigation: PR-04 lives in a separate file (`CatsToBIOConversions.scala`) that's *not* aggregated into the package object; PR-08 adds an explicit assertion that `Bifunctorized` is reachable on a no-cats classpath.
5. **Performance — per-throw allocation.** `SubmergedTypedError` is allocated on every `fail` of a Bifunctorized monofunctor. We mitigate by `writableStackTrace=false` (skips the stack capture, which is ~80% of `Throwable` construction cost). Realistic budget: ~150 ns per fail on JDK 21, dominated by the `cause` field assignment. For ZIO/MonixBIO this cost is zero (no-op identity path). Documented expectation: do not use a Bifunctorized-`cats.effect.IO` for hot-path error handling; use ZIO instead.
6. **Identity special-case correctness.** Today's `QuasiIOIdentity` is *unlawful* — `maybeSuspend` doesn't actually suspend. Tests that rely on the unlawful behavior (e.g. side-effecting in a `pure` block) may break under the MiniBIO route. Mitigation: M2 includes a focused test that exercises the cats-laws `Monad`/`MonadError` suite over `Bifunctorized[Identity, Throwable, _]`, which forces lawful behavior. Suspected callers in `distage-testkit` may need a one-line tweak to wrap side-effects in `F.maybeSuspend`/`F.sync`.
7. **Scaladoc / macro interactions.** `TagK[F]` is macro-derived; if `F` is itself the opaque alias `Bifunctorized[G, *, *]`, `TagK` derivation must still succeed. Verification: a new test summoning `TagK[Bifunctorized[cats.effect.IO, *, *]]` (Goal 7 sub-clause). We do not anticipate a problem because `Bifunctorized` is erased to `Any`, and izumi-reflect resolves to the underlying type-tag via its own `LightTypeTag` machinery, but it's worth a smoke test.
8. **Quasi* removal blast radius.** Inventory (from `git grep`): 106 source files reference `Quasi*` across 9 sub-modules. Strategy: deprecate-then-delete, not big-bang. M3 and M4 add the BIO-based replacements and mark the `Quasi*` constraints `@deprecated`. M5 codemods call sites (search-and-replace, one sub-module at a time, in dependency order: `fundamentals-bio` → `distage-core-api` → `distage-core` → `distage-framework` → `distage-framework-docker` → `distage-extension-config` → `distage-testkit-core` → `distage-testkit-scalatest` → `logstage-core`). Final PR deletes `quasi/` package.

## 5. Open questions

- **[QUESTION]** Should `Bifunctorized.Identity` be a top-level alias `type IdentityBifunctorized[+E, +A] = Bifunctorized[Identity, E, A]`, or only available via `Bifunctorized.IdentityBifunctorized`? Top-level is more ergonomic but pollutes `bio` namespace. **Default: top-level alias for parity with `Identity2`.**
- **[QUESTION]** When `bifunctorize` is invoked on an `F[A]` that already happens to be a `SubmergedTypedError[F]`'s causal output (very rare in practice), should it idempotently no-op-resubmerge? **Default: yes, the `SubmergedTypedError.apply` method already handles this via the pattern match in its companion.**
- **[QUESTION]** The cats-effect `Async[F]#cont` is implemented via `defaultCont` in `CatsConversions.BIOCatsAsync`. The reverse direction (`AsyncToBIO`) must also implement an equivalent — the prior art stubbed `cont` (not visible in the patch). Is `defaultCont` available on `Bifunctorized`'s derived `Async` instance, or do we need a hand-rolled `cont`? **Default: use `defaultCont` and revisit if laws fail.**
- **[QUESTION]** Should the `Bifunctorized` overload at `Injector.apply` (M4) take `TagK[F]` (the monofunctor's tag, used to select the submerge discriminator) *or* `TagKK[Bifunctorized[F, *, *]]` (the bifunctor's tag, used to resolve type-class instances internally)? The two are derivable from each other, but on Scala 2.12 derivation may be flaky. **Default: take both, the second derived from the first via the abstract-type identity.**
- **[QUESTION]** For `cats.effect.Sync[F]`-only effect types (no `Async`), does the spec want `Bifunctorized[F, +_, +_]` to expose `Async2`? The prior art only sketched `Async2`; the spec says "we provide conversion typeclasses from Cats Effect to BIO, of form … MonadToBIO …" — implies the full ladder. **Default: full ladder (PR-04 plan).**

## 6. Verification matrix

| Spec goal | PR(s) demonstrating it | Command / test name |
|-----------|------------------------|---------------------|
| 1. Bifunctorized passes cats laws (CE→BIO→CE no loss) | PR-04, PR-07 | `sbt "+fundamentals-bioJVM/Test/testOnly izumi.functional.bio.laws.CatsLawsTest"` |
| 2. Submerged errors discriminated by TagK[F]; terminates use raw Throwable | PR-02, PR-04 | `sbt "+fundamentals-bioJVM/Test/testOnly izumi.functional.bio.SubmergedTypedErrorTest izumi.functional.bio.CatsToBIOTest"` |
| 3. Transparent bifunctorization at Injector/Lifecycle/LogIO + Identity → MiniBIO | M2-PR-03, M3, M4 (transitive) | `sbt "+distage-core/Test/testOnly izumi.distage.injector.*"` + new `BifunctorizedIdentityTest` in M2 |
| 4. No-op for real bifunctors (`bifunctorize(zio) eq zio`) | PR-01, PR-05 | `sbt "+fundamentals-bioJVM/Test/testOnly izumi.functional.bio.BifunctorizedTypeTest izumi.functional.bio.BifunctorizedNoOpTest"` |
| 5. No-More-Orphans (cats not forced on classpath) | PR-06, PR-08 | `sbt "+distage-extension-config/Test/testOnly izumi.distage.impl.OptionalDependencyTest"` |
| 6. Quasi* deleted, BIO used everywhere | M5 | `sbt "+Test/compile"` + `! grep -rE 'Quasi(IO|Async|Functor|Applicative|Primitives|Temporal|IORunner)' fundamentals/ distage/ logstage/ --include='*.scala'` (zero hits) |
| 7. Compiles on Scala 2.13, Scala 3, Scala 2.12 | PR-09, every milestone closer | `sbt clean +Test/compile +test` |

## Summary for ledger

```
[ ] M1 — Bifunctorized core + CE→BIO conversion + cats laws (Goals 1, 2, 4, 5, 7)
    PRs: 01 opaque type, 02 SubmergedTypedError, 03 Exit.Trace note, 04 CatsToBIO ladder,
         05 no-op bifunctor identity, 06 deprecate PrimitivesFromBIOAndCats,
         07 CatsLawsTest, 08 OptionalDependencyTest guard, 09 cross-Scala lock
[ ] M2 — Identity → MiniBIO bridge + Bifunctorized.Identity alias (Goals 3, 4, 7)
[ ] M3 — Lifecycle bifunctorization, replace QuasiIO/QuasiPrimitives constraints (Goals 3, 6, 7)
[ ] M4 — Injector/Subcontext/Producer/LogIO seams accept F[+_,+_]: IO2 with monofunctor overload (Goals 3, 6, 7)
[ ] M5 — Quasi* sweep + deletion across 106 files in 9 sub-modules (Goals 6, 7)
[ ] M6 — Microsite, migration guide, release notes (Goal 7)
```
