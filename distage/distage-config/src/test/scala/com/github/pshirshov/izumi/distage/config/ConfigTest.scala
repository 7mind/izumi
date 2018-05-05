package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.configapp.TestConfigApp
import com.github.pshirshov.izumi.distage.Injectors
import com.typesafe.config._
import org.scalatest.WordSpec


object DummyConfigInstanceReader extends ConfigInstanceReader {
  def read(value: ConfigValue, clazz: Class[_]): Product = {
    clazz.getConstructor(classOf[Int], classOf[String]).newInstance(0.intValue().underlying(), "").asInstanceOf[Product]
  }
}

class ConfigTest extends WordSpec {
  "Config resolver" should {
    "resolve config references" in {
      val config = AppConfig(ConfigFactory.load())
      val injector = Injectors.bootstrap(new ConfigModule(config, DummyConfigInstanceReader))
      val plan = injector.plan(TestConfigApp.definition)
      //println(plan)

      val context = injector.produce(plan)
      assert(context.get[TestConfigApp].services.size == 3)
    }
  }

}

