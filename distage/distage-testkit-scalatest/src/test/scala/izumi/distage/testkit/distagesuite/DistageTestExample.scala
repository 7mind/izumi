package izumi.distage.testkit.distagesuite

import java.util.concurrent.atomic.AtomicReference

import cats.effect.{IO => CIO}
import distage._
import izumi.distage.framework.model.PluginSource
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.distagesuite.fixtures.{ApplePaymentProvider, MockCache, MockCachedUserService, MockUserRepository}
import izumi.distage.testkit.scalatest.{DistageBIOSpecScalatest, DistageSpecScalatest}
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.fundamentals.platform.functional.Identity
import zio.Task

trait DistageMemoizeExample[F[_]] extends DistageAbstractScalatestSpec[F] {
  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots = Set(
        DIKey.get[MockCache[F]],
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

  override protected def config: TestConfig = super.config.copy(
      pluginSource = super.config.pluginSource ++ PluginSource(PluginConfig.cached(Seq("xxx")))
    )

  val XXX_Whitebox_memoizedMockCache = new AtomicReference[MockCache[F]]

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
          _ <- F.maybeSuspend(XXX_Whitebox_memoizedMockCache.compareAndSet(null, service.cache))
          _ <- F.maybeSuspend(assert(service != null))
          _ <- F.maybeSuspend(assert(service.cache eq XXX_Whitebox_memoizedMockCache.get()))
          _ <- F.maybeSuspend(println("test1"))
        } yield ()
    }

    "test 3" in {
      service: MockCachedUserService[F] =>
        F.maybeSuspend {
          XXX_Whitebox_memoizedMockCache.compareAndSet(null, service.cache)
          assert(service != null)
          assert(service.cache eq XXX_Whitebox_memoizedMockCache.get())
        }
    }

    "test 4 (should be ignored)" in {
      _: ApplePaymentProvider[F] =>
        ???
    }

    "test 5 (should be ignored)" skip {
      _: MockCachedUserService[F] =>
        ???
    }

    "test 6 (should be ignored)" in {
      _: MockCachedUserService[F] =>
        assume(false, "xxx")
    }
  }

}



final class DistageTestExampleId extends DistageTestExampleBase[Identity]
final class DistageTestExampleCIO extends DistageTestExampleBase[CIO]
final class DistageTestExampleZIO extends DistageTestExampleBase[Task]

abstract class DistageSleepTest[F[_]: TagK](implicit F: DIEffect[F]) extends DistageSpecScalatest[F] with DistageMemoizeExample[F] {
  "distage test" should {
    "sleep" in {
      service: MockUserRepository[F] =>
        for {
          _ <- F.maybeSuspend(Thread.sleep(100))
        } yield ()
    }
  }
}

final class DistageSleepTest01 extends DistageSleepTest[CIO]
final class DistageSleepTest02 extends DistageSleepTest[CIO]
final class DistageSleepTest03 extends DistageSleepTest[CIO]
final class DistageSleepTest04 extends DistageSleepTest[CIO]
final class DistageSleepTest05 extends DistageSleepTest[CIO]
final class DistageSleepTest06 extends DistageSleepTest[CIO]
final class DistageSleepTest07 extends DistageSleepTest[CIO]
final class DistageSleepTest08 extends DistageSleepTest[CIO]
final class DistageSleepTest09 extends DistageSleepTest[CIO]
final class IdentityDistageSleepTest01 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest02 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest03 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest04 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest05 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest06 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest07 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest08 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest09 extends DistageSleepTest[Identity]
final class TaskDistageSleepTest01 extends DistageSleepTest[Task]
final class TaskDistageSleepTest02 extends DistageSleepTest[Task]
final class TaskDistageSleepTest03 extends DistageSleepTest[Task]
final class TaskDistageSleepTest04 extends DistageSleepTest[Task]
final class TaskDistageSleepTest05 extends DistageSleepTest[Task]
final class TaskDistageSleepTest06 extends DistageSleepTest[Task]
final class TaskDistageSleepTest07 extends DistageSleepTest[Task]
final class TaskDistageSleepTest08 extends DistageSleepTest[Task]
final class TaskDistageSleepTest09 extends DistageSleepTest[Task]