package izumi.distage.testkit.scalatest

import java.util.concurrent.atomic.AtomicReference

import cats.effect.IO
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.testkit.services.scalatest.adapter.ExternalResourceProvider
import izumi.distage.testkit.scalatest.fixtures._
import izumi.fundamentals.platform.functional.Identity
import distage.TagK

@deprecated("Use dstest", "2019/Jul/18")
abstract class TestkitMemoizationTest[F[_] : TagK] extends TestkitSelftest[F] {
  val ref = new AtomicReference[TestResource1]()

  "testkit" must {
    "support memoization (1/2)" in dio {
      (res: TestResource1, eff: DIEffect[F]) =>
        eff.maybeSuspend {
          assert(ref.get() == null)
          ref.set(res)
        }
    }

    "support memoization (2/2)" in dio {
      (res: TestResource1, eff: DIEffect[F]) =>
        eff.maybeSuspend {
          assert(ref.get() eq res)
        }
    }

    "not finalize resources immediately (1/2)" in dio {
      (_: TestResourceDI, eff: DIEffect[F]) =>
        eff.maybeSuspend {
          assert(TestResourceDI.closeCount.get() == 0)
        }
    }

    "not finalize resources immediately (2/2)" in {
      assert(TestResourceDI.closeCount.get() == 0)
    }

  }

  override protected def externalResourceProvider: ExternalResourceProvider = ExternalResourceProvider.singleton[F] {
    case IdentifiedRef(_, _: TestResource1) =>
      true
    case IdentifiedRef(_, _: TestResourceDI) =>
      true
    case _ =>
      false
  }
}

@deprecated("Use dstest", "2019/Jul/18")
class TestkitMemoizationTestIO extends TestkitMemoizationTest[IO]

@deprecated("Use dstest", "2019/Jul/18")
class TestkitMemoizationTestIdentity extends TestkitMemoizationTest[Identity]

@deprecated("Use dstest", "2019/Jul/18")
class TestkitMemoizationTestZio extends TestkitMemoizationTest[zio.IO[Throwable, ?]]
