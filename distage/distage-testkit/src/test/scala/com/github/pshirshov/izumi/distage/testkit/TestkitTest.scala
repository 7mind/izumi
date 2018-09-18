package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr

class TestService1 extends AutoCloseable {
  override def close(): Unit = {
  }
}

class TestService2 {
}

class TestPlugin extends PluginDef {
  make[TestService1]
  make[TestService2]
}

class TestkitTest extends DistagePluginSpec {
  override protected def disabledTags: TagExpr.Strings.Expr = TagExpr.Strings.False

  "testkit" must {
    "load plugins" in di {
      (service: TestService1, locatorRef: LocatorRef) =>
        assert(locatorRef.get.instances.exists(_.value == service))
        assert(!locatorRef.get.instances.exists(_.value.isInstanceOf[TestService2]))
        assert(locatorRef.get.get[Set[AutoCloseable]].size == 1)
        assert(locatorRef.get.parent.get.get[Set[AutoCloseable]].size == 0)
    }
  }
}
