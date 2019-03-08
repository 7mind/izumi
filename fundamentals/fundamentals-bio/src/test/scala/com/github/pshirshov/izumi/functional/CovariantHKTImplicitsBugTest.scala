package com.github.pshirshov.izumi.functional

import org.scalatest.{GivenWhenThen, WordSpec}

class CovariantHKTImplicitsBugTest extends WordSpec with GivenWhenThen {

  "progression test: covariant HKT implicits are broken" in {
    And("quite broken")
    assertTypeError(
      """
        val alg: SomeAlg[IO] = SomeAlg.mk()
      """)
    And("really broken")
    assertTypeError(
      """
        val alg: SomeAlg[IO] = SomeAlg.mk[IO]()
      """)
  }

  trait MonoIO[F[_]]
  trait BifunctorIO[F[+_, _]]

  case class IO[+A]()
  object IO {
    implicit val monoInstance: MonoIO[IO] = new MonoIO[IO]{}
  }

  trait SomeIO[+F[_]]
  object SomeIO {
    implicit def fromMono[F[_]: MonoIO]: SomeIO[F] = new SomeIO[F]{}
    implicit def fromBIO[F[+_, _]: BifunctorIO]: SomeIO[F[Nothing, ?]] = new SomeIO[F[Nothing, ?]]{}
  }

  class SomeAlg[+F[_]]
  type SomeAlg2[F[_, _]] = SomeAlg[F[Nothing, ?]]
  object SomeAlg {
    def mk[F[_]: SomeIO](): SomeAlg[F] = new SomeAlg[F]
  }


}
