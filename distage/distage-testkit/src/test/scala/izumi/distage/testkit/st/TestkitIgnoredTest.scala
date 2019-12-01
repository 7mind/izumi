package izumi.distage.testkit.st

import cats.effect.IO
import distage.TagK
import izumi.distage.model.Locator
import izumi.distage.model.Locator.LocatorRef
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.testkit.st.fixtures.{TestService1, TestkitSelftest}
import izumi.fundamentals.platform.functional.Identity

abstract class TestkitIgnoredTest[F[_] : TagK] extends TestkitSelftest[F] {
  override protected def pluginPackages: Seq[String] = thisPackage

  "testkit" must {
    "support test suppression" in dio {
      _: LocatorRef =>
        fail("This test must be ignored")
    }
  }

  override protected def additionalRoots: Set[distage.DIKey] = {
    // here we may define the roots we need for dependency checks
    Set(DIKey.get[TestService1])
  }

  /** You can override this to e.g. skip test when certain external dependencies are not available **/
  override protected def beforeRun(context: Locator): Unit = {
    assert(context.find[TestService1].isDefined)
    ignoreThisTest() // and here we may conditionally ignore the tests or even use
  }
}

class TestkitIgnoredTestIO extends TestkitIgnoredTest[IO]

class TestkitIgnoredTestIdentity extends TestkitIgnoredTest[Identity]

class TestkitIgnoredTestZio extends TestkitIgnoredTest[zio.IO[Throwable, ?]]
