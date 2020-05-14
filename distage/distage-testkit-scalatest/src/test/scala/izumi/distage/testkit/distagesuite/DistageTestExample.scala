package izumi.distage.testkit.distagesuite

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import cats.effect.{IO => CIO}
import distage._
import distage.plugins.PluginDef
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.distagesuite.DistageTestExampleBase._
import izumi.distage.testkit.distagesuite.fixtures.{ActiveComponent, ApplePaymentProvider, ForcedRootProbe, ForcedRootResource, MockCache, MockCachedUserService, MockUserRepository, TestActiveComponent}
import izumi.distage.testkit.scalatest.{AssertIO, DistageBIOEnvSpecScalatest, DistageBIOSpecScalatest, DistageSpecScalatest}
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.platform.language.Quirks._
import org.scalatest.exceptions.TestFailedException
import zio.{Has, Task, ZIO}

trait DistageMemoizeExample[F[_]] extends DistageAbstractScalatestSpec[F] {
  override protected def config: TestConfig = {
    super
      .config.copy(
        memoizationRoots = Set(
          DIKey.get[MockCache[F]],
          DIKey.get[Set[SetElement]],
          DIKey.get[SetCounter],
        )
      )
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

class DistageTestExampleBIOEnv extends DistageBIOEnvSpecScalatest[ZIO] with DistageMemoizeExample[Task] with AssertIO {

  val service = ZIO.access[Has[MockUserRepository[Task]]](_.get)

  "distage test runner" should {
    "support trifunctor env" in {
      for {
        service <- service
        _ <- assertIO(service != null)
      } yield ()
    }

    "support empty env" in {
      assertIO(true)
    }

    "support mixing parameters & env" in {
      cached: MockCachedUserService[Task] =>
        for {
          service <- service
          _ <- assertIO(cached != null)
          _ <- assertIO(service != null)
        } yield ()
    }
  }

}

object DistageTestExampleBase {
  class SetCounter {
    private[this] val c: AtomicInteger = new AtomicInteger(0)
    def inc(): Unit = c.incrementAndGet().discard()
    def get: Int = c.get()
  }
  sealed trait SetElement {
    locally {
      counter.inc()
    }
    def counter: SetCounter
  }
  final case class SetElement1(counter: SetCounter) extends SetElement
  final case class SetElement2(counter: SetCounter) extends SetElement
  final case class SetElement3(counter: SetCounter) extends SetElement
  final case class SetElement4(counter: SetCounter) extends SetElement
  final case class SetElementRetainer(element: SetElement4)
}

abstract class DistageTestExampleBase[F[_]: TagK](implicit F: DIEffect[F]) extends DistageSpecScalatest[F] with DistageMemoizeExample[F] {

  override protected def config: TestConfig = super
    .config.copy(
      pluginConfig = super.config.pluginConfig.enablePackage("xxx") ++ new PluginDef {
          make[SetCounter]

          make[SetElement1]
          make[SetElement2]
          make[SetElement3]
          make[SetElement4]
          make[SetElementRetainer]

          many[SetElement]
            .weak[SetElement1]
            .weak[SetElement2]
            .weak[SetElement3]
            .weak[SetElement4]

          many[SetElement]
            .named("unmemoized-set")
            .weak[SetElement1]
            .weak[SetElement2]
            .weak[SetElement3]
            .weak[SetElement4]
        }
    )

  val XXX_Whitebox_memoizedMockCache = new AtomicReference[MockCache[F]]

  "distage test custom runner" should {
    "support memoized weak sets with transitively retained elements" in {
      (
        set: Set[SetElement],
        s1: SetElementRetainer,
      ) =>
        Quirks.discard(s1)
        F.maybeSuspend(assert(set.size == 4))
    }
    "support memoized weak sets" in {
      (
        set: Set[SetElement],
        s1: SetElement1,
        s2: SetElement2,
        s3: SetElement3,
      ) =>
        Quirks.discard(s1, s2, s3)
        F.maybeSuspend(assert(set.size == 4))
    }

    "support unmemoized named weak sets with memoized elements" in {
      (
        set: Set[SetElement] @Id("unmemoized-set"),
        s1: SetElement1,
        s2: SetElement2,
        s3: SetElement3,
      ) =>
        Quirks.discard(s1, s2, s3)
        F.maybeSuspend(assert(set.size == 3))
    }

    "support unmemoized named weak sets with memoized elements" in {
      (
        set: Set[SetElement] @Id("unmemoized-set"),
        s1: SetElement1,
        s2: SetElement2,
        s3: SetElement3,
        s4: SetElementRetainer,
      ) =>
        Quirks.discard(s1, s2, s3, s4)
        F.maybeSuspend(assert(set.size == 4))
    }

    "return memoized weak set with have whole list of members even if test does not depends on them" in {
      (
        set: Set[SetElement],
        c: SetCounter,
      ) =>
        assume(c.get != 0)
        F.maybeSuspend(assert(set.size == 4))
    }

    "support tests with no deps" in {
      F.unit
    }

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
        assert(false)
    }

    "test 5 (should be ignored)" skip {
      _: MockCachedUserService[F] =>
        assert(false)
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
final class DistageTestExampleZIO2 extends DistageTestExampleBase[Task]

abstract class DistageSleepTest[F[_]: TagK](implicit F: DIEffect[F]) extends DistageSpecScalatest[F] with DistageMemoizeExample[F] {
  "distage test" should {
    "sleep" in {
      _: MockUserRepository[F] =>
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

abstract class OverloadingTest[F[_]: DIEffect: TagK] extends DistageSpecScalatest[F] with DistageMemoizeExample[F] {
  "test overloading of `in`" in {
    () =>
      // `in` with Unit return type is ok
      assertCompiles(""" "test" in { println(""); () }  """)
      // `in` with Assertion return type is ok
      assertCompiles(""" "test" in { assert(1 + 1 == 2) }  """)
      // `in` with any other return type is not ok
      val res = intercept[TestFailedException](
        assertCompiles(
          """ "test" in { println(""); 1 + 1 }  """
        )
      )
      assert(res.getMessage() contains "overloaded method")
  }
}

final class OverloadingTestCIO extends OverloadingTest[CIO]
final class OverloadingTestTask extends OverloadingTest[Task]
final class OverloadingTestIdentity extends OverloadingTest[Identity]

abstract class ActivationTest[F[_]: DIEffect: TagK] extends DistageSpecScalatest[F] with DistageMemoizeExample[F] {
  "resolve bindings for the same key via activation axis" in {
    activeComponent: ActiveComponent =>
      assert(activeComponent == TestActiveComponent)
  }
}

final class ActivationTestCIO extends ActivationTest[CIO]
final class ActivationTestTask extends ActivationTest[Task]
final class ActivationTestIdentity extends ActivationTest[Identity]

abstract class ForcedRootTest[F[_]: DIEffect: TagK] extends DistageSpecScalatest[F] {
  override protected def config: TestConfig = super
    .config.copy(
      moduleOverrides = new ModuleDef {
        make[ForcedRootResource[F]].fromResource[ForcedRootResource[F]]
        make[ForcedRootProbe]
      },
      forcedRoots = Set(DIKey.get[ForcedRootResource[F]]),
    )

  "forced root was attached and the acquire effect has been executed" in {
    locatorRef: LocatorRef =>
      assert(locatorRef.get.get[ForcedRootProbe].started)
  }
}

final class ForcedRootTestCIO extends ForcedRootTest[CIO]
final class ForcedRootTestTask extends ForcedRootTest[Task]
final class ForcedRootTestIdentity extends ForcedRootTest[Identity]
