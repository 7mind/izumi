package izumi.functional

import org.scalatest.GivenWhenThen
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

class CovariantHKTImplicitsBugTest extends AnyWordSpec with GivenWhenThen {

  "progression test: covariant HKT implicits are broken" in {
    And("quite broken")
    val res1 = intercept[TestFailedException](assertCompiles("""
        val alg: SomeAlg[IO] = SomeAlg.mk()
      """))
    assert(res1.getMessage contains "could not find implicit value")
    And("really broken")
    val res2 = intercept[TestFailedException](assertCompiles("""
        val alg: SomeAlg[IO] = SomeAlg.mk[IO]()
      """))
    assert(res2.getMessage contains "could not find implicit value")
  }

  trait MonoIO[F[_]]
  trait BifunctorIO[F[+_, _]]

  case class IO[+A]()
  object IO {
    implicit val monoInstance: MonoIO[IO] = new MonoIO[IO] {}
  }

  trait AnyIO[+F[_]]
  object AnyIO {
    implicit def fromMono[F[_]: MonoIO]: AnyIO[F] = new AnyIO[F] {}
    implicit def fromBIO[F[+_, _]: BifunctorIO]: AnyIO[F[Nothing, `?`]] = new AnyIO[F[Nothing, `?`]] {}
  }

  class SomeAlg[+F[_]]
  type SomeAlg2[F[_, _]] = SomeAlg[F[Nothing, `?`]]
  object SomeAlg {
    def mk[F[_]: AnyIO](): SomeAlg[F] = new SomeAlg[F]
  }

}
