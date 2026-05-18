//> using scala 3.7.4
//> using dep dev.zio::izumi-reflect:3.0.8
//> using dep org.typelevel::cats-effect:3.6.3

// izumi-reflect η-normalization repro on Scala 3 — binary-shape abstract type case.
//
// Mirrors the exact `Bifunctorized[F[_], +E, +A]` shape used by izumi/bio: an
// abstract type with kind `[F[_], +_, +_]` (mono-F-mono-binary-bif). We compare:
//   - direct: `TagKK[Bifunctorized[IO, +_, +_]]` (where IO is concrete)
//   - indirect: `withCapturedTagK[IO]` → `TagKK[Bifunctorized[F, +_, +_]]` (F captured from outer scope)
//
// In Scala 3, this case is where M5-D01 reports the LightTypeTag inequivalence.

import izumi.reflect.{Tag, TagK, TagKK}
import izumi.reflect.macrortti.LightTypeTag
import cats.effect.IO

// Abstract higher-kinded shim mirroring izumi.functional.bio.Bifunctorized.
type Bif[F[_], +E, +A] = BifOuter.BifAbs[F, E, A]

object BifOuter {
  type BifAbs[F[_], +E, +A]
}

object BinaryShapeRepro {

  // Path A: direct macro — IO substituted concretely into the F[_] slot of Bif.
  def directTagKK: LightTypeTag = TagKK[[E, A] =>> Bif[IO, E, A]].tag

  // Path B: indirect — F[_] captured via TagK, then summon TagKK[Bif[F, +_, +_]].
  def indirectTagKK[F[_]: TagK]: LightTypeTag = TagKK[[E, A] =>> Bif[F, E, A]].tag

  // Path C: explicitly η-expand on the direct call side.
  def etaExpandedTagKK: LightTypeTag = TagKK[[E, A] =>> Bif[[x] =>> IO[x], E, A]].tag

  def main(args: Array[String]): Unit = {
    val a = directTagKK
    val b = indirectTagKK[IO]
    val c = etaExpandedTagKK

    println("=== izumi-reflect η-normalization on Scala 3 — binary-shape Bif[F[_], +_, +_] (izumi-reflect 3.0.8) ===")
    println()
    println("Path A: TagKK[[E, A] =>> Bif[IO, E, A]]            (direct: IO substituted concretely)")
    println(s"  repr: ${a.repr}")
    println(s"  hashCode: ${a.hashCode}")
    println()
    println("Path B: indirectTagKK[IO] = TagKK[[E, A] =>> Bif[F, E, A]] where F=IO captured via TagK")
    println(s"  repr: ${b.repr}")
    println(s"  hashCode: ${b.hashCode}")
    println()
    println("Path C: TagKK[[E, A] =>> Bif[[x] =>> IO[x], E, A]] (explicitly η-expanded type lambda)")
    println(s"  repr: ${c.repr}")
    println(s"  hashCode: ${c.hashCode}")
    println()

    val abLeq = a <:< b
    val baLeq = b <:< a
    val acLeq = a <:< c
    val caLeq = c <:< a
    val bcLeq = b <:< c
    val cbLeq = c <:< b

    println("=== Comparison ===")
    println(s"  A <:< B : $abLeq   (direct <:< indirect)")
    println(s"  B <:< A : $baLeq   (indirect <:< direct)")
    println(s"  A <:< C : $acLeq   (direct <:< η-expanded)")
    println(s"  C <:< A : $caLeq   (η-expanded <:< direct)")
    println(s"  B <:< C : $bcLeq   (indirect <:< η-expanded)")
    println(s"  C <:< B : $cbLeq   (η-expanded <:< indirect)")
    println(s"  A =:= B : ${a =:= b}")
    println(s"  A =:= C : ${a =:= c}")
    println(s"  B =:= C : ${b =:= c}")
    println()
    val ok = abLeq && baLeq && acLeq && caLeq && bcLeq && cbLeq
    println(
      if (ok) "RESULT: PASS — all three representations compare equal under LightTypeTag."
      else "RESULT: FAIL — at least one pair of representations does not compare equal under LightTypeTag."
    )

    System.exit(if (ok) 0 else 1)
  }

}

BinaryShapeRepro.main(Array.empty)
