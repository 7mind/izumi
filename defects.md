# Izumi — Bifunctorization Defect Ledger

Discovered defects from adversarial reviews of each PR. Entries are
append-only; status flips in place. Headlines describe the problem, not
the fix.

Status: `[ ]` open · `[~]` under fix · `[x]` resolved

---

## PR-01

## [PR-01-D01] `ClassTag[Bifunctorized[F, E, A]]` test does not actually exercise `getClassTag`
**Status:** resolved
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/.jvm/src/test/scala/izumi/functional/bio/BifunctorizedTypeTest.scala:71
**Description:** Because `Bifunctorized[F, E, A]` is an abstract type that erases to `Object`, the compiler's built-in `ClassTag` materializer macro produces `ClassTag(classOf[Object])` for it — identical to what the implicit `getClassTag` returns. Mutation test: deleting `getClassTag` entirely would not fail this assertion. Any value whose `runtimeClass eq classOf[Any]` (Object) passes, which the macro-derived `ClassTag` produces too.
**Fix:** Added `assert(ct eq Bifunctorized.getClassTag[DummyF, Throwable, Int])` at BifunctorizedTypeTest.scala:71 (reference-identity assertion locks the named implicit as the resolved source). Verified on 2.12/2.13/3.

## [PR-01-D02] Missing variance widening test
**Status:** resolved
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/BifunctorizedTypeTest.scala
**Description:** Plan §4 risk #2 explicitly flags Scala 2.12 variance inference for `Bifunctorized[F, +E, +A]` as the main hazard. The current test asserts identity-eq but never exercises covariant widening on either `E` or `A`. A regression that drops the `+` on the abstract type member would not be caught.
**Fix:** Added "preserve covariance on E and A" test case at `.jvm/.../BifunctorizedTypeTest.scala:33-39`, exercising widening on both `+E` (RuntimeException → Throwable) and `+A` (Cat → Animal) simultaneously. Compiles only because both abstract-type members carry `+`. Verified on 2.12/2.13/3.

## [PR-01-D03] `Bifunctorized.assert` is public but performs an unchecked cast
**Status:** resolved
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala:25
**Description:** `assert` performs an unchecked `asInstanceOf[Bifunctorized[F, E, A]]` for an arbitrary user-chosen `E`. Once PR-04 lands submerging semantics, public `assert` lets a user produce a `Bifunctorized[ZIO, MyTypedError, A]` from a raw `ZIO[Any, Throwable, A]` whose error channel was never submerged — silently breaking the conversion-typeclass invariant. Plan §3.5 enumerates `bifunctorize`/`debifunctorize`/conversions/`toMonofunctor` as the user-facing surface; `assert` is implementation machinery.
**Root cause:** Prior art (izumi-1766.patch line 164) kept `assert` accessible within `object Bifunctorized`'s scope — effectively public, but the surface was small (one file, two internal callers).
**Fix:** Changed signature at Bifunctorized.scala:25 to `private[bio] def assert[...]`. PR-04 `impl/` sub-package and the test (both within `izumi.functional.bio`) retain access. Verified on 2.12/2.13/3.

## [PR-01-D04] `assert` shadows `Predef.assert` in wildcard imports
**Status:** resolved (auto-resolved by D03)
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala:25
**Description:** Any user who writes `import izumi.functional.bio.Bifunctorized.*` (wildcard) or `import izumi.functional.bio.Bifunctorized.assert` will lose access to `Predef.assert(condition: Boolean)` inside that scope, breaking idiomatic Scala. The test file dodges this by importing only named conversion methods. The reviewer's checklist item 17 explicitly flagged this collision.
**Fix:** Auto-resolved by D03 — `private[bio]` means `assert` is not in any user wildcard-import scope outside the `bio` package, so the collision cannot occur.

## [PR-01-D05] Test imports `bifunctorizeConversion` inside method bodies — doesn't verify §3.5 UX promise
**Status:** resolved
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/BifunctorizedTypeTest.scala:33-46
**Description:** Plan §3.5 states the implicit conversions should be auto-applied at expected-type sites — the intended UX is that placing a value of type `F[A]` at a position expecting `Bifunctorized[F, Throwable, A]` Just Works without any user-side import. The current test imports `bifunctorizeConversion`/`debifunctorizeConversion` per-method, which masks whether the design's promise (companion-of-RHS-of-alias implicit search) actually holds.
**Fix:** Removed both inline `import Bifunctorized.bifunctorizeConversion` / `import Bifunctorized.debifunctorizeConversion` lines (no longer present anywhere in the test). Test still compiles, confirming §3.5's promise that the companion-of-RHS-of-alias implicit search resolves auto-conversions at expected-type sites without explicit imports.

## [PR-01-D06] Test uses `DummyF` — does not exercise Goal 4 "real bifunctor no-op" case
**Status:** resolved
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/BifunctorizedTypeTest.scala:10-11
**Description:** Goal 4 (`bifunctorize(f: ZIO[…]) eq f`) cannot be demonstrated with a `DummyF` stand-in that isn't a bifunctor. Plan §2 PR-01 explicitly required "identity-eq (`val z: ZIO[Any, Throwable, Int] = ???; bifunctorize(z) eq z` — needs CE/ZIO classpath available — guarded behind a JVM-only test)". The executor stripped that and substituted a `DummyF` test.
**Root cause:** ZIO is already on the `fundamentals-bio` classpath (see `Root.scala:90` `BIOZIO` using `zio.IO` directly). Goal 5 (No-More-Orphans) is unaffected because Goal 5 forbids cats, not zio.
**Fix:** Added ZIO-based test "preserve runtime identity through bifunctorize for a real bifunctor (ZIO)" at `.jvm/.../BifunctorizedTypeTest.scala:74-78`. Whole test file moved from shared `src/test/scala/…` to `.jvm/src/test/scala/…` because ZIO is JVM-only on this module. Goal 4 (`bifunctorize(zio) eq zio`) now exercised by a real bifunctor. Verified on 2.12/2.13/3.

## [PR-01-D07] `BifunctorizedOps.unwrap` may allocate on Scala 2.12.21 due to AnyVal + asInstanceOf interaction
**Status:** resolved (deferred to PR-05 / PR-07 verification)
**Severity:** nit
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala:60-65
**Description:** Value-class implicit classes whose body performs `asInstanceOf` are known to occasionally allocate the wrapper on Scala 2.12 when the receiver is captured into a closure. Theoretical regression to Goal 4's zero-cost promise. Prior art uses the same shape, so this is pre-existing behaviour.
**Fix:** No code change in PR-01. Verification deferred to PR-05 (where the no-op identity path makes performance load-bearing) and PR-07 (cats laws environment exercises hot paths). Reviewer explicitly marked this "Out of scope; flag for PR-05 / PR-07 verification."

## [PR-01-D08] package.scala alias placement is inconsistent with related alias clusters
**Status:** resolved (deferred — cosmetic, no functional impact)
**Severity:** nit
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/package.scala:177
**Description:** Pre-existing `Ref2`/`Latch2`/`Semaphore2`/`SyncSafe2`/`Clock2`/`Entropy2` form a cluster of "F[_, _] → F[Nothing, _]" derivations. `Bifunctorized` is conceptually different (a brand-new type, not an alias-derived projection) and placing it at end of file makes its distinction less obvious at a glance.
**Fix:** Cosmetic only. Deferred to a future microsite / scaladoc refresh PR; not load-bearing on any compile or behaviour.

## [PR-01-D09] Missing top-of-file header scaladoc for `object Bifunctorized`
**Status:** resolved (deferred — cosmetic)
**Severity:** nit
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala
**Description:** Neighbouring `CatsConversions.scala` opens with a top-level header comment on the trait/object. The new file's scaladoc lives on the inner `type Bifunctorized`, not the object. Inconsistent but not a defect.
**Fix:** Cosmetic only. The inner-type scaladoc still conveys the design intent; the object-level header is redundant given the namespace mirrors the type name.

## [PR-01-D11] Untracked stub file remains in shared test path after move
**Status:** resolved
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/BifunctorizedTypeTest.scala
**Description:** When D06's fix moved the test to `.jvm/src/test/scala/...`, the fix subagent left a 2-line stub at the original shared path (`package izumi.functional.bio` + a comment pointer). The file is untracked in git (confirmed via `git status` showing `??` and absence from `git ls-files`), so there is no historical artifact to preserve. Future humans will see an empty test-class file and wonder why it exists.
**Fix:** `rm` removed the stub. Verified absent via `ls`.

## [PR-01-D12] `getClassTag` body deviation from prior art justified by an incorrect premise
**Status:** resolved (reviewer's empirical premise was itself refuted; deviation kept, rationale corrected)
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala:43-44
**Description:** The round-1 fix subagent changed `getClassTag`'s body from `implicitly[ClassTag[Any]].asInstanceOf[...]` (matching prior art izumi-1766.patch:157) to `ClassTag.AnyRef.asInstanceOf[...]`. The round-2 reviewer claimed the deviation was unjustified because `implicitly[ClassTag[Any]]` returns a stable singleton equally well. The round-3 fix subagent attempted the revert and empirically observed the `ct eq getClassTag[...]` assertion FAIL on Scala 2.13.18 (`Object was not the same instance as Any` at BifunctorizedTypeTest.scala:71). Conclusion: on Scala 2.13, `implicitly[ClassTag[Any]]` does NOT produce a stable singleton across invocations — the compiler's ClassTag materializer-macro path (and/or interaction with `asInstanceOf` casts) allocates fresh `ClassTag` instances. `ClassTag.AnyRef`, being a named `val` on the `ClassTag` companion object, IS guaranteed-stable. Both round-1's deviation and the reviewer's refutation cited the wrong cause; the correct cause is that **the *materializer-macro* path (not the explicit `val` lookup) is what `implicitly` resolves to here, and the macro allocates**.
**Root cause:** Scala 2.13's `implicitly[ClassTag[Any]]` resolves via the `ClassTag.apply` factory (not the `ClassTag.Any` stable val), which allocates a fresh `ClassTag(classOf[Object])` each call. This is independent of any subsequent `asInstanceOf` cast.
**Fix:** No source change to `getClassTag` body — original deviation was empirically correct for the wrong stated reason. To prevent a future maintainer from "fixing" this back to `implicitly[ClassTag[Any]]` based on the (incorrect) prior-art-parity argument, see [PR-01-D13] for a defensive comment addition.

## [PR-01-D13] `getClassTag` lacks an explanatory comment for the non-obvious `ClassTag.AnyRef` choice
**Status:** resolved (superseded by D14 — `ClassTag.AnyRef` itself is unsound, see below)
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala:40-44
**Description:** Round 2 already demonstrated that a smart reviewer can be confidently wrong about why `ClassTag.AnyRef` is used here instead of the more idiomatic `implicitly[ClassTag[Any]]`. Without a code-level comment, a future maintainer reading this file in isolation is highly likely to "fix" the deviation, silently breaking the test on Scala 2.13. Defensive comments are exactly the kind of comment CLAUDE.md prescribes ("Only add one when the WHY is non-obvious") — this is the textbook case.
**Suggested fix:** Extend the scaladoc above `getClassTag` (currently lines 40-42) with one extra sentence explaining the choice:
```scala
/** Implicit `ClassTag` shim. Mirrors the prior-art pattern so that `Bifunctorized[F, E, A]`
  * is recognised everywhere a `ClassTag[F[A]]` would be (both erase to `Any`).
  *
  * Body uses `ClassTag.AnyRef` (a stable singleton) rather than `implicitly[ClassTag[Any]]`:
  * on Scala 2.13, separate `implicitly[ClassTag[Any]]` invocations return distinct heap
  * instances (the materializer macro allocates), which breaks the `eq` invariant the
  * test relies on. Do not "revert" to `implicitly[ClassTag[Any]]` without re-running
  * `BifunctorizedTypeTest` on Scala 2.13.
  */
```
Verify the test still passes on all three Scala versions after the doc-comment change.
**Fix:** Comment was added at Bifunctorized.scala:40-48, but the underlying choice it defended (`ClassTag.AnyRef`) is itself unsound — see D14. The comment will be replaced when D14 is fixed.

## [PR-01-D14] `getClassTag` returns `ClassTag.AnyRef`, lying about the runtime class when `F[A]` is a primitive
**Status:** resolved
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala:40-50
**Description:** User feedback (2026-05-13): the current `getClassTag` body `ClassTag.AnyRef.asInstanceOf[ClassTag[Bifunctorized[F, E, A]]]` is unsound. `Bifunctorized[F, E, A]` is an abstract type but at runtime the value IS the underlying `F[A]`. For `F = Identity` and `A = Int`, `F[A]` is `Int` (because `Identity[X] = X`), which is NOT `<:< AnyRef`. Returning `ClassTag.AnyRef` means callers that allocate `Array[Bifunctorized[Identity, E, Int]]` get `Array[Object]` when they should get `Array[Int]` (primitive). Prior art has the same flaw (`implicitly[ClassTag[Any]]`); this fix improves on prior art.
**Root cause:** Both the round-1 fix subagent (using `ClassTag.AnyRef`) and the prior art (using `implicitly[ClassTag[Any]]`) treated the abstract-type erasure (Object) as the source of truth instead of the underlying-value class.
**Suggested fix:** Change `getClassTag` to take `ClassTag[F[A]]` as an implicit parameter and cast it:
```scala
/** Implicit `ClassTag` shim. Reflects the runtime class of the underlying `F[A]`.
  *
  * `Bifunctorized[F, E, A]` is an abstract type, so the compiler's `ClassTag` materializer
  * cannot synthesize one directly. We delegate to `ClassTag[F[A]]` — macro-derivable for any
  * concrete `F` and `A` — and cast. This is honest: a `Bifunctorized[F, E, A]` value at
  * runtime IS an `F[A]`. In particular for `F = Identity`, `A = Int` the underlying value
  * is a primitive `Int`, and the derived `ClassTag` correctly carries `classOf[Int]`.
  */
implicit def getClassTag[F[_], E, A](implicit underlying: ClassTag[F[A]]): ClassTag[Bifunctorized[F, E, A]] =
  underlying.asInstanceOf[ClassTag[Bifunctorized[F, E, A]]]
```
The test must also change: the `runtimeClass eq classOf[Any]` and `ct eq Bifunctorized.getClassTag[…]` assertions both depended on the old (unsound) body. Update the `ClassTag` test to verify what actually matters: that the implicit is summonable and that for a non-primitive `F[A]` the runtime class is the underlying type. Drop or weaken the `eq getClassTag[…]` reference-identity assertion — with the materializer-derived `ClassTag[F[A]]`, two invocations may not be `eq` (and that's fine; the property doesn't matter for correctness).
**Fix:** `getClassTag` at Bifunctorized.scala:48-49 now takes `implicit underlying: ClassTag[F[A]]` and casts it (with explanatory scaladoc replacing D13's defensive comment). Test updated: explicit `dummyFClassTag[A]` implicit provided (required because `DummyF` is a parameterized trait; the materializer cannot synthesize a `ClassTag` for it on Scala 2). New Identity-style test "derive correct ClassTag for primitive F[A]" at lines 82-94 with locally-scoped `type Id[A] = A` plus explicit `idIntClassTag = ClassTag.Int.asInstanceOf[...]` (Scala 2.13's macro doesn't expand local aliases, so the explicit evidence is needed). All 11/11 tests pass on Scala 3.7.4, 2.13.18, 2.12.21. See D15/D16 for cleanup of decorative scaffolding and misleading comments added during the fix.

## [PR-01-D15] `@nowarn("cat=unused-locals")` on `idIntClassTag` is unnecessary; comment misleads
**Status:** resolved
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/.jvm/src/test/scala/izumi/functional/bio/BifunctorizedTypeTest.scala:86-90
**Description:** Round-3 reviewer empirically refuted the rationale for the annotation: removing it and re-running on all three Scala versions yields zero warnings. The accompanying comment ("@nowarn suppresses the 'unused' fatal warning that fires on Scala 2.13 when Scala 3 resolves this implicit through a different path") is factually wrong — the implicit IS consumed by `getClassTag` on every version, no warning is emitted. Leaving the annotation + comment in place will misinform future maintainers.
**Suggested fix:** Remove the `@scala.annotation.nowarn("cat=unused-locals")` annotation. Rewrite the comment to one factual line: "Scala 2.13's `ClassTag` macro does not expand local type aliases, so we provide the evidence (`Id[Int] = Int`) explicitly." Verify 11/11 on 2.12/2.13/3 after.
**Fix:** Removed `@scala.annotation.nowarn(...)` and the misleading 2-line justifying comment. Replaced with single accurate line about Scala 2.13's macro behavior. 11/11 pass on 2.12.21, 2.13.18, 3.7.4 with zero warnings.

## [PR-01-D16] Companion-object move of `DummyF`/`DummyBox` is decorative; comments state the wrong cause
**Status:** resolved
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/.jvm/src/test/scala/izumi/functional/bio/BifunctorizedTypeTest.scala:5-15
**Description:** Round-3 reviewer empirically refuted the rationale for the companion-object scaffolding: inlining `DummyF`/`DummyBox`/`dummyFClassTag` back into the class body works fine on all three Scala versions (11/11 pass). The comment claim ("Defined at companion-object level...so that the Scala 2.13 ClassTag materializer can synthesize ClassTag[DummyF[A]] without an outer-class reference") is false; the second comment ("Scala 2.13's ClassTag macro cannot auto-derive tags for trait types defined in companion objects") is also wrong about the cause (the issue is parameterized abstract types in general, not the companion-vs-inner distinction). The `dummyFClassTag` IS necessary (compile fails without it on 2.13), but the companion-object placement is purely decorative.
**Suggested fix:** Inline `DummyF`/`DummyBox`/`dummyFClassTag` back into the class body. Remove `object BifunctorizedTypeTest { … }` and the `import BifunctorizedTypeTest._` line. Single comment on `dummyFClassTag`: "DummyF is a parameterized trait; the materializer cannot synthesize a ClassTag for it on Scala 2, so we supply one explicitly." Re-run 11/11 on 2.12/2.13/3.
**Fix:** Removed `object BifunctorizedTypeTest { ... }` wrapper and `import BifunctorizedTypeTest._`. Inlined `DummyF`/`DummyBox`/`dummyFClassTag` as `private` members of the test class with a single correct comment about parameterized-trait ClassTag synthesis on Scala 2. 11/11 pass on all three Scala versions.

## [PR-01-D17] ClassTag implicit-search regression risk for downstream PRs
**Status:** resolved (deferred — flagged for plan §3.5 and future PRs)
**Severity:** nit
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala:48
**Description:** The new `getClassTag(implicit ClassTag[F[A]])` signature adds a constraint where downstream callers asking for `ClassTag[Bifunctorized[F, E, A]]` must also have `ClassTag[F[A]]` in scope. For concrete `F` and `A` the macro derives this; for abstract `F[_]: ClassTag`-style helpers, callers may need to thread the constraint through. PR-02..PR-08 should re-verify no implicit-search regression as new instances land.
**Fix:** No PR-01 code change. Note for future PRs added to defects.md and the resolution of this entry.

## [PR-01-D18] DummyF-based `ClassTag` test (lines 75-80) is structurally redundant after Identity test added
**Status:** resolved (deferred — nit, no functional impact)
**Severity:** nit
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/.jvm/src/test/scala/izumi/functional/bio/BifunctorizedTypeTest.scala:75-80
**Description:** With the new `getClassTag` body, the `DummyF`-based ClassTag test is near-tautological — `ct.runtimeClass eq classOf[DummyF[Any]]` only confirms that `getClassTag` returns its implicit parameter. The Identity-style test at lines 82-94 already verifies the soundness more strongly (primitive `Int` rather than `Object`). Harmless redundancy.
**Fix:** Deferred. The test is cheap to keep; the redundancy is at most a 6-line nit.

## [PR-01-D10] Forward-looking scaladoc on `assert` references PR-04 behaviour
**Status:** resolved (auto-resolved by D03)
**Severity:** nit
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/Bifunctorized.scala:21-24
**Description:** Scaladoc on `assert` reads "For internal use by submerging code paths..." — describing PR-04 behaviour, not what `assert` does in PR-01. Users reading the freshly-shipped object will be confused. Folds into D03's fix.
**Fix:** Scaladoc at Bifunctorized.scala:21-24 rewritten to describe internal-escape-hatch semantics: "Unchecked reinterpret cast. Internal escape hatch used by `bifunctorize` and conversion-typeclass implementations that have already encoded their own error channel."

## [PR-02-D01] Companion-object placement of test fixtures `FA`/`FB` is decorative
**Status:** resolved
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/SubmergedTypedErrorTest.scala:77-81 (and 8 per-`in` imports at lines 10,16,22,29,38,55,62,68)
**Description:** `object SubmergedTypedErrorTest { trait FA[A]; trait FB[A] }` is the same defect class as PR-01-D16. The reviewer empirically verified that inlining `private trait FA[A]` / `private trait FB[A]` as class-body members compiles and passes 19/19 on Scala 3.7.4, 2.13.18, and 2.12.21. The executor's stated rationale ("Scala 3 'infinite loop' warning from `implicit val tagFA: TagK[FA] = TagK[FA]`") was a non-sequitur: no such implicit val exists or is needed — the materializer macro derives `TagK[FA]` at each call site.
**Suggested fix:** Move `FA` and `FB` to `private trait FA[A]` / `private trait FB[A]` at the top of the class body (matching post-D16 `BifunctorizedTypeTest` layout). Remove `object SubmergedTypedErrorTest { … }` and the eight per-`in`-block `import SubmergedTypedErrorTest.…` lines (the traits become directly visible at the method scope).
**Fix:** `FA` and `FB` inlined as `private trait FA[A]` / `private trait FB[A]` at the top of `SubmergedTypedErrorTest` class body. Removed `object SubmergedTypedErrorTest { … }` companion and all 8 inline imports. 19/19 pass on Scala 3.7.4, 2.13.18, 2.12.21.

## [PR-02-D02] NPE risk on null payload — consistency-with-prior-art behaviour
**Status:** resolved (deferred — consistent with `TypedError.scala`)
**Severity:** minor
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/SubmergedTypedError.scala:24
**Description:** `s"Submerged typed error of class=${payload.getClass.getName}..."` will NPE if `payload` is `null` (legal at type `Any`). Failure happens inside the `RuntimeException` superconstructor, which is a confusing site for the failure.
**Fix:** No code change. The same latent NPE exists in `TypedError.scala:4` (`error.getClass.getName`). Adding `Objects.requireNonNull` here without applying the same change to `TypedError` would create inconsistency. PR-02 follows the codebase convention; if null-guarding is desired, address `TypedError` and `SubmergedTypedError` together in a future cleanup. CLAUDE.md "fail fast" applies — null submerges are user errors and the failure happens close enough to the call site.

## [PR-02-D03] Scala-2-style wildcard `[_]` in pattern matches
**Status:** resolved (deferred — codebase mixes styles)
**Severity:** nit
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/SubmergedTypedError.scala:39,50
**Description:** Pattern matches use `SubmergedTypedError[_]` (Scala 2 style). Scala 3 prefers `[?]` under `-source:future`.
**Fix:** No change. The codebase mixes both styles; the project hasn't enabled the deprecation. Cross-build green on all three Scala versions.

## [PR-02-D04] `asInstanceOf` cast in `apply` lacks a one-line comment for the soundness argument
**Status:** resolved (deferred — nit, reviewer accepted as design intent)
**Severity:** nit
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/SubmergedTypedError.scala:40
**Description:** The cast is soundness-by-tag-equality; the LightTypeTag equality at the guard implies same monofunctor `F` by spec §3.2. The class-level scaladoc already documents the discriminator semantics so the cast is reachable for an attentive reader. A line-level comment would help skim-readers but isn't necessary.
**Fix:** No change. The class scaladoc carries the design intent.

## [PR-02-D05] Symmetric test case (unapplying FB on inner existingFB) is implicit, not explicit
**Status:** resolved (deferred — transitively covered)
**Severity:** nit
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/test/scala/izumi/functional/bio/SubmergedTypedErrorTest.scala:34
**Description:** Test 4 checks unapplying FB on the OUTER returns None. The symmetric case — unapplying FB on the INNER `existingFB` returns Some(42) — is transitively covered by test 1 (over FB) but not stated explicitly. Adding a 2-line assertion would round out coverage.
**Fix:** No change. The transitive coverage suffices for PR-02.

## [PR-02-D06] Inconsistent line-style between `SubmergedTypedError` and `TypedError` extends clauses
**Status:** resolved (deferred — both styles exist)
**Severity:** nit
**Location:** /home/kai/src/izumi/fundamentals/fundamentals-bio/src/main/scala/izumi/functional/bio/SubmergedTypedError.scala:20-28
**Description:** `SubmergedTypedError` opens params on the class declaration line and indents the `extends RuntimeException(...)` block. `TypedError.scala:4` keeps everything on one line. Both styles exist elsewhere.
**Fix:** No change. Multi-line form is more readable for this 4-arg superconstructor call.
