//> using scala 3.7.4
//> using dep dev.zio::izumi-reflect:3.0.8
//> using dep org.typelevel::cats-effect:3.6.3

// Closer simulation of the actual M5-D01 scenario.
//
// At binding time, distage's `make[T].fromResource(catsResource: Resource[IO, T])` calls
// `LifecycleAdapters.providerFromCatsProvider[F[_]: TagK, A]` (LifecycleAdapters.scala:56)
// which captures `TagK[IO]` and synthesizes a `Lifecycle.FromCats[IO, A]` extending
// `Lifecycle[Bifunctorized[IO, +_, +_], Throwable, A]`. The binding stores
// `effectHKTypeCtor: SafeType` derived via `LifecycleTag` from F.
//
// At injection time, `Injector[Bifunctorized[IO, +_, +_]]()` provides
// `TagKK[Bifunctorized[IO, +_, +_]]` directly.
//
// The check `actionEffectType <:< SafeType.getKK[F]` (ExecutableOp.scala:170-173) compares
// the two. defects.md M5-D01 hypothesized that they diverge — the binding-side tag stores
// "IO" unexpanded while the injector-side tag has "λ x => IO[x]" η-expanded, and
// LightTypeTag.<:< fails to normalize.
//
// This repro reconstructs both paths and exercises the comparison.

import izumi.reflect.{Tag, TagK, TagKK}
import izumi.reflect.macrortti.LightTypeTag
import cats.effect.IO

// Stand-in for izumi.functional.bio.Bifunctorized.
object BifOuter {
  type Bif[F[_], +E, +A]
}
import BifOuter.Bif

object DistageM5D01Repro {

  // Binding side: this mimics `providerFromCatsProvider[F[_]: TagK, A]`'s capture and
  // subsequent `TagKK[Bif[F, +_, +_]]` derivation through a context-bound TagK[F].
  def bindingSideEffectHKTypeCtor[F[_]: TagK]: LightTypeTag = TagKK[[E, A] =>> Bif[F, E, A]].tag

  // Injector side: direct TagKK[Bif[IO, +_, +_]].
  def injectorSideEffectHKTypeCtor: LightTypeTag = TagKK[[E, A] =>> Bif[IO, E, A]].tag

  def main(args: Array[String]): Unit = {
    val binding = bindingSideEffectHKTypeCtor[IO]
    val injector = injectorSideEffectHKTypeCtor

    println("=== M5-D01 simulation: binding side (captured TagK[IO]) vs injector side (direct TagKK[Bif[IO, +_, +_]]) ===")
    println()
    println("Binding side: bindingSideEffectHKTypeCtor[IO]")
    println(s"  repr: ${binding.repr}")
    println(s"  hashCode: ${binding.hashCode}")
    println()
    println("Injector side: TagKK[[E, A] =>> Bif[IO, E, A]]")
    println(s"  repr: ${injector.repr}")
    println(s"  hashCode: ${injector.hashCode}")
    println()

    val bLeqI = binding <:< injector
    val iLeqB = injector <:< binding
    val bEqI  = binding =:= injector

    println("=== Comparison (mimics ExecutableOp.isIncompatibleBifunctorEffectType) ===")
    println(s"  bindingEffectType <:< injectorEffectType : $bLeqI")
    println(s"  injectorEffectType <:< bindingEffectType : $iLeqB")
    println(s"  bindingEffectType =:= injectorEffectType : $bEqI")
    println()

    val ok = bLeqI && iLeqB && bEqI
    println(
      if (ok) {
        "RESULT: PASS — binding and injector side tags compare equal. M5-D01 hypothesis is NOT reproduced. The actual `CatsResourcesTestJvm` failures must have a different proximate cause."
      } else {
        "RESULT: FAIL — binding and injector side tags do NOT compare equal. M5-D01 hypothesis is reproduced — this is a real izumi-reflect deficiency."
      }
    )

    System.exit(if (ok) 0 else 1)
  }

}

DistageM5D01Repro.main(Array.empty)
