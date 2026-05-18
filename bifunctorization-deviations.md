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

_None._

## Closed / remediated deviations

### [D-02] `Bifunctorized.bifunctorize`/`debifunctorize` (method) was identity; submerging happened only at the implicit-conversion seam in `CatsToBIOConversions`
**Status:** remediated (commit chain following the introduction of the `Bifunctorize[F]` typeclass)
**Spec section:** "Conversion of effect values":

> "the Throwable error must be Submerged, converted into a typed error during `bifunctorize`."
>
> "In `debifunctorize`, a typed error must be de-Submerged, unwrapped, as its expected to be in order for monofunctor's native methods to work with it."

**Deviation (was):** the spec describes `bifunctorize` / `debifunctorize` as if they are
single functions that submerge / de-submerge unconditionally. The previous implementation
split the responsibility:
- `Bifunctorized.bifunctorize(f: F[A])` (the method on the companion) — identity reinterpret-cast (Goal 4 zero-cost).
- `Bifunctorized.debifunctorize(b: Bifunctorized[F, Throwable, A])` (the method) — identity reinterpret-cast.
- Separate implicit conversions `bifunctorizeSubmerging` / `debifunctorizeUnSubmerging` in `CatsToBIOConversions` — actually submerged / de-submerged, gated on `cats.ApplicativeError[F, Throwable]` plus `izumi.reflect.TagK[F]`.
- Companion-of-RHS conversions `Bifunctorized.{bifunctorizeConversion, debifunctorizeConversion}` — cats-free identity (only fired when the cats conversion was not in scope, Goal 5).

So the same effect-site `val b: Bifunctorized[F, Throwable, A] = fa` could pick either submerging or identity depending on whether `CatsToBIOConversions._` had been imported, AND the spec-mandated submerging never happened on the literal `Bifunctorized.bifunctorize(_)` call regardless.

**Reason (was):** three constraints appeared to make a single unconditional implementation impossible:
1. **Goal 4** ("bifunctorize is a no-op for real bifunctors — `bifunctorize(zio) eq zio` should hold"). A method that always submerged broke this for ZIO/MonixBIO/Either/MiniBIO.
2. **Goal 5** ("No More Orphans" — users without cats on the classpath must still use `Bifunctorized`). A method requiring `cats.ApplicativeError` would break the no-cats build (`Bifunctorized.scala` would have to import `cats.*`).
3. The spec's combined effect "any user reaching for `bifunctorize(io)` on a cats monofunctor sees typed-error semantics" was achieved indirectly by routing through the implicit-conversion seam in `CatsToBIOConversions._`.

**Remediation:** introduce a `Bifunctorize[F[_]]` typeclass that owns the
`F[A] <-> Bifunctorized[F, Throwable, A]` round-trip:

```scala
trait Bifunctorize[F[_]] {
  def bifunctorize[A](fa: F[A]): Bifunctorized[F, Throwable, A]
  def debifunctorize[A](b: Bifunctorized[F, Throwable, A]): F[A]
}
```

Two instances satisfy the three constraints simultaneously:
- **Identity** (in `Bifunctorize` companion via `LowPriorityBifunctorizeInstances`): reinterpret cast in both directions. Used for real bifunctors and any `F` without an `ApplicativeError` in scope. `Bifunctorize.scala` does NOT import cats, so it is reachable on a no-cats classpath (Goal 5).
- **Cats-mediated** (in `CatsToBIOConversions.bifunctorizeForCatsApplicativeError`): submerges via `F.adaptError`. Gated on `cats.ApplicativeError[F, Throwable]` and `TagK[F]`, with the "No-More-Orphans" trick (`@unused` phantom `\`cats.ApplicativeError\`` parameter from `izumi.fundamentals.orphans.OrphanDefs`) so users without cats on the classpath cannot resolve it and are not forced to depend on it (Goal 5).

Both the methods `Bifunctorized.bifunctorize` / `Bifunctorized.debifunctorize` AND the
implicit conversions `bifunctorizeConversion` / `debifunctorizeConversion` now take an
implicit `Bifunctorize[F]` and delegate — a single source of truth and a single conversion
level (no second cats-mediated implicit conversion in `CatsToBIOConversions`).

Resolution priority remains the standard Scala one: a cats-mediated `Bifunctorize[F]`
instance in the user's import scope (via `import CatsToBIOConversions.*`) outranks the
identity instance in the companion of `Bifunctorize`, so the spec-mandated submerging
fires uniformly across `bifunctorize(_)` method calls, implicit conversions, and
`.toMonofunctor` syntax. For real bifunctors (whose `ApplicativeError` is not in scope
via the cats conversion) and for any `F` without that import, the identity instance fires
and Goal 4 (`bifunctorize(realBifunctor) eq realBifunctor`) holds.

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
