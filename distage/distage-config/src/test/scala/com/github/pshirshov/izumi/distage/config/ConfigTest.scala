package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.configapp._
import com.github.pshirshov.izumi.distage.Injectors
import com.typesafe.config._
import org.scalatest.WordSpec

class ConfigTest extends WordSpec {
  "Config resolver" should {
    "resolve config references" in {
      val config = AppConfig(ConfigFactory.load())
      val injector = Injectors.bootstrap(new ConfigModule(config))
      val plan = injector.plan(TestConfigApp.definition)

      val context = injector.produce(plan)

      assert(context.get[Endpoint]("service1").address.port == 80)
      assert(context.get[Endpoint]("service2").address.port == 8080)
      assert(context.get[Endpoint].address.port == 8888)
      assert(context.get[CassandraEndpoint].address.port == 9000)
    }

    "resolve config maps" in {
      val config = AppConfig(ConfigFactory.load("map-test.conf"))
      val injector = Injectors.bootstrap(new ConfigModule(config))
      val plan = injector.plan(TestConfigApp.mapDefinition)

      val context = injector.produce(plan)

      assert(context.get[MapCaseClass].map.keySet == Set("service1", "service2", "service3"))
      assert(context.get[MapCaseClass].map.values.forall(_.host == "localhost"))
    }

    "resolve config lists" in {
      val config = AppConfig(ConfigFactory.load("list-test.conf"))
      val injector = Injectors.bootstrap(new ConfigModule(config))
      val plan = injector.plan(TestConfigApp.listDefinition)

      val context = injector.produce(plan)

      assert(context.get[ListCaseClass].list.head ==
        Set(
          Wrapper(HostPort(80, "localhost"))
          , Wrapper(HostPort(8080, "localhost"))
          , Wrapper(HostPort(8888, "localhost"))
        )
      )
    }
  }

}

