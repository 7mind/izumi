package izumi.fundamentals.bio.test

import izumi.functional.bio._
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Task, ZIO}

class BIOSoundnessTest extends AnyWordSpec {
  /*
    There's a `<: BIOFunctor3[f]` constraint now in BIOConvert3To2
    to prevent trifunctorial classes from being converted to bifunctorial,
    but it's still circumventable by inheriting from BIOFunctor + Trifunctor
    hierarchies in one implicit instance, like `BIOAsync[F] with BIOLocal[F]`.

    A solution would be to add a marker type member inside instances:

    trait FunctorialityHelper {
      type Functoriality
    }
    object FunctorialityHelper {
      type Monofunctorial
      type Bifunctorial <: Monofunctorial
      type Trifunctorial <: Bifunctorial
    }

    And then add a LUB guard to check that type member is _no more specific_
    than `Bifunctorial`: `(implicit guard: Lub[F#Functoriality, Trifunctorial, Bifunctorial])`
  */

  type BIOArrow2[F[+_, +_]] = BIOArrow[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type FR[F[-_, +_, +_], R] = { type l[+E, +A] = F[R, E, A] }

  "Cannot convert polymorphic BIOArrow into a bifunctor typeclass (normally)" in {
    val res = intercept[TestFailedException](assertCompiles(
      """
      def valueF[F[-_, +_, +_]: BIOArrow: BIOMonadAsk: BIO3] = {
        val FA: BIOArrow2[FR[F, Int]#l] = implicitly[BIOArrow2[FR[F, Int]#l]]
        FA.andThen(F.unit, F.access((i: Int) => F.sync(println(i))))
      }
      zio.Runtime.default.unsafeRun(valueF[ZIO].provide(1))
      """))
    assert(res.getMessage contains "could not find implicit value for parameter")
    assert(res.getMessage contains "BIOArrow2")
  }

  "Cannot convert ZIO BIOArrow instance into a bifunctor typeclass (normally)" in {
    val res = intercept[TestFailedException](assertCompiles(
    """
    def valueZIO = {
      val F: BIOArrow2[FR[ZIO, Int]#l] = implicitly[BIOArrow2[FR[ZIO, Int]#l]]
      F.andThen(IO.unit, ZIO.accessM[Int](i => Task(println(i))))
    }
    zio.Runtime.default.unsafeRun(valueZIO.provide(1))
    """))
    assert(res.getMessage contains "could not find implicit value for parameter")
    assert(res.getMessage contains "BIOArrow2")
  }
}
