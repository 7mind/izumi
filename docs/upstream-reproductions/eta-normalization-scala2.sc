//> using scala 2.13.18
//> using dep dev.zio::izumi-reflect:3.0.8
//> using dep org.typelevel::cats-effect:3.6.3
//> using plugin org.typelevel:::kind-projector:0.13.4

// izumi-reflect η-normalization repro on Scala 2.13 — same scenarios as the Scala 3 variant.

import izumi.reflect.{Tag, TagK, TagKK}
import izumi.reflect.macrortti.LightTypeTag
import cats.effect.IO

// Abstract higher-kinded shim mirroring izumi.functional.bio.Bifunctorized.
object BifOuter {
  type Bif[F[_], +E, +A]
}
import BifOuter.Bif

object BinaryShapeRepro {

  // Path A: direct macro — IO substituted concretely into the F[_] slot of Bif.
  def directTagKK: LightTypeTag = TagKK[Bif[IO, +*, +*]].tag

  // Path B: indirect — F[_] captured via TagK, then summon TagKK[Bif[F, +_, +_]].
  def indirectTagKK[F[_]: TagK]: LightTypeTag = TagKK[Bif[F, +*, +*]].tag

  // Path C: explicitly η-expand on the direct call side.
  def etaExpandedTagKK: LightTypeTag = TagKK[Bif[Lambda[x => IO[x]], +*, +*]].tag

  def main(args: Array[String]): Unit = {
    val a = directTagKK
    val b = indirectTagKK[IO]
    val c = etaExpandedTagKK

    println("=== izumi-reflect η-normalization on Scala 2.13 — binary-shape Bif[F[_], +_, +_] (izumi-reflect 3.0.8) ===")
    println()
    println("Path A: TagKK[Bif[IO, +*, +*]]                     (direct: IO substituted concretely)")
    println(s"  repr: ${a.repr}")
    println(s"  hashCode: ${a.hashCode}")
    println()
    println("Path B: indirectTagKK[IO] = TagKK[Bif[F, +*, +*]] where F=IO captured via TagK")
    println(s"  repr: ${b.repr}")
    println(s"  hashCode: ${b.hashCode}")
    println()
    println("Path C: TagKK[Bif[Lambda[x => IO[x]], +*, +*]]      (explicitly η-expanded type lambda)")
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
