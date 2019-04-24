package com.github.pshirshov.izumi.distage.testkit

import cats.effect.IO
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
import com.github.pshirshov.izumi.distage.testkit.fixtures.{TestService1, TestkitSelftest}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import distage.TagK

abstract class TestkitIgnoredTest[F[_] : TagK : DIEffect] extends TestkitSelftest[F] {
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
    implicit val tt = typeTag[TestService1] // a quirk to avoid a warning in the assertion below
    assert(context.find[TestService1].isDefined)
    ignoreThisTest() // and here we may conditionally ignore the tests or even use
  }
}


class TestkitIgnoredTestIO extends TestkitIgnoredTest[IO]

class TestkitIgnoredTestIdentity extends TestkitIgnoredTest[Identity]
