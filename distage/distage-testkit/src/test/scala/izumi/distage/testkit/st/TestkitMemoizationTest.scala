package izumi.distage.testkit.st

import java.util.concurrent.atomic.AtomicReference

import cats.effect.IO
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.testkit.services.st.adapter.ExternalResourceProvider
import izumi.distage.testkit.st.fixtures._
import izumi.fundamentals.platform.functional.Identity
import distage.TagK

abstract class TestkitMemoizationTest[F[_]: TagK] extends TestkitSelftest[F] {
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

class TestkitMemoizationTestIO extends TestkitMemoizationTest[IO]

class TestkitMemoizationTestIdentity extends TestkitMemoizationTest[Identity]

class TestkitMemoizationTestZio extends TestkitMemoizationTest[zio.IO[Throwable, ?]]
