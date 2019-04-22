//package com.github.pshirshov.izumi.distage.testkit.legacy
//
//import com.github.pshirshov.izumi.distage.config.annotations.ConfPathId
//import com.github.pshirshov.izumi.distage.config.model.AppConfig
//import com.github.pshirshov.izumi.distage.config.{ConfigInjectionOptions, ConfigProvider}
//import com.github.pshirshov.izumi.distage.model.definition.{Binding, ImplDef, ModuleBase}
//import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
//import com.github.pshirshov.izumi.distage.testkit.fixtures.{TestConfig, TestService2}
//import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
//import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
//import com.typesafe.config.ConfigFactory
//
//class TestkitConfigTest extends DistagePluginSpec {
//  "testkit" must {
//    "produce expected logger routers" in {
//      assert(makeLogRouter(makeConfig).isInstanceOf[ConfigurableLogRouter])
//    }
//
//    "load config" in di {
//      service: TestService2 =>
//        assert(service.cfg == TestConfig(1, 3))
//        assert(service.cfg1 == TestConfig(0, 0))
//    }
//  }
//
//  override protected def makeConfig: Option[AppConfig] = {
//    // here we may override our config values on config level
//    Some(AppConfig(ConfigFactory.parseResources("distage-testkit-test.conf")))
//  }
//
//  override protected val configOptions: ConfigInjectionOptions = ConfigInjectionOptions.make {
//    // here we may patternmatch on config value context and rewrite it
//    case (ConfigProvider.ConfigImport(ConfPathId(_, _, _), _), c: TestConfig) =>
//      c.copy(y = 3)
//  }
//
//  override protected def makeBindings: ModuleBase = super.makeBindings ++ new ModuleBase {
//    // here we may add a config value with an appropriate key into our context ahead of time
//    override def bindings: Set[Binding] = Set(
//      Binding.SingletonBinding(
//        DIKey.get[TestConfig].named(ConfPathId(DIKey.get[TestService2], "<test-override>", "test1"))
//        , ImplDef.InstanceImpl(SafeType.get[TestConfig], TestConfig(0, 0))
//        , Set.empty
//        , SourceFilePosition.unknown
//      )
//    )
//  }
//
//
//}
//
