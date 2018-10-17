package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.testkit.TestkitTest.NotAddedClass

object TestkitTest {
  case class NotAddedClass()
}

class TestkitTest extends DistagePluginSpec {
  "testkit" must {
    "load plugins" in di {
      (service: TestService1, locatorRef: LocatorRef) =>
        assert(locatorRef.get.instances.exists(_.value == service))
        assert(!locatorRef.get.instances.exists(_.value.isInstanceOf[TestService2]))
        assert(locatorRef.get.get[Set[AutoCloseable]].size == 3)
        assert(locatorRef.get.parent.get.get[Set[AutoCloseable]].isEmpty)
    }

    "start and close resources and role components in correct order" in {
      var ref: LocatorRef = null

      di {
        (_: TestService1, locatorRef: LocatorRef) =>
          ref = locatorRef
      }

      val ctx = ref.get
      assert(ctx.get[InitCounter].closedCloseables == Seq(ctx.get[TestService1], ctx.get[TestResource2], ctx.get[TestResource1]))
      assert(ctx.get[InitCounter].startedRoleComponents == Seq(ctx.get[TestComponent1], ctx.get[TestComponent2], ctx.get[TestComponent3]))
      assert(ctx.get[InitCounter].closedRoleComponents == Seq(ctx.get[TestComponent3], ctx.get[TestComponent2], ctx.get[TestComponent1]))
    }

    "create classes in `di` arguments even if they that aren't in makeBindings" in di {
      notAdded: NotAddedClass =>
        assert(notAdded == NotAddedClass())
    }
  }
}
