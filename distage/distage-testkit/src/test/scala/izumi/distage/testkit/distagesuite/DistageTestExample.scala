package izumi.distage.testkit.distagesuite

import java.util.concurrent.TimeUnit

import zio.{Task, IO => ZIO}
import cats.effect.{IO => CIO}
import izumi.distage.testkit.distagesuite.fixtures.{ApplePaymentProvider, DynamoContainerDecl, MockCache, MockCachedUserService, MockUserRepository, PgContainerDecl, PgSvcExample}
import izumi.distage.testkit.services.st.dtest.{DistageAbstractScalatestSpec, TestConfig}
import izumi.distage.testkit.st.specs.{DistageBIOSpecScalatest, DistageSpecScalatest}
import distage._
import izumi.distage.testkit.services.dstest.TestEnvironment
import zio.clock.Clock
import zio.duration.Duration

trait DistageMemoizeExample[F[_]] { this: DistageAbstractScalatestSpec[F] =>
  override protected def config: TestConfig = {
    TestConfig(
      memoizedKeys = Set(
        DIKey.get[MockCache[CIO]],
        DIKey.get[MockCache[Task]],
      ))
  }
}

class DistageTestExampleBIO extends DistageBIOSpecScalatest[zio.IO] with DistageMemoizeExample[Task] {

  "distage test runner" should {
    "support bifunctor" in {
      service: MockUserRepository[Task] =>
        for {
          _ <- Task(assert(service != null))
        } yield ()
    }
  }

}

class DistageTestDockerBIO extends DistageBIOSpecScalatest[ZIO] {

  "distage test runner" should {
    "support docker resources" in {
      (service: PgSvcExample, clock: Clock) =>
        for {
          _ <- zio.ZIO.effect(println(s"ports: pg=${service.pg} ddb=${service.ddb} "))
        } yield ()
    }

    "support memoization" in {
      (service: PgSvcExample, clock: Clock) =>
        for {
          _ <- zio.ZIO.effect(println(s"ports: pg=${service.pg} ddb=${service.ddb} "))
        } yield ()
    }
  }

  override protected def config: TestConfig = {
    TestConfig(
      memoizedKeys = Set(
        DIKey.get[DynamoContainerDecl.Type],
        DIKey.get[PgContainerDecl.Type],
      ))
  }
}

class DistageTestExample extends DistageSpecScalatest[CIO] with DistageMemoizeExample[CIO] {
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

    "test 4 (should be ignored)" in {
      _: ApplePaymentProvider[CIO] =>
//        ???
    }
  }

}

class DistageTestExample1 extends DistageSpecScalatest[CIO] with DistageMemoizeExample[CIO] {

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
