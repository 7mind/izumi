package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.configapp.TestConfigApp
import com.github.pshirshov.izumi.distage.Injectors
import com.typesafe.config._
import org.scalatest.WordSpec



class ConfigTest extends WordSpec {
  "Config resolver" should {
    "resolve config references" in {
      val config = AppConfig(ConfigFactory.load())
      val injector = Injectors.bootstrap(new ConfigModule(config))
      val plan = injector.plan(TestConfigApp.definition)
      //println(plan)

      val context = injector.produce(plan)
      assert(context.get[TestConfigApp].services.size == 3)
    }
  }

}

