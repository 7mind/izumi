package com.github.pshirshov.izumi.distage.testkit.st

import java.util.concurrent.atomic.AtomicInteger

import cats.effect.IO
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
import com.github.pshirshov.izumi.distage.testkit.st.fixtures.{TestService1, TestkitSelftest}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import distage.{DIKey, TagK}


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
    implicit val tt = typeTag[TestService1] // a quirk to avoid a warning in the assertion below
    assert(context.find[TestService1].isDefined)
    assert(cc.incrementAndGet() == 1)
    suppressTheRestOfTestSuite()
  }
}

class TestkitSuppressionTestIO extends TestkitSuppressionTest[IO]

class TestkitSuppressionTestIdentity extends TestkitSuppressionTest[Identity]

class TestkitSuppressionTestZio extends TestkitSuppressionTest[zio.IO[Throwable, ?]]
