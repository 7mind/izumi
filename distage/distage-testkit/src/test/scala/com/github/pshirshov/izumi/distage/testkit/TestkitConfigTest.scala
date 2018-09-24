package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.config.ConfigInjectionOptions
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
import com.typesafe.config.ConfigFactory

class TestkitConfigTest extends DistagePluginSpec {
  "testkit" must {
    "produce expected logger routers" in {
      assert(makeLogRouter(makeConfig()).isInstanceOf[ConfigurableLogRouter])
    }

    "load config" in di {
      service: TestService2 =>
        assert(service.cfg.x == 1)
        assert(service.cfg.y == 3)
    }
  }

  override protected def makeConfig(): Option[AppConfig] = {
    Some(AppConfig(ConfigFactory.parseResources("distage-testkit-test.conf")))
  }

  override protected val configOptions: ConfigInjectionOptions = ConfigInjectionOptions.make {
    case (_, c: TestConfig) =>
      c.copy(y = 3)
  }
}

