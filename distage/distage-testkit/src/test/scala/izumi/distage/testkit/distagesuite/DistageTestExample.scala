package izumi.distage.testkit.distagesuite

import zio.{IO => ZIO}
import cats.effect.{IO => CIO}
import izumi.distage.testkit.distagesuite.fixtures.{ApplePaymentProvider, MockCachedUserService, MockUserRepository}
import izumi.distage.testkit.st.specs.{DistageBIOSpecScalatest, DistageSpecScalatest}

class DistageTestExampleBIO extends DistageBIOSpecScalatest[ZIO] {

  "distage test runner" should {
    "support bifunctor" in {
      service: MockUserRepository[ZIO[Throwable, ?]] =>
        for {
          _ <- ZIO(assert(service != null))
        } yield ()
    }
  }

}

class DistageTestExample extends DistageSpecScalatest[CIO] {
  "distage test runner" should {
    "test 1" in {
      service: MockUserRepository[CIO] =>
        for {
          _ <- CIO.delay(assert(service != null))
          _ <- CIO.delay(println("test2"))
        } yield ()
    }

    "test 2" in {
      service: MockCachedUserService[CIO] =>
        for {
          _ <- CIO.delay(assert(service != null))
          _ <- CIO.delay(println("test1"))
        } yield ()
    }

    "test 3" in {
      service: MockCachedUserService[CIO] =>
        CIO.delay(assert(service != null))
    }

    "test 4" in {
      _: ApplePaymentProvider[CIO] =>
//        ???
    }
  }

}

class DistageTestExample1 extends DistageSpecScalatest[CIO] {

  "distage test custom runner" should {
    "test 1" in {
      service: MockUserRepository[CIO] =>
        for {
          _ <- CIO.delay(assert(service != null))
          _ <- CIO.delay(println("test2"))
        } yield ()
    }

    "test 2" in {
      service: MockCachedUserService[CIO] =>
        for {
          _ <- CIO.delay(assert(service != null))
          _ <- CIO.delay(println("test1"))
        } yield ()
    }

    "test 3" in {
      service: MockCachedUserService[CIO] =>
        CIO.delay(assert(service != null))
    }

    "test 4" in {
      _: ApplePaymentProvider[CIO] =>
        //???
    }
  }


}
