package izumi.distage.testkit.scalatest

import java.util.concurrent.atomic.AtomicInteger

import cats.effect.IO
import distage.{DIKey, LocatorRef, TagK}
import izumi.distage.model.Locator
import izumi.distage.testkit.scalatest.fixtures.{TestService1, TestkitSelftest}
import izumi.fundamentals.platform.functional.Identity


abstract class TestkitSuppressionTest[F[_] : TagK] extends TestkitSelftest[F] {
  override protected def pluginPackages: Seq[String] = thisPackage
  private val cc = new AtomicInteger(0)

  "testkit" must {
    "support suite-level suppression" in dio {
      _: LocatorRef =>
        fail("This test must be ignored")
    }

    "support test suppression" in dio {
      _: LocatorRef =>
        fail("This test must be ignored as well")
    }
  }


  override protected def additionalRoots: Set[distage.DIKey] = {
    // here we may define the roots we need for dependency checks
    Set(DIKey.get[TestService1])
  }

  /** You can override this to e.g. skip test when certain external dependencies are not available **/
  override protected def beforeRun(context: Locator): Unit = {
    assert(context.find[TestService1].isDefined)
    assert(cc.incrementAndGet() == 1)
    suppressTheRestOfTestSuite()
  }
}

class TestkitSuppressionTestIO extends TestkitSuppressionTest[IO]

class TestkitSuppressionTestIdentity extends TestkitSuppressionTest[Identity]

class TestkitSuppressionTestZio extends TestkitSuppressionTest[zio.IO[Throwable, ?]]
