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
