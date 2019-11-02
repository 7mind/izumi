package izumi.distage.testkit.st

import cats.effect.IO
import izumi.distage.testkit.st.fixtures.{TestFailingIntegrationResource, TestkitSelftest}
import izumi.fundamentals.platform.functional.Identity
import distage.TagK

abstract class TestkitIntegrationCheckTest[F[_] : TagK] extends TestkitSelftest[F] {
  "testkit" must {
    "skip test if external resource check failed" in dio {
      _: TestFailingIntegrationResource =>
        fail("This test must be ignored")
    }
  }
}

class TestkitIntegrationCheckTestIO extends TestkitIntegrationCheckTest[IO]

class TestkitIntegrationCheckTestIdentity extends TestkitIntegrationCheckTest[Identity]

class TestkitIntegrationCheckTestZio extends TestkitIntegrationCheckTest[zio.IO[Throwable, ?]]
