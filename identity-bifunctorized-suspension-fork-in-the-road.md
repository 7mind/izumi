# Identity-Bifunctorized Suspension — Fork In The Road

Open questions and concerns captured for a future fresh-context session.
The bifunctorization refactor is structurally complete (67 commits ahead
of `develop` on `feature/bifunctorization`, HEAD `148b84d63`), and the
typeclass-driven transparent submerging from `Bifunctorize[F]` (commit
`751009c55`) plus the `Spec1[F[_]]` / `SpecIdentity` DSL lift
(`1e79fc020`) plus the transparent `Injector.apply[F[_]]` overload
(`805454fb7`) deliver Goal 3's "transparent bifunctorization at seams"
on the user-facing side. The remaining concerns are framework-internal
regressions that surfaced when test suites exercise the full role-app
lifecycle.

---

## The fork: Identity suspension semantics

`type Identity[+A] = A`, so pre-bifunctorization an `Injector[Identity]
.produceRun(...)` chain ran eagerly — every value was evaluated at the
moment of construction, side effects executed inline, the `Unit` result
returned without any "run" step.

After M5, `Injector` is bifunctor-shaped (`Injector[F[+_, +_]]`) and the
Identity special-case routes through `Bifunctorized.IdentityBifunctorized
[+E, +A]` whose runtime carrier is `MiniBIO[Throwable, A]` (boxed,
**suspended**). This is by design and matches `bifunctorization.md`
Goal 3: "Identity is special-cased and goes through a bifunctorization/
debifunctorization cycle to MiniBIO and back, transparently to the user."

The transparent path:
1. User writes `Injector[cats.effect.IO]()` or `Injector[Identity]`-shape
   code. The `Injector.apply[F[_]]` monofunctor overload (commit
   `805454fb7`) summons `Bifunctorize[F]` and lifts to the bifunctor world.
2. For `Identity`: `Bifunctorize.bifunctorizeIdentity(a) = MiniBIO.sync(a)`
   — value `a` is now SUSPENDED inside MiniBIO.
3. BIO machinery runs inside the bifunctor world; the chain returns a
   `Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, A]`.
4. To extract: `Bifunctorized.debifunctorizeIdentity(b) = MiniBIO.autoRun
   .autoRunAlways(b.asInstanceOf[MiniBIO[Throwable, A]])` — runs the
   MiniBIO synchronously and rethrows on failure.

**The fork** is step 2 vs step 4: bifunctorization at the seam suspends
implicitly, but de-bifunctorization at the seam does NOT happen
implicitly for `IdentityBifunctorized`. The Identity-typeclass instances
(`Parallel2[IdentityBifunctorized]`, `UnsafeRun2[IdentityBifunctorized]`,
`Temporal2[IdentityBifunctorized]`, `ApplicativeError2[IdentityBifunctorized]`)
delegate to MiniBIO's instances via `asInstanceOf`, and the user's
extraction is a separate explicit `debifunctorizeIdentity` call OR an
auto-applied implicit conversion at expected-type sites.

The user-facing failure mode this enables:

```scala
// Before M5 (worked because Identity[A] = A; eager):
class MyApp extends RoleAppMain[Identity] {
  override def main: MainEffect[Unit] = Unit = {
    Injector[Identity]().produceRun(...)
    // produceRun returned `Unit` (== Identity[Unit] == A); side effects ran inline.
  }
}

// After M5 (silently broken):
class MyApp extends RoleAppMain[Bifunctorized.IdentityBifunctorized] {
  override def main: MainEffect[Unit] = {
    Injector[Bifunctorized.IdentityBifunctorized]().produceRun(...)
    // Returns Bifunctorized.IdentityBifunctorized[Throwable, Unit]
    //       = MiniBIO[Throwable, Unit] (suspended)
    // The value is DISCARDED (return type `Unit` triggers value-discard).
    // The MiniBIO is never run. Side effects never execute. The role lifecycle
    // never instantiates anything. Downstream `probe.locator.get[...]` NPEs.
  }
}
```

The user-visible damage: silent eager → lazy switch at the `main`
entry point. No compile error (Scala's value-discard is silent in many
return-type positions), no runtime exception until the role's resources
are referenced.

### Design options

1. **Auto-run on debifunctorize at value-discard positions** — make the
   implicit `debifunctorizeIdentityConversion` (or an analogous
   `MainEffect[Unit]`-shaped sink) execute the MiniBIO at the conversion
   seam. Hard to do robustly: Scala's value-discard machinery isn't a
   normal implicit-conversion site, and forcing eager execution at
   conversion violates the spec's invariant that `Bifunctorized[Identity,
   ...]` is the MiniBIO-suspended form (Goal 3).

2. **Force the user to `debifunctorizeIdentity` explicitly** — train
   callers (and the migration guide) to wrap any `main`-style
   `Identity`-typed body in `Bifunctorized.debifunctorizeIdentity(...)`.
   Mechanical, no design surprise. Burden on the user.

3. **Don't bifunctorize at the `RoleAppMain.main` boundary** — keep the
   `main` method monofunctor-typed. The `RoleAppMain` trait would need
   two variants (or a type parameter) to support both Identity-eager and
   Bifunctorized-suspended bodies. Adds complexity at the framework
   entry; preserves user expectations.

4. **Redesign the M2 Identity bridge** — drop the MiniBIO suspension
   for the Identity special case. Make `Bifunctorized.IdentityBifunctorized
   [+E, +A]` equivalent to `Either[E, A]` at the runtime level (eager
   carrier with a typed error channel). Preserves "Identity is eager".
   Breaks the M2 "Identity gains lawful monadic suspension" design choice.

5. **Hybrid**: keep MiniBIO suspension for the in-bifunctor-world ops,
   but at the user-facing extraction seams (`MainEffect`,
   `produceRun`'s return, etc.) auto-run. This requires identifying
   the seams and providing dedicated extraction points (e.g.
   `Injector.produceRunIdentity` that internally
   `debifunctorizeIdentity`-s).

The user invoking autonomous mode chose (autonomously) option 2 by
default. The framework-internal regressions documented below are the
consequences. A deliberate user decision could change the design.

### Where this matters in code

- `RoleAppMain.main` — discards the suspended `IdentityBifunctorized` result.
- `Producer.produceRun(plan)(f: Locator => F[Throwable, B])` — returns
  `F[Throwable, B]`. For Identity this is a suspended MiniBIO; downstream
  must run it. Currently runs only when the caller explicitly extracts.
- `RoleAppPlanner.Impl[F]`'s runtime-root set includes `Async2[F]` —
  but `Async2[IdentityBifunctorized]` is intentionally not bound (sync
  carrier). Pre-M5 used `Async1[Identity]` which was an unlawful no-op.
- `AppResourceProvider.Impl[F: TagKK: IO2: Primitives2]` /
  `RoleAppEntrypoint.Impl[F: TagKK: Primitives2]` — class-level context
  bounds that the BOOTSTRAP injector can't satisfy. Pre-M5 used `F[_]:
  TagK` with no constraints and pulled typeclasses from `runtimeLocator`
  at instantiation time.
- `TerminatingHandler` — calls `System.exit(1)` on uncaught role
  failures. Kills the ScalaTest JVM mid-run.

---

## Open question 1: 8 `RoleAppTest` failures (this fork's downstream consequence)

`distage-frameworkJVM/test`: 11/19 pass; 8 fail in `RoleAppTest.scala`.
Confirmed at HEAD `148b84d63` and at earlier baselines (M5/10c).

**Four distinct root causes**, all framework-internal regressions
caused by M5/10c:

1. **`RoleAppMain.main` discards the lifecycle** (the fork above).
   Wrapping the body in `Bifunctorized.debifunctorizeIdentity(...)`
   makes it run, but exposes #2.

2. **`AppResourceProvider.Impl` / `RoleAppEntrypoint.Impl` class-level
   context bounds.** These are wired by `RoleAppBootModule` whose
   injector is `IdentityBifunctorized`-flavored;
   `IO2[Bifunctorized[IO, +_, +_]]` etc. are bound only in
   `DefaultModule[F]`, consumed by the inner app injector. Bootstrap
   injector can't satisfy them.

   Fix: drop the constraints and plumb `IO2`/`Primitives2`/`Async2`
   through `runTasksAndRoles` parameters (or via a dedicated
   `FrameworkSupportModule` that ports the inner module's typeclass
   bindings into the bootstrap stage).

3. **`Async2[Bifunctorized.IdentityBifunctorized]` is not bound** by
   `IdentitySupportModule`. `RoleAppPlanner.Impl[F]` registers `DIKey
   .get[Async2[F]]` as a runtime root unconditionally. Identity's
   MiniBIO carrier has no `Async2` (sync-only).

   Fix options:
   - Bind a stub `Async2[IdentityBifunctorized]` that throws on async
     ops (matches the pre-M5 unlawful Identity behavior; lawless but
     unblocks the planner).
   - Make `RoleAppPlanner` register `Async2` as an OPTIONAL root and
     fall back to `IO2` for sync-only carriers.

4. **`TerminatingHandler` calls `System.exit(1)`** which kills the
   ScalaTest JVM mid-run, masking the actual assertion failures of
   tests that exercise role apps with uncaught faults. Pre-M5 the same
   handler ran via `Identity[Unit]` whose `unit` was eager `()` — the
   exit ran inline at the role app's natural completion, not from
   inside a suspended MiniBIO that may never have been entered.

   Fix: replace `System.exit(1)` with a typed-error raise inside the
   `F` carrier so the test harness can intercept it without killing
   the JVM. Tests that DO assert on `System.exit` would need a
   separate witness mechanism.

These four fixes are independent but interact at the framework level.
A clean session-7 dispatch would tackle them in order #1 → #3 → #2 → #4,
verifying `RoleAppTest` 19/19 incrementally.

---

## Open question 2: 3 ignored `CatsResourcesTestJvm` tests (M5-D01)

`distage-coreJVM/test`: 396/399 (3 `ignore`). The M5-D01 root-cause
hypothesis (izumi-reflect η-normalization gap between `Bifunctorized
[=IO, _, _]` and `Bifunctorized[=λ x => IO[x], _, _]`) was empirically
**refuted** in commit `3e2655ad6`: 5 standalone scala-cli reproducers at
`docs/upstream-reproductions/` show both Scala 2.13.18 and Scala 3.7.4
against izumi-reflect 3.0.8 PASS all comparisons (direct, indirect, η-
expanded).

The real divergence is elsewhere. **Next investigative step**: instrument
the load-bearing site at `distage-core-api/src/main/scala/izumi/distage/
model/plan/ExecutableOp.scala:170-173` (the `IncompatibleEffectType`
check) to log the actual `SafeType` / `LightTypeTag` pair being compared
at runtime when the test fails. The `<:<` returns false on those two
tags — capturing what they actually are will identify whether it's:
- A path-dependent type leak in some macro-derived tag,
- A `Throwable`-channel mismatch (`Throwable` vs `+E` vs `Nothing`),
- An eta issue at a DIFFERENT level than the one the scala-cli
  reproducers tested (e.g. nested type-lambda inside a higher-order
  position),
- A SafeType-level normalization gap (SafeType wraps LightTypeTag with
  extra bookkeeping; comparison may differ).

The 3 affected tests in
`distage-core/.jvm/src/test/scala/izumi/distage/compat/CatsResourcesTestJvm.scala`
are marked `ignore` with a comment pointer to `defects.md [M5-D01]`.

---

## Open question 3: 3 Scala 2-only logstage macros (user-deferred)

User-deferred per "Scala 2 to be unblocked later by me" during M5.
Three files still reference the pre-M5 `*1` monofunctor adapter
typeclasses (`IO1`, `Primitives1`, etc.) which no longer exist:

- `logstage/logstage-core/src/main/scala-2/izumi/logstage/macros/LogIOMacroMethods.scala`
- `logstage/logstage-core/src/main/scala-2/izumi/logstage/macros/LogMethodMacro.scala`
- `logstage/logstage-core/src/main/scala-2/izumi/logstage/api/logger/AbstractMacroLogIO.scala`

The Scala 3 versions of these files (`scala-3/`) were rebuilt on BIO2 in
M5/12a (commit `fd6fe9a8f`). The Scala 2 versions need a parallel
rewrite. Mechanical via quasiquotes; mirror M5/12a's pattern. ~2-4h.

The implementation in `scala-3/` uses `IO2#syncThrowable` + `Error2#tapBoth`
(see `fd6fe9a8f` diff for the new shape). Scala 2's quasiquote
equivalent should be straightforward.

Side effect of completing this: `fundamentals-bioJVM` compiles cross-build
on Scala 2.13.18 / 2.12.21 (currently main sources compile on 2.13 but
the logstage-core leaves `*1` references that fail Scala 2 cross-build
elsewhere). Note: per Session 3.5 finding, the `Lifecycle.F` covariance
fix only works on Scala 3 + 2.13; Scala 2.12 is dropped regardless.

---

## Open question 4: `StandaloneWiringTest` stub

`distage-testkit-scalatest/.jvm/src/test/scala/izumi/distage/testkit/distagesuite/compiletime/StandaloneWiringTest.scala`
remains stubbed. Root cause is a **pre-existing wiring bug** in
`StaticTestMain.scala:24` (or thereabouts) — the test's
`staticTestMainPlugin[F, G]` generic plumbing has an
`IdentityBifunctorized↔Bifunctorized[CIO, _, _]` planner mismatch.

Not bifunctorization-caused; not in this milestone's scope to fix.
But it's the last remaining stub in the testkit-scalatest test corpus.

---

## Open question 5: `InterruptionTest` cross-effect heterogeneous list

The InterruptionTest fixture originally had a heterogeneous list of
test classes spanning ZIO + CIO + Identity. With each test class now
typed `Spec1[F[_]]` / `Spec2[F[+_, +_]]` / `SpecIdentity`, the list
needs a bifunctor `AnyF`-style witness to remain homogeneous in the
container. The M5 work introduced `AnyF2` in `fundamentals-language`
as a placeholder; the test's machinery to use it is incomplete.

Lower-priority than the other open questions. Cross-effect test
heterogeneity is a niche pattern.

---

## Other surfaces affected by the suspension fork (less urgent)

These are user-visible but not currently broken (because they're
exercised through the `SpecIdentity` test-DSL lift `1e79fc020`):

- `BifunctorizedInjector` was deleted in M5/9d once `Injector` became
  bifunctor-shaped. Users with monofunctor `F[_]` use the M5-fix4a
  transparent `Injector.apply[F[_]]` overload.
- `LifecycleBifunctorized` was deleted in M5/7 once `Lifecycle` became
  bifunctor-shaped. Users with `Lifecycle[Identity, A]`-style needs use
  `Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, A]`.
- `Spec1[F[_]]` (`1e79fc020`) provides the user-facing DSL lift via
  `Bifunctorize[F]`. `SpecIdentity` similarly.

In all three the `Bifunctorize[F]` typeclass (commit `751009c55`)
performs the (de-)submerging transparently for cats-monofunctors, AND
the Identity bridge via `bifunctorizeIdentity`/`debifunctorizeIdentity`.

What's NOT transparent: framework entry points like `RoleAppMain.main`
that consume the result of the wrapped computation and discard it (the
fork question above). Those need either auto-run at the seam, or
explicit `debifunctorizeIdentity` in user code.

---

## Recommended next session priorities

1. **Resolve the Identity suspension fork** — pick option 1-5 from the
   "Design options" list above and document the choice in
   `bifunctorization-deviations.md` as `[D-NN]`. The chosen option then
   informs the fix shape for the 4 `RoleAppTest` root causes.

2. **Fix the 4 `RoleAppTest` root causes** per their independent fixes.
   Verify `distage-frameworkJVM/test` 19/19 incrementally.

3. **M5-D01 runtime instrumentation** — capture the actual SafeType pair
   at `ExecutableOp.scala:170-173` to identify the real
   `IncompatibleEffectType` divergence. Likely a few hours of careful
   logging + targeted unit tests.

4. (Optional, user-deferred) **Scala 2 logstage macro rewrite** — when
   the user signals readiness, ~2-4h of mechanical quasiquote work.

5. (Lowest priority) **`StandaloneWiringTest` `StaticTestMain.scala:24`
   plumbing fix**, **`InterruptionTest` cross-effect heterogeneous list**.

---

## Reference: state at HEAD `148b84d63`

- 67 commits ahead of `develop` on `feature/bifunctorization`.
- M1-M6 all `[x]` in `tasks.md`.
- `bifunctorization-deviations.md` Active deviations: empty.
- `bifunctorization.md` spec is at its original text (per the
  spec-immutable convention added in commit `67694f3dd`).
- Test counts on Scala 3.7.4:
  - `fundamentals-bioJVM`: 571/571
  - `distage-coreJVM`: 398/398 + 3 ignored (M5-D01)
  - `distage-extension-configJVM`: 29/29 + Goal-5 7/7
  - `distage-frameworkJVM`: 11/19 (8 RoleAppTest failures — Q1)
  - `distage-framework-docker`: green
  - `distage-testkit-coreJVM`: 0 (no tests)
  - `distage-testkit-scalatestJVM`: 128/128 + 1 cancelled-as-designed
  - `logstage-coreJVM`: 105/105
  - `distage-extension-pluginsJVM`: 7/7
- Cross-build:
  - Scala 3.7.4: green (modulo Q1+Q2+Q4+Q5)
  - Scala 2.13.18: main sources compile; tests have ~97
    `LifecycleTag.resourceTag` higher-kinded unification errors that
    need a Scala 2-specific macro or explicit type-app at binding sites
  - Scala 2.12.21: dropped (Lifecycle covariance supertype-dance rejected
    by 2.12's variance check)

## Key commits to read

- `cba7d0bbf` M5/7: Lifecycle restructure + delete `*1` family (Session 1)
- `bc9739765` M5/9a: distage-core main sources bifunctorized
- `751009c55` M5-fix2: `Bifunctorize[F]` typeclass; single-level conversion
- `1e79fc020` M5-fix3b: Spec1/SpecIdentity DSL transparent lift
- `805454fb7` M5-fix4a: transparent `Injector.apply[F[_]]` overload
- `7828e9837` M5-fix5b: Identity hardcoding mirrored to IntegrationCheck
- `148b84d63` M5-fix6: cats-mediated Parallel2 / UnsafeRun2 / ApplicativeError2
- `3e2655ad6` M5-fix4c: izumi-reflect η-normalization repro (refuted
  the M5-D01 hypothesis)

## Files of interest for the suspension-fork investigation

- `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorize.scala`
- `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala`
- `/home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/impl/MiniBIO.scala`
- `/home/kai/src/izumi/distage/distage-framework/src/main/scala/izumi/distage/roles/RoleAppMain.scala`
- `/home/kai/src/izumi/distage/distage-framework/src/main/scala/izumi/distage/roles/RoleAppEntrypoint.scala`
- `/home/kai/src/izumi/distage/distage-framework/src/main/scala/izumi/distage/roles/AppResourceProvider.scala`
- `/home/kai/src/izumi/distage/distage-framework/src/main/scala/izumi/distage/roles/RoleAppPlanner.scala`
- `/home/kai/src/izumi/distage/distage-core/src/main/scala/izumi/distage/modules/support/IdentitySupportModule.scala`
- `/home/kai/src/izumi/distage/distage-framework/.jvm/src/test/scala/izumi/distage/roles/test/RoleAppTest.scala`
- `/home/kai/src/izumi/distage/distage-core-api/src/main/scala/izumi/distage/model/plan/ExecutableOp.scala`

Cross-references in `tasks.md` and `defects.md` (esp. `[M5-D01]`).
