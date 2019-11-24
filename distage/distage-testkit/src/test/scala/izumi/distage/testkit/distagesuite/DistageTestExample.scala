package izumi.distage.testkit.distagesuite

import cats.effect.{IO => CIO}
import distage._
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.testkit.distagesuite.fixtures.{ApplePaymentProvider, MockCache, MockCachedUserService, MockUserRepository}
import izumi.distage.testkit.services.st.dtest.{DistageAbstractScalatestSpec, TestConfig}
import izumi.distage.testkit.st.specs.{DistageBIOSpecScalatest, DistageSpecScalatest}
import izumi.fundamentals.platform.functional.Identity
import zio.Task

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

abstract class DistageTestExampleBase[F[_]: TagK](implicit F: DIEffect[F]) extends DistageSpecScalatest[F] with DistageMemoizeExample[F] {

  "distage test custom runner" should {
    "test 1" in {
      service: MockUserRepository[F] =>
        for {
          _ <- F.maybeSuspend(assert(service != null))
          _ <- F.maybeSuspend(println("test2"))
        } yield ()
    }

    "test 2" in {
      service: MockCachedUserService[F] =>
        for {
          _ <- F.maybeSuspend(assert(service != null))
          _ <- F.maybeSuspend(println("test1"))
        } yield ()
    }

    "test 3" in {
      service: MockCachedUserService[F] =>
        F.maybeSuspend(assert(service != null))
    }

    "test 4 (should be ignored)" in {
      _: ApplePaymentProvider[F] =>
        ???
    }

    "test 5 (should be ingored)" skip {
      _: MockCachedUserService[F] =>
       ???
    }

    "test 6 (should be ingored)" in {
      _: MockCachedUserService[F] =>
        assume(false)
    }
  }

}

//final class DistageTestExampleId extends DistageTestExampleBase[Identity]
//final class DistageTestExampleCIO extends DistageTestExampleBase[CIO]
final class DistageTestExampleZIO extends DistageTestExampleBase[Task]


abstract class DistageTestExampleBase1[F[_]: TagK](implicit F: DIEffect[F]) extends DistageSpecScalatest[F] with DistageMemoizeExample[F] {

  "distage test custom runner" should {
    "xtest 1" in {
      service: MockUserRepository[F] =>
        for {
          _ <- F.maybeSuspend(assert(service != null))
          _ <- F.maybeSuspend(println("xtest2"))
        } yield ()
    }

    "xtest 2" in {
      service: MockCachedUserService[F] =>
        for {
          _ <- F.maybeSuspend(assert(service != null))
          _ <- F.maybeSuspend(println("xtest1"))
        } yield ()
    }

    "xtest 3" in {
      service: MockCachedUserService[F] =>
        F.maybeSuspend(assert(service != null))
    }

    "xtest 4 (should be ignored)" in {
      _: ApplePaymentProvider[F] =>
        ???
    }

    "xtest 5 (should be ingored)" skip {
      _: MockCachedUserService[F] =>
        ???
    }

    "xtest 6 (should be ingored)" in {
      _: MockCachedUserService[F] =>
        assume(false)
    }
  }

}
final class DistageTestExampleZIO1 extends DistageTestExampleBase[Task]
