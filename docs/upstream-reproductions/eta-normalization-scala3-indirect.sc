//> using scala 3.7.4
//> using dep dev.zio::izumi-reflect:3.0.8
//> using dep org.typelevel::cats-effect:3.6.3

// izumi-reflect η-normalization repro on Scala 3 (indirect substitution path).
//
// This repro reconstructs the scenario from izumi/defects.md M5-D01 — where the
// macro substitutes a captured TagK[F] into a higher-kinded slot of an abstract
// type, vs. the direct macro path where the type is given concretely.
//
// We use a stand-in `MyBif[F[_], A]` abstract type (mirroring izumi-bio's
// `Bifunctorized[F[_], E, A]` shape) and compare:
//   - direct: `Tag[MyBif[IO, Int]]` summoned with `IO` written concretely.
//   - indirect: `withCapturedTagK[IO]` summons `Tag[MyBif[F, Int]]` where F = IO
//     was bound via `def withCapturedTagK[F[_]: TagK]`.

import izumi.reflect.{Tag, TagK}
import izumi.reflect.macrortti.LightTypeTag
import cats.effect.IO

// Abstract higher-kinded shim — kind [_] for the F slot.
type MyBif[F[_], A] = MyBifOuter.MyBifAbs[F, A]

object MyBifOuter {
  type MyBifAbs[F[_], A]
}

object IndirectEtaRepro {

  // Path A: direct macro — IO substituted at the call site.
  def directTag: LightTypeTag = Tag[MyBif[IO, Int]].tag

  // Path B: indirect macro — IO substituted via captured TagK[F].
  def indirectTag[F[_]: TagK]: LightTypeTag = Tag[MyBif[F, Int]].tag

  def main(args: Array[String]): Unit = {
    val direct = directTag
    val indirect = indirectTag[IO]

    println("=== izumi-reflect η-normalization on Scala 3 (indirect substitution, izumi-reflect 3.0.8) ===")
    println()
    println("Path A: direct — Tag[MyBif[IO, Int]] (IO written concretely)")
    println(s"  repr: ${direct.repr}")
    println(s"  hashCode: ${direct.hashCode}")
    println()
    println("Path B: indirect — withCapturedTagK[IO] producing Tag[MyBif[F, Int]] where F=IO")
    println(s"  repr: ${indirect.repr}")
    println(s"  hashCode: ${indirect.hashCode}")
    println()

    val abLeq = direct <:< indirect
    val baLeq = indirect <:< direct
    val abEq  = direct =:= indirect

    println("=== Comparison ===")
    println(s"  direct <:< indirect : $abLeq")
    println(s"  indirect <:< direct : $baLeq")
    println(s"  direct =:= indirect : $abEq")
    println()
    val ok = abLeq && baLeq && abEq
    println(
      if (ok) "RESULT: PASS — both representations compare equal (direct and indirect macro paths produce equivalent LightTypeTags)."
      else "RESULT: FAIL — direct and indirect macro paths produce LightTypeTags that do not compare equal."
    )

    System.exit(if (ok) 0 else 1)
  }

}

IndirectEtaRepro.main(Array.empty)
