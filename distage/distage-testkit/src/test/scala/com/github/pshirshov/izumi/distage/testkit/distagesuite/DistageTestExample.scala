package com.github.pshirshov.izumi.distage.testkit.distagesuite

import cats.effect.IO
import com.github.pshirshov.izumi.distage.testkit.distagesuite.fixtures.{ApplePaymentProvider, MockCachedUserService, MockUserRepository}
import com.github.pshirshov.izumi.distage.testkit.DistageSpecScalatest


class DistageTestExample extends DistageSpecScalatest[IO]  {

  "distage test runner" should {
    "test 1" in {
      service: MockUserRepository[IO] =>
        for {
          _ <- IO.delay(assert(service != null))
          _ <- IO.delay(println("test2"))
        } yield {

        }
    }

    "test 2" in {
      service: MockCachedUserService[IO] =>
        for {
          _ <- IO.delay(assert(service != null))
          _ <- IO.delay(println("test1"))
        } yield {

        }
    }

    "test 3" in {
      service: MockCachedUserService[IO] =>
        IO.delay(assert(service != null))
    }

    "test 4" in {
      _: ApplePaymentProvider[IO] =>
        ???
    }
  }

}

class DistageTestExample1 extends DistageSpecScalatest[IO] {

  "distage test custom runner" should {
    "test 1" in {
      service: MockUserRepository[IO] =>
        for {
          _ <- IO.delay(assert(service != null))
          _ <- IO.delay(println("test2"))
        } yield {

        }
    }

    "test 2" in {
      service: MockCachedUserService[IO] =>
        for {
          _ <- IO.delay(assert(service != null))
          _ <- IO.delay(println("test1"))
        } yield {

        }
    }

    "test 3" in {
      service: MockCachedUserService[IO] =>
        IO.delay(assert(service != null))
    }

    "test 4" in {
      _: ApplePaymentProvider[IO] =>
        ???
    }
  }


}
