package izumi.distage.testkit.scalatest

import cats.effect.IO
import izumi.distage.testkit.scalatest.fixtures.{TestFailingIntegrationResource, TestkitSelftest}
import izumi.fundamentals.platform.functional.Identity
import distage.TagK

@deprecated("Use dstest", "2019/Jul/18")
abstract class TestkitIntegrationCheckTest[F[_] : TagK] extends TestkitSelftest[F] {
  "testkit" must {
    "skip test if external resource check failed" in dio {
      _: TestFailingIntegrationResource =>
        fail("This test must be ignored")
    }
  }
}

@deprecated("Use dstest", "2019/Jul/18")
class TestkitIntegrationCheckTestIO extends TestkitIntegrationCheckTest[IO]

@deprecated("Use dstest", "2019/Jul/18")
class TestkitIntegrationCheckTestIdentity extends TestkitIntegrationCheckTest[Identity]

@deprecated("Use dstest", "2019/Jul/18")
class TestkitIntegrationCheckTestZio extends TestkitIntegrationCheckTest[zio.IO[Throwable, ?]]
