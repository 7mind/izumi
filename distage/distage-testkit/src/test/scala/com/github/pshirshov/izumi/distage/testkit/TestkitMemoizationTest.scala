package com.github.pshirshov.izumi.distage.testkit

import java.util.concurrent.atomic.AtomicReference

import cats.effect.IO
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.testkit.fixtures._
import com.github.pshirshov.izumi.distage.testkit.services.ExternalResourceProvider
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import distage.TagK


abstract class TestkitMemoizationTest[F[_] : TagK : DIEffect] extends TestkitSelftest[F] {
  val ref = new AtomicReference[TestResource1]()

  "testkit" must {
    "store memoized value" in di {
      res: TestResource1 =>
        DIEffect[F].maybeSuspend {
          assert(ref.get() == null)
          ref.set(res)
        }
    }

    "support memoization" in di {
      res: TestResource1 =>
        DIEffect[F].maybeSuspend {
          assert(ref.get() eq res)
        }
    }

  }

  override protected def externalResourceProvider: ExternalResourceProvider = ExternalResourceProvider.singleton {
    case IdentifiedRef(_, _: TestResource1) =>
      true
    case _ =>
      false
  }
}

class TestkitMemoizationTestIO extends TestkitMemoizationTest[IO]

class TestkitMemoizationTestIdentity extends TestkitMemoizationTest[Identity]
