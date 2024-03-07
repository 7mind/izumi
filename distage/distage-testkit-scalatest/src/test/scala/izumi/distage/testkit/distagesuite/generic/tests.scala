package izumi.distage.testkit.distagesuite.generic

import distage.*
import distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.distagesuite.fixtures.*
import izumi.distage.testkit.distagesuite.generic.DistageTestExampleBase.*
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.{AssertZIO, Spec1, Spec2, SpecZIO}
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.platform.language.Quirks.*
import org.scalatest.exceptions.TestFailedException
import zio.{Task, ZEnvironment, ZIO}

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

trait DistageMemoizeExample[F[_]] extends DistageAbstractScalatestSpec[F] {
  override protected def config: TestConfig = {
    super.config.copy(
      pluginConfig = PluginConfig.cached(classOf[ActiveComponent].getPackage.getName),
      memoizationRoots = Map(
        1 -> Set(DIKey.get[MockCache[F]]),
        2 -> Set(DIKey.get[Set[SetElement]], DIKey.get[SetCounter]),
      ),
    )
  }
}

class DistageTestExampleBIO extends Spec2[zio.IO] with DistageMemoizeExample[Task] {

  "distage test runner" should {
    "support bifunctor" in {
      (service: MockUserRepository[Task]) =>
        for {
          _ <- ZIO.attempt(assert(service != null))
        } yield ()
    }
  }

}

class DistageTestExampleBIOEnv extends SpecZIO with DistageMemoizeExample[Task] with AssertZIO {

  val service = ZIO.environmentWith[MockUserRepository[Task]](_.get)

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
      (cached: MockCachedUserService[Task]) =>
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

abstract class DistageTestExampleBase[F[_]: TagK: DefaultModule](implicit F: QuasiIO[F]) extends Spec1[F] with DistageMemoizeExample[F] {

  override protected def config: TestConfig = super.config.copy(
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

      make[ZEnvironment[Int]].named("zio-initial-env").from(ZEnvironment(1))
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

    "support unmemoized named weak sets with memoized elements (3)" in {
      (
        set: Set[SetElement] @Id("unmemoized-set"),
        s1: SetElement1,
        s2: SetElement2,
        s3: SetElement3,
      ) =>
        Quirks.discard(s1, s2, s3)
        F.maybeSuspend(assert(set.size == 4))
    }

    "support unmemoized named weak sets with memoized elements (4)" in {
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
      (service: MockUserRepository[F]) =>
        for {
          _ <- F.maybeSuspend(assert(service != null))
        } yield ()
    }

    "test 2" in {
      (service: MockCachedUserService[F]) =>
        for {
          _ <- F.maybeSuspend(XXX_Whitebox_memoizedMockCache.compareAndSet(null, service.cache))
          _ <- F.maybeSuspend(assert(service != null))
          _ <- F.maybeSuspend(assert(service.cache eq XXX_Whitebox_memoizedMockCache.get()))
        } yield ()
    }

    "test 3" in {
      (service: MockCachedUserService[F]) =>
        F.maybeSuspend {
          XXX_Whitebox_memoizedMockCache.compareAndSet(null, service.cache)
          assert(service != null)
          assert(service.cache eq XXX_Whitebox_memoizedMockCache.get())
        }
    }

    "test 4 (should be ignored)" in {
      (_: ApplePaymentProvider[F]) =>
        assert(false)
    }

    "test 5 (should be ignored)" skip {
      (_: MockCachedUserService[F]) =>
        assert(false)
    }

    "test 6 (should be ignored due to `assume`)" in {
      (_: MockCachedUserService[F]) =>
        assume(false, "xxx")
    }
  }

}

abstract class DistageSleepTest[F[_]: TagK: DefaultModule](implicit F: QuasiIO[F]) extends Spec1[F] with DistageMemoizeExample[F] {
  "distage test" should {
    "sleep" in {
      (_: MockUserRepository[F]) =>
        for {
          _ <- F.maybeSuspend(Thread.sleep(100))
        } yield ()
    }
  }
}

abstract class OverloadingTest[F[_]: QuasiIO: TagK: DefaultModule] extends Spec1[F] with DistageMemoizeExample[F] {
  "test overloading of `in`" in {
    () =>
      // `in` with Unit return type is ok
      assertCompiles(""" "test" in { println(""); QuasiIO[F].pure(()) }  """)
      // `in` with Assertion return type is ok
      assertCompiles(""" "test" in { QuasiIO[F].pure(assert(1 + 1 == 2)) }  """)
      // `in` with any other return type is not ok
      val res = intercept[TestFailedException](
        assertCompiles(
          """ "test" in { println(""); QuasiIO[F].pure(1 + 1) }  """
        )
      )
      assert(res.getMessage() contains "overloaded")
  }
}

abstract class ActivationTest[F[_]: QuasiIO: TagK: DefaultModule] extends Spec1[F] with DistageMemoizeExample[F] {
  "resolve bindings for the same key via activation axis" in {
    (activeComponent: ActiveComponent) =>
      assert(activeComponent == TestActiveComponent)
  }
}

abstract class ForcedRootTest[F[_]: QuasiIO: TagK: DefaultModule] extends Spec1[F] {
  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[ForcedRootResource[F]].fromResource[ForcedRootResource[F]]
      make[ForcedRootProbe]
    },
    forcedRoots = Set(DIKey.get[ForcedRootResource[F]]),
  )

  "forced root was attached and the acquire effect has been executed" in {
    (locatorRef: LocatorRef) =>
      assert(locatorRef.get.get[ForcedRootProbe].started)
  }
}
