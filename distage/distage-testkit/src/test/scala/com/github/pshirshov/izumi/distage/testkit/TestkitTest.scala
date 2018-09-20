package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr
import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
import com.typesafe.config.ConfigFactory


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


class TestkitConfigTest extends DistagePluginSpec {
  override protected def disabledTags: TagExpr.Strings.Expr = TagExpr.Strings.False

  "testkit" must {
    "load config" in di {
      _: LocatorRef =>
        assert(makeLogRouter(makeConfig()).isInstanceOf[ConfigurableLogRouter])
    }
  }

  override protected def makeConfig(): Option[AppConfig] = Some(AppConfig(ConfigFactory.parseResources("distage-testkit-test.conf")))
}


