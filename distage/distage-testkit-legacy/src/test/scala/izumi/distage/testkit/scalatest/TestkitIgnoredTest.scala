package izumi.distage.testkit.scalatest

import cats.effect.IO
import distage.{LocatorRef, TagK}
import izumi.distage.model.Locator
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.testkit.scalatest.fixtures.{TestService1, TestkitSelftest}
import izumi.fundamentals.platform.functional.Identity

@deprecated("Use dstest", "2019/Jul/18")
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

@deprecated("Use dstest", "2019/Jul/18")
class TestkitIgnoredTestIO extends TestkitIgnoredTest[IO]

@deprecated("Use dstest", "2019/Jul/18")
class TestkitIgnoredTestIdentity extends TestkitIgnoredTest[Identity]

@deprecated("Use dstest", "2019/Jul/18")
class TestkitIgnoredTestZio extends TestkitIgnoredTest[zio.IO[Throwable, ?]]
