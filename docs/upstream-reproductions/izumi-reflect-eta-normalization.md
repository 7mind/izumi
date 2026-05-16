# izumi-reflect η-normalization investigation

## Status

**No deficiency reproduced in izumi-reflect.** The scenario described in `izumi/defects.md [M5-D01]` claimed that `LightTypeTag.<:<` rejects equivalence between `Bifunctorized[=cats.effect.IO, =0, =1]` and `Bifunctorized[=λ x => IO[x], =0, =1]`. Reduced to scala-cli reproducers on both Scala 2.13.18 and Scala 3.7.4, the comparison **passes** in all three cases tested: direct concrete substitution, indirect substitution via a captured `TagK[F]`, and explicit η-expansion via a type lambda.

The user's hypothesis from 2026-05-15 — "Bifunctorized[cats.effect.IO, _, _] and Bifunctorized[Lambda[x => IO[x]], _, _] should be equivalent in izumi-reflect in all cases" — is supported by this empirical evidence.

The 3 `CatsResourcesTestJvm` test failures attributed to M5-D01 in `defects.md` must have a different root cause. Suggested next investigation: inspect the actual LightTypeTag pair produced at the load-bearing call site (`ExecutableOp.scala:170-173`'s `isIncompatibleBifunctorEffectType`) at runtime, rather than reconstructing it from the macro source paths. The hypothesis that the two paths diverge appears to not hold in isolated repros.

## Reproducers

Three scala-cli scripts, each both Scala-version-explicit and runnable without an editor.

### 1. Scala 3 — simple `TagK` comparison

`eta-normalization-scala3.sc` — compares `TagK[cats.effect.IO]` against `TagK[[x] =>> cats.effect.IO[x]]`. Both produce identical `LightTypeTag` repr and hashCode; all three comparisons (`A <:< B`, `B <:< A`, `A =:= B`) return `true`.

Command: `scala-cli run docs/upstream-reproductions/eta-normalization-scala3.sc`

Output:
```
=== izumi-reflect η-normalization on Scala 3 (izumi-reflect 3.0.8) ===

Path A: TagK[cats.effect.IO]
  repr: λ %0 → cats.effect.IO[+0]
  hashCode: 1416592205

Path B: TagK[[x] =>> cats.effect.IO[x]]  (η-expanded type lambda)
  repr: λ %0 → cats.effect.IO[+0]
  hashCode: 1416592205

=== Comparison ===
  A <:< B  : true
  B <:< A  : true
  A =:= B  : true

RESULT: PASS — both representations compare equal.
```

### 2. Scala 3 — abstract higher-kinded shim with indirect substitution

`eta-normalization-scala3-indirect.sc` — mirrors the M5-D01 scenario by introducing a `MyBif[F[_], A]` abstract type and comparing direct vs. indirect macro substitution paths. The "indirect" path captures `TagK[F]` via a context bound and substitutes into `MyBif[F, Int]`; the "direct" path writes `MyBif[IO, Int]` concretely.

Output:
```
=== izumi-reflect η-normalization on Scala 3 (indirect substitution, izumi-reflect 3.0.8) ===

Path A: direct — Tag[MyBif[IO, Int]] (IO written concretely)
  repr: <prefix>::MyBifOuter::MyBifAbs[=λ %0 → cats.effect.IO[+0],=scala.Int]
  hashCode: -1813686372

Path B: indirect — withCapturedTagK[IO] producing Tag[MyBif[F, Int]] where F=IO
  repr: <prefix>::MyBifOuter::MyBifAbs[=λ %0 → cats.effect.IO[+0],=scala.Int]
  hashCode: -1813686372

=== Comparison ===
  direct <:< indirect : true
  indirect <:< direct : true
  direct =:= indirect : true

RESULT: PASS — both representations compare equal (direct and indirect macro paths produce equivalent LightTypeTags).
```

### 3. Scala 3 — binary-shape `Bif[F[_], +E, +A]` (matches `izumi.functional.bio.Bifunctorized`)

`eta-normalization-scala3-binary.sc` — closest to the actual production shape. The abstract type `Bif[F[_], +E, +A]` mirrors `izumi.functional.bio.Bifunctorized`. We compare three derivations:
- **Path A (direct)**: `TagKK[[E, A] =>> Bif[IO, E, A]]` — IO concrete in source.
- **Path B (indirect)**: `def indirectTagKK[F[_]: TagK]: TagKK[[E, A] =>> Bif[F, E, A]]`, called with `IO` — F captured via context-bound TagK.
- **Path C (η-expanded)**: `TagKK[[E, A] =>> Bif[[x] =>> IO[x], E, A]]` — explicit η-expansion at the source.

Output:
```
=== izumi-reflect η-normalization on Scala 3 — binary-shape Bif[F[_], +_, +_] (izumi-reflect 3.0.8) ===

Path A: TagKK[[E, A] =>> Bif[IO, E, A]]            (direct: IO substituted concretely)
  repr: λ %0,%1 → <prefix>::BifOuter::BifAbs[=λ %1:0 → cats.effect.IO[+1:0],=0,=1]
  hashCode: 1786931593

Path B: indirectTagKK[IO] = TagKK[[E, A] =>> Bif[F, E, A]] where F=IO captured via TagK
  repr: λ %1,%2 → <prefix>::BifOuter::BifAbs[=λ %0 → cats.effect.IO[+0],=1,=2]
  hashCode: 1786931593

Path C: TagKK[[E, A] =>> Bif[[x] =>> IO[x], E, A]] (explicitly η-expanded type lambda)
  repr: λ %0,%1 → <prefix>::BifOuter::BifAbs[=λ %1:0 → cats.effect.IO[+1:0],=0,=1]
  hashCode: 1786931593

=== Comparison ===
  A <:< B : true   (direct <:< indirect)
  B <:< A : true   (indirect <:< direct)
  A <:< C : true   (direct <:< η-expanded)
  C <:< A : true   (η-expanded <:< direct)
  B <:< C : true   (indirect <:< η-expanded)
  C <:< B : true   (η-expanded <:< indirect)
  A =:= B : true
  A =:= C : true
  B =:= C : true

RESULT: PASS — all three representations compare equal under LightTypeTag.
```

Note: Paths B and C differ in the `repr` only in the **bound-variable names** (`%0,%1` vs `%1,%2`) — this is α-equivalent, and `LightTypeTag.<:<`/`=:=` correctly treat them as equal (hashCodes are identical, `<:<` returns `true` in both directions).

### 4. Scala 3 — distage M5-D01 direct simulation

`eta-normalization-scala3-distage.sc` — reconstructs the exact scenario from `defects.md [M5-D01]`. The "binding side" path mirrors `LifecycleAdapters.providerFromCatsProvider[F[_]: TagK, A]` — captured `TagK[IO]` is substituted into `Bif[F, +_, +_]`. The "injector side" path mirrors `Injector[Bif[IO, +_, +_]]()`'s direct `TagKK[Bif[IO, +_, +_]]`.

Output:
```
=== M5-D01 simulation: binding side (captured TagK[IO]) vs injector side (direct TagKK[Bif[IO, +_, +_]]) ===

Binding side: bindingSideEffectHKTypeCtor[IO]
  repr: λ %1,%2 → <prefix>::BifOuter::Bif[=λ %0 → cats.effect.IO[+0],=1,=2]
  hashCode: -900949201

Injector side: TagKK[[E, A] =>> Bif[IO, E, A]]
  repr: λ %0,%1 → <prefix>::BifOuter::Bif[=λ %1:0 → cats.effect.IO[+1:0],=0,=1]
  hashCode: -900949201

=== Comparison (mimics ExecutableOp.isIncompatibleBifunctorEffectType) ===
  bindingEffectType <:< injectorEffectType : true
  injectorEffectType <:< bindingEffectType : true
  bindingEffectType =:= injectorEffectType : true

RESULT: PASS — binding and injector side tags compare equal. M5-D01 hypothesis is NOT reproduced. The actual `CatsResourcesTestJvm` failures must have a different proximate cause.
```

Note the printed `repr` strings differ in **bound-variable names** (`%0,%1` vs. `%1,%2`) and inner-IO variable indices (`%0` vs. `%1:0`). These are α-equivalent. The `hashCode` is identical and `<:<`/`=:=` correctly return `true` in both directions — confirming izumi-reflect handles α-equivalence at this site.

### 5. Scala 2.13 — same binary-shape repro

`eta-normalization-scala2.sc` — same comparison on Scala 2.13.18 with `kind-projector` for the `+*,+*` and `Lambda[x => …]` syntax.

Output:
```
=== izumi-reflect η-normalization on Scala 2.13 — binary-shape Bif[F[_], +_, +_] (izumi-reflect 3.0.8) ===

Path A: TagKK[Bif[IO, +*, +*]]                     (direct: IO substituted concretely)
  repr: λ %0,%1 → <prefix>::BifOuter::Bif[=λ %2:0 → cats.effect.IO[+2:0],+0,+1]
  hashCode: -264071434

Path B: indirectTagKK[IO] = TagKK[Bif[F, +*, +*]] where F=IO captured via TagK
  repr: λ %1,%2 → <prefix>::BifOuter::Bif[=λ %0 → cats.effect.IO[+0],+1,+2]
  hashCode: -264071434

Path C: TagKK[Bif[Lambda[x => IO[x]], +*, +*]]      (explicitly η-expanded type lambda)
  repr: λ %0,%1 → <prefix>::BifOuter::Bif[=λ %2:0 → cats.effect.IO[+2:0],+0,+1]
  hashCode: -264071434

=== Comparison ===
  A <:< B : true   (direct <:< indirect)
  B <:< A : true   (indirect <:< direct)
  A <:< C : true   (direct <:< η-expanded)
  C <:< A : true   (η-expanded <:< direct)
  B <:< C : true   (indirect <:< η-expanded)
  C <:< B : true   (η-expanded <:< indirect)
  A =:= B : true
  A =:= C : true
  B =:= C : true

RESULT: PASS — all three representations compare equal under LightTypeTag.
```

## Summary

| Repro | Scala | izumi-reflect | Direct ↔ Indirect | Direct ↔ η-expanded | Indirect ↔ η-expanded |
| --- | --- | --- | --- | --- | --- |
| simple `TagK[IO]` vs `TagK[[x] =>> IO[x]]` | 3.7.4 | 3.0.8 | PASS | n/a (only two paths) | n/a |
| `MyBif[F[_], A]` direct vs indirect | 3.7.4 | 3.0.8 | PASS | n/a | n/a |
| `Bif[F[_], +E, +A]` (3 paths) | 3.7.4 | 3.0.8 | PASS | PASS | PASS |
| M5-D01 distage simulation | 3.7.4 | 3.0.8 | PASS | n/a | n/a |
| `Bif[F[_], +E, +A]` (3 paths) | 2.13.18 | 3.0.8 | PASS | PASS | PASS |

## Implication for `defects.md [M5-D01]`

The M5-D01 root-cause hypothesis — that izumi-reflect's `LightTypeTag.<:<` fails η-normalization between the direct and indirect macro substitution paths — is **not reproducible** in isolated scala-cli scripts on either Scala 2.13 or Scala 3 against izumi-reflect 3.0.8.

The 3 `CatsResourcesTestJvm` failures that motivated M5-D01 must therefore have a different proximate cause. Suggested next investigation:

1. Re-run the original failure scenario with logging inserted at `ExecutableOp.scala:170-173`. Capture the actual two `SafeType` values whose `<:<` returns `false`. Confirm their `tag.repr` strings.
2. If the captured tags have identical `repr` but compare unequal, the bug is in `LightTypeTag.<:<`'s structural comparison (not η-normalization).
3. If the captured tags have **different** `repr` strings, the bug is in how those tags are constructed at the binding site or the injector-side site — and is in the distage / izumi-bio code, not izumi-reflect.

## Files in this directory

- `eta-normalization-scala3.sc` — basic `TagK[IO]` vs `TagK[[x] =>> IO[x]]`
- `eta-normalization-scala3-indirect.sc` — `MyBif[F[_], A]` direct vs indirect substitution
- `eta-normalization-scala3-binary.sc` — full `Bif[F[_], +E, +A]` shape (production-equivalent)
- `eta-normalization-scala3-distage.sc` — closest simulation of `defects.md [M5-D01]` (binding-side captured TagK vs. injector-side direct TagKK)
- `eta-normalization-scala2.sc` — `Bif[F[_], +E, +A]` shape on Scala 2.13.18
- `izumi-reflect-eta-normalization.md` — this document
