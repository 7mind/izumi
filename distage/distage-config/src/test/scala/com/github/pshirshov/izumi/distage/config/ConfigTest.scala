package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.configapp.{CassandraEndpoint, Endpoint, TestConfigApp}
import com.github.pshirshov.izumi.distage.Injectors
import com.github.pshirshov.izumi.distage.config.pureconfig.PureConfigInstanceReader
import com.typesafe.config._
import org.scalatest.WordSpec


class ConfigTest extends WordSpec {
  "Config resolver" should {
    "resolve config references" in {
      val config = AppConfig(ConfigFactory.load())
      val injector = Injectors.bootstrap(new ConfigModule(config, PureConfigInstanceReader))
      val plan = injector.plan(TestConfigApp.definition)

      //println(plan)

      val context = injector.produce(plan)
      assert(context.get[TestConfigApp].services.size == 3)

      assert(context.get[Endpoint]("service1").address.port == 80)
      assert(context.get[Endpoint]("service2").address.port == 8080)
      assert(context.get[Endpoint].address.port == 8888)
      assert(context.get[CassandraEndpoint].address.port == 9000)
    }
  }

}

