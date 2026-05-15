# Bifunctorization Deviations from Spec

`bifunctorization.md` is the authoritative spec. It **must not** be edited
to match implementation choices — that creates a moving target. If the
implementation deviates from the spec, document the deviation here with
the reason and the path to remediation.

This file replaces the convention of in-place spec amendments (notably
commit `6fecdd330` "PR-04 follow-up: resolve design Q via Option B (spec
amended)", which was reverted after the deviation it documented was
implemented properly).

---

## Format

```
## [D-NN] <one-line headline>
**Status:** open | remediated
**Spec section:** <quote or section reference in bifunctorization.md>
**Deviation:** <what the implementation does differently>
**Reason:** <why the deviation exists>
**Remediation path:** <what would close it, or "n/a" if accepted permanently>
```

---

## Active deviations

### [D-02] `Bifunctorized.bifunctorize`/`debifunctorize` (method) remains identity; submerging happens at the implicit-conversion seam, gated on `cats.ApplicativeError`
**Status:** open (accepted as design)
**Spec section:** "Conversion of effect values":

> "the Throwable error must be Submerged, converted into a typed error during `bifunctorize`."
>
> "In `debifunctorize`, a typed error must be de-Submerged, unwrapped, as its expected to be in order for monofunctor's native methods to work with it."

**Deviation:** the spec describes `bifunctorize` / `debifunctorize` as if they are
single functions that submerge / de-submerge unconditionally. The implementation
splits the responsibility:
- `Bifunctorized.bifunctorize(f: F[A])` (the method on the companion) — identity reinterpret-cast (Goal 4 zero-cost).
- `Bifunctorized.debifunctorize(b: Bifunctorized[F, Throwable, A])` (the method) — identity reinterpret-cast.
- The implicit conversions `bifunctorizeSubmerging` / `debifunctorizeUnSubmerging` in `CatsToBIOConversions` — actually submerge / de-submerge, gated on `cats.ApplicativeError[F, Throwable]` plus `izumi.reflect.TagK[F]`.
- Companion-of-RHS conversions `Bifunctorized.{bifunctorizeConversion, debifunctorizeConversion}` — cats-free identity (only fires when cats is NOT on the classpath, Goal 5).

**Reason:** three constraints make a single unconditional implementation impossible:
1. **Goal 4** ("bifunctorize is a no-op for real bifunctors — `bifunctorize(zio) eq zio` should hold"). A method that always submerges breaks this for ZIO/MonixBIO/Either/MiniBIO.
2. **Goal 5** ("No More Orphans" — users without cats on the classpath must still use `Bifunctorized`). A method requiring `cats.ApplicativeError` would break the no-cats build (`Bifunctorized.scala` imports `cats.*`).
3. The spec's combined effect "any user reaching for `bifunctorize(io)` on a cats monofunctor sees typed-error semantics" is achieved by routing through the implicit-conversion seam in `CatsToBIOConversions._`, which is the canonical user-facing import for the cats→BIO ladder.

Concretely: users who `import izumi.functional.bio.CatsToBIOConversions.*` (which is required anyway to get `AsyncToBIO` and `PrimitivesToBIO`) automatically pick up the transparent (de-)submerging behavior at expected-type sites. Users who do not import it (i.e., real-bifunctor users) keep the zero-cost identity path.

The spec text mentions "during `bifunctorize`" — under this implementation, the
spec-mandated submerging is observable at user-facing seams where the user writes
`val b: Bifunctorized[F, Throwable, A] = fa` (assignment-site conversion) or
`val fa: F[A] = b` (reverse conversion). The literal method `Bifunctorized.bifunctorize`
remains identity because forcing it to submerge would either break Goal 4 (for
real bifunctors) or Goal 5 (cats-free build).

**Remediation path:** could be closed by either:
- Splitting `bifunctorize` into a cats-free method (current behavior, in `Bifunctorized`) and a cats-mediated method (in `CatsToBIOConversions`, e.g. `CatsToBIOConversions.bifunctorize`). Users would call the appropriate variant explicitly.
- Or, accepted as design: the spec text "during `bifunctorize`" reads operationally as "at the seam where the user's monofunctor `F[A]` becomes a `Bifunctorized[F, Throwable, A]`", which the implicit-conversion seam satisfies.

Accepted as design for now — Option A from the implementation task; lowest change radius.

## Closed / remediated deviations

### [D-01] `bifunctorize`/`debifunctorize` did not transparently (de-)submerge typed errors
**Status:** remediated (commit chain following the revert of `6fecdd330`)
**Spec section:** "Conversion of effect values":

> "the Throwable error must be Submerged, converted into a typed error during `bifunctorize`."
>
> "In `debifunctorize`, a typed error must be de-Submerged, unwrapped, as its expected to be in order for monofunctor's native methods to work with it."

**Deviation (was):** the original M1 PR-01 implementation made
`bifunctorize`/`debifunctorize` pure type-level identity casts. Submerging
happened only inside BIO instance methods (`fail`, `syncThrowable`,
`fromFuture`, etc.). A user who created a `Bifunctorized[IO, Throwable, A]`
via `F.fail(rt)` and then called `debifunctorize(_).unsafeRunSync()` would
see `SubmergedTypedError[IO](rt)` raised, not `rt` raw — contradicting the
spec's "must be de-Submerged" requirement.

**Reason (was):** PR-04 review flagged the spec/impl mismatch as PR-04-D01.
The orchestrator recommended "Option B — amend the spec to match the
implementation" and committed that amendment as `6fecdd330` under
autonomous-loop-dynamic mode. The amendment was a workaround, not a
genuine design decision; it rationalised the implementation rather than
fixing it.

**Remediation:** spec restored to its original text. Transparent
submerging/de-submerging implemented in `CatsToBIOConversions.scala` for
cats-mediated monofunctors. For real bifunctors (ZIO, MonixBIO, Either,
MiniBIO): no-op (Goal 4 zero-cost identity preserved via
`BifunctorizedNoOpInstances`). For `Identity`: handled by the existing M2
`bifunctorizeIdentity`/`debifunctorizeIdentity` path, which uses MiniBIO
as the carrier and runs synchronously with rethrow on the way out.
