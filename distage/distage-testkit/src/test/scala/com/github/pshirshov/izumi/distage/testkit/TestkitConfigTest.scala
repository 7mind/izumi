package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
import com.typesafe.config.ConfigFactory

class TestkitConfigTest extends DistagePluginSpec {
  "testkit" must {
    "load config" in di {
      _: LocatorRef =>
        assert(makeLogRouter(makeConfig()).isInstanceOf[ConfigurableLogRouter])
    }
  }

  override protected def makeConfig(): Option[AppConfig] = Some(AppConfig(ConfigFactory.parseResources("distage-testkit-test.conf")))
}



