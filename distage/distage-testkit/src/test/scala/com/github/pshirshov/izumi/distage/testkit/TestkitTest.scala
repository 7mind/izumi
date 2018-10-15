package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef

class TestkitTest extends DistagePluginSpec {
  "testkit" must {
    "load plugins" in di {
      (service: TestService1, locatorRef: LocatorRef) =>
        assert(locatorRef.get.instances.exists(_.value == service))
        assert(!locatorRef.get.instances.exists(_.value.isInstanceOf[TestService2]))
        assert(locatorRef.get.get[Set[AutoCloseable]].size == 3)
        assert(locatorRef.get.parent.get.get[Set[AutoCloseable]].isEmpty)
    }

    "close resources in correct order" in {
      var ref: LocatorRef = null

      di {
        (_: TestService1, locatorRef: LocatorRef) =>
          ref = locatorRef
      }

      val ctx = ref.get
      assert(ctx.get[InitCounter].closedCloseables == Seq(ctx.get[TestService1], ctx.get[TestResource2], ctx.get[TestResource1]))
    }
  }
}
