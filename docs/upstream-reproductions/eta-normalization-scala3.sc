//> using scala 3.7.4
//> using dep dev.zio::izumi-reflect:3.0.8
//> using dep org.typelevel::cats-effect:3.6.3

// izumi-reflect η-normalization repro on Scala 3.
//
// Question: are `Tag[cats.effect.IO]` (where cats.effect.IO's kind is `[+_]`) and
// `Tag[Lambda[x => cats.effect.IO[x]]]` (η-expanded) equal under `LightTypeTag.=:=` ?
//
// Both denote the same Scala type and should compare equal. The defect previously
// documented in izumi/defects.md M5-D01 claimed they do not.
//
// This repro builds both representations and reports the comparison results.

import izumi.reflect.{Tag, TagK}
import izumi.reflect.macrortti.{LightTypeTag, LTag, LightTypeTagRef}
import cats.effect.IO

object EtaNormRepro {

  // Path A: TagK[IO] — captured via context bound, no η-expansion needed because IO already has kind [+_]
  val tagA: TagK[IO] = TagK[IO]
  val lttA: LightTypeTag = tagA.tag

  // Path B: TagK[[x] =>> IO[x]] — explicitly η-expanded type lambda
  val tagB: TagK[[x] =>> IO[x]] = TagK[[x] =>> IO[x]]
  val lttB: LightTypeTag = tagB.tag

  def main(args: Array[String]): Unit = {
    println("=== izumi-reflect η-normalization on Scala 3 (izumi-reflect 3.0.8) ===")
    println()
    println("Path A: TagK[cats.effect.IO]")
    println(s"  repr: ${lttA.repr}")
    println(s"  hashCode: ${lttA.hashCode}")
    println()
    println("Path B: TagK[[x] =>> cats.effect.IO[x]]  (η-expanded type lambda)")
    println(s"  repr: ${lttB.repr}")
    println(s"  hashCode: ${lttB.hashCode}")
    println()

    val abLeq = lttA <:< lttB
    val baLeq = lttB <:< lttA
    val abEq  = lttA =:= lttB

    println("=== Comparison ===")
    println(s"  A <:< B  : $abLeq")
    println(s"  B <:< A  : $baLeq")
    println(s"  A =:= B  : $abEq")
    println()
    val ok = abLeq && baLeq && abEq
    println(if (ok) "RESULT: PASS — both representations compare equal." else "RESULT: FAIL — the two representations do not compare equal under LightTypeTag.")

    System.exit(if (ok) 0 else 1)
  }

}

EtaNormRepro.main(Array.empty)
