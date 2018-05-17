package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.configapp._
import com.github.pshirshov.izumi.distage.Injectors
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.typesafe.config._
import org.scalatest.WordSpec

class ConfigTest extends WordSpec {
  "Config resolver" should {
    "resolve config references" in {
      val config = AppConfig(ConfigFactory.load())
      val injector = Injectors.bootstrap(new ConfigModule(config))
      val plan = injector.plan(TestConfigApp.definition)

      val context = injector.produce(plan)

      assert(context.get[HttpServer1].listenOn.port == 8081)
      assert(context.get[HttpServer2].listenOn.port == 8082)
      assert(context.get[HttpServer3].listenOn.port == 8083)

      assert(context.get[DataPuller1].target.port == 9001)
      assert(context.get[DataPuller2].target.port == 9002)
      assert(context.get[DataPuller3].target.port == 9003)

      assert(context.get[TestAppService]("puller4").asInstanceOf[DataPuller1].target.port == 10010)
      assert(context.get[TestAppService]("puller5").asInstanceOf[DataPuller2].target.port == 10020)
      assert(context.get[TestAppService]("puller6").asInstanceOf[DataPuller3].target.port == 9003)

      assert(context.get[TestAppService]("puller1") eq context.get[DataPuller1])
      assert(context.get[Set[TestAppService]]("puller1").exists(_ eq context.get[TestAppService]("puller1")))
    }

    "resolve config maps" in {
      val config = AppConfig(ConfigFactory.load("map-test.conf"))
      val injector = Injectors.bootstrap(new ConfigModule(config))
      val plan = injector.plan(TestConfigReaders.mapDefinition)

      val context = injector.produce(plan)

      assert(context.get[MapCaseClass].m.keySet == Set("service1", "service2", "service3"))
      assert(context.get[MapCaseClass].m.values.forall(_.host == "localhost"))
    }

    "resolve config lists" in {
      val config = AppConfig(ConfigFactory.load("list-test.conf"))
      val injector = Injectors.bootstrap(new ConfigModule(config))
      val plan = injector.plan(TestConfigReaders.listDefinition)

      val context = injector.produce(plan)

      assert(context.get[ListCaseClass].l.head ==
        Set(
          Wrapper(HostPort(80, "localhost"))
          , Wrapper(HostPort(8080, "localhost"))
          , Wrapper(HostPort(8888, "localhost"))
        )
      )
    }

    "resolve config options" in {
      val config = AppConfig(ConfigFactory.load("opt-test.conf"))
      val injector = Injectors.bootstrap(new ConfigModule(config))
      val plan = injector.plan(TestConfigReaders.optDefinition)

      val context = injector.produce(plan)

      assert(context.get[OptionCaseClass] == OptionCaseClass(Some(1), Some(5.0), None))
      assert(context.get[OptionCaseClass2].opt == Opt(None))
    }
  }

}

