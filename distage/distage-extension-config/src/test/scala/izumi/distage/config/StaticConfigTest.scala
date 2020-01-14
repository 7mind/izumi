//package izumi.distage.config
//
//import com.typesafe.config.ConfigFactory
//import distage.Id
//import izumi.distage.config.annotations.AutoConf
//import izumi.distage.config.model.AppConfig
//import izumi.distage.injector.MkInjector
//import izumi.distage.model.PlannerInput
//import izumi.distage.model.definition.ModuleDef
//import org.scalatest.wordspec.AnyWordSpec
//
//class StaticConfigTest extends AnyWordSpec with MkInjector {
//
//  "Inject config works for macro trait methods" in {
//    import ConfigFixtures._
//
//    val config = AppConfig(ConfigFactory.load("fixtures-test.conf"))
//    val injector = mkStaticInjector(new ConfigModule(config))
//
//    val definition = new ModuleDef {
//      make[TestDependency]
//      make[TestTrait]
//    }
//    val plan = injector.plan(PlannerInput.noGc(definition))
//    val context = injector.produceUnsafe(plan)
//
//    assert(context.get[TestTrait].x == TestDependency(TestConf(false)))
//    assert(context.get[TestTrait].testConf == TestConf(true))
//    assert(context.get[TestDependency] == TestDependency(TestConf(false)))
//  }
//
//  "Inject config works for macro factory methods (not products)" in {
//    import ConfigFixtures._
//
//    val config = AppConfig(ConfigFactory.load("fixtures-test.conf"))
//    val injector = mkStaticInjector(new ConfigModule(config))
//
//    val definition = new ModuleDef {
//      make[TestDependency]
//      make[TestGenericConfFactory[TestConfAlias]]
//    }
//    val plan = injector.plan(PlannerInput.noGc(definition))
//    val context = injector.produceUnsafe(plan)
//
//    assert(context.get[TestDependency] == TestDependency(TestConf(false)))
//    assert(context.get[TestGenericConfFactory[TestConf]].x == TestDependency(TestConf(false)))
//  }
//
//  "Inject config works for macro factory products" in {
//    // FactoryMethod wirings are generated at compile-time and inacessible to ConfigModule, so method ends up depending on TestConf, not TestConf#auto[..]. To fix this, need a new type of binding that would include all factory reflected info
//    import ConfigFixtures._
//
//    val config = AppConfig(ConfigFactory.load("fixtures-test.conf"))
//    val injector = mkStaticInjector(new ConfigModule(config))
//
//    val definition = new ModuleDef {
//      make[TestDependency]
//      make[TestFactory]
//      make[TestGenericConfFactory[TestConfAlias]]
//    }
//    val plan = injector.plan(PlannerInput.noGc(definition))
//    val context = injector.produceUnsafe(plan)
//
//    val factory = context.get[TestFactory]
//    assert(factory.make(5) == ConcreteProduct(TestConf(true), 5))
//    assert(factory.makeTrait().testConf == TestConf(true))
//    assert(factory.makeTraitWith().asInstanceOf[AbstractProductImpl].testConf == TestConf(true))
//
//    assert(context.get[TestDependency] == TestDependency(TestConf(false)))
//
//    assert(context.get[TestGenericConfFactory[TestConf]].x == TestDependency(TestConf(false)))
//    assert(context.get[TestGenericConfFactory[TestConf]].make().testConf == TestConf(false))
//  }
//
//  "Inject config works for providers" in {
//    import ConfigFixtures._
//
//    val config = AppConfig(ConfigFactory.load("fixtures-test.conf"))
//    val injector = mkStaticInjector(new ConfigModule(config))
//
//    val definition = new ModuleDef {
//      make[Int].named("depInt").from(5)
//      make[ConcreteProduct].from {
//        (conf: TestConf@AutoConf, i: Int@Id("depInt")) => ConcreteProduct(conf, i * 10)
//      }
//    }
//    val plan = injector.plan(PlannerInput.noGc(definition))
//    val context = injector.produceUnsafe(plan)
//
//    assert(context.get[ConcreteProduct] == ConcreteProduct(TestConf(false), 50))
//  }
//
//}
