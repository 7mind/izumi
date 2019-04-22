package com.github.pshirshov.izumi.distage.testkit

import cats.effect.IO
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.testkit.fixtures.{TestFailingIntegrationResource, TestkitSelftest}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import distage.TagK

abstract class TestkitIntegrationCheckTest[F[_] : TagK : DIEffect] extends TestkitSelftest[F] {
  "testkit" must {
    "skip test if external resource check failed" in di {
      _: TestFailingIntegrationResource =>
        fail("This test must be ignored")
    }
  }
}

class TestkitIntegrationCheckTestIO extends TestkitIntegrationCheckTest[IO]

class TestkitIntegrationCheckTestIdentity extends TestkitIntegrationCheckTest[Identity]
