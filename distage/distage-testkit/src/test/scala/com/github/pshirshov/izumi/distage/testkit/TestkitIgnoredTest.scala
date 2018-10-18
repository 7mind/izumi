package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
import com.github.pshirshov.izumi.distage.roles.roles.RoleStarter
import distage.{ModuleBase, ModuleDef}

class TestkitIgnoredTest extends DistagePluginSpec {
  "testkit" must {
    "support test suppression" in di {
      _: LocatorRef =>
        fail("This test must be ignored")
    }
  }


  override protected def suiteRoots: Set[DIKey] = {
    // here we may define the roots we need for dependency checks
    Set(DIKey.get[TestService1])
  }

  override protected def beforeRun(context: Locator, roleStarter: RoleStarter): Unit = {
    implicit val tt = typeTag[TestService1] // a quirk to avoid a warning in the assertion below
    assert(context.find[TestService1].isDefined)
    ignoreThisTest() // and here we may conditionally ignore the tests or even use
  }
}

class TestkitSuppressionTest extends DistagePluginSpec {
  "testkit" must {
    "support suite-level suppression" in di {
      _: LocatorRef =>
        fail("This test must be ignored")
    }

    "support test suppression" in di {
      _: LocatorRef =>
        fail("This test must be ignored as well")
    }
  }


  override protected def suiteRoots: Set[DIKey] = {
    // here we may define the roots we need for dependency checks
    Set(DIKey.get[TestService1])
  }

  override protected def beforeRun(context: Locator, roleStarter: RoleStarter): Unit = {
    implicit val tt = typeTag[TestService1] // a quirk to avoid a warning in the assertion below
    assert(context.find[TestService1].isDefined)
    suppressTheRestOfTestSuite()
  }
}

class TestkitIntegrationCheckTest extends DistageSpec {
  override def makeBindings: ModuleBase = new ModuleDef {
    make[TestFailingIntegrationResource]
  }

  "testkit" must {
    "skip test if external resource check failed" in di {
      _: TestFailingIntegrationResource =>
        fail("This test must be ignored")
    }
  }
}
