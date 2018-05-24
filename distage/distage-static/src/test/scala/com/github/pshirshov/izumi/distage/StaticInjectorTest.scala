package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.config.annotations.AutoConf
import com.github.pshirshov.izumi.distage.config.{ConfigFixtures, ConfigModule}
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Injector
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.definition.StaticDSL._
import com.github.pshirshov.izumi.distage.model.provisioning.FactoryExecutor
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.provisioning.{AbstractConstructor, FactoryTools}
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpec

class StaticInjectorTest extends WordSpec {

  def mkInjector(): Injector = Injectors.bootstrap()

  "DI planner" should {

    "handle macro factory injections" in {
      import Case5._

      val definition = new ModuleDef {
         make[Factory].statically
        make[Dependency].statically
        make[OverridingFactory].statically
        make[AssistedFactory].statically
        make[AbstractFactory].statically
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val factory = context.get[Factory]
      assert(factory.wiringTargetForDependency != null)
      assert(factory.factoryMethodForDependency() != factory.wiringTargetForDependency)
      assert(factory.x().b.isInstanceOf[Dependency])

      val abstractFactory = context.get[AbstractFactory]
      assert(abstractFactory.x().isInstanceOf[AbstractDependencyImpl])

      val fullyAbstract1 = abstractFactory.y()
      val fullyAbstract2 = abstractFactory.y()
      assert(fullyAbstract1.isInstanceOf[FullyAbstractDependency])
      assert(fullyAbstract1.a.isInstanceOf[Dependency])
      assert(!fullyAbstract1.eq(fullyAbstract2))

      val overridingFactory = context.get[OverridingFactory]
      assert(overridingFactory.x(ConcreteDep()).b.isInstanceOf[ConcreteDep])

      val assistedFactory = context.get[AssistedFactory]
      assert(assistedFactory.x(1).a == 1)
      assert(assistedFactory.x(1).b.isInstanceOf[Dependency])
    }

    "handle generic arguments in macro factory methods" in {
      import Case5._

      val definition: ModuleBase = new ModuleDef {
        make[GenericAssistedFactory].statically
        make[Dependency].from(ConcreteDep())
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[GenericAssistedFactory]
      val product = instantiated.x(List(SpecialDep()), List(5))
      assert(product.a.forall(_.isSpecial))
      assert(product.b.forall(_ == 5))
      assert(product.c == ConcreteDep())
    }

    "handle assisted dependencies in macro factory methods" in {
      import Case5._

      val definition: ModuleBase = new ModuleDef {
        make[AssistedFactory].statically
        make[Dependency].from(ConcreteDep())
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[AssistedFactory]

      assert(instantiated.x(5).a == 5)
    }

    "handle named assisted dependencies in macro factory methods" in {
      import Case5._

      val definition: ModuleBase = new ModuleDef {
        make[NamedAssistedFactory].statically
        make[Dependency].statically
        make[Dependency].named("special").from(SpecialDep())
        make[Dependency].named("veryspecial").from(VerySpecialDep())
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[NamedAssistedFactory]

      assert(instantiated.dep.isVerySpecial)
      assert(instantiated.x(5).b.isSpecial)
    }

    "handle one-arg trait" in {
      import Case7._

      val definition = new ModuleDef {
        make[Dependency1].statically
        make[TestTrait].statically
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]
      assert(instantiated.isInstanceOf[TestTrait])
      assert(instantiated.dep != null)
    }

    "handle named one-arg trait" in {
      import Case7._

      val definition = new ModuleDef {
        make[Dependency1].statically
        make[TestTrait].named("named-trait")
          .statically
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]("named-trait")
      assert(instantiated.isInstanceOf[TestTrait])
      assert(instantiated.dep != null)
    }

    "handle mixed sub-trait with protected autowires" in {
      import Case8._

      val definition = new ModuleDef {
        make[Trait3].statically
        make[Trait2].statically
        make[Trait1].statically
        make[Dependency3]
        make[Dependency2]
        make[Dependency1]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated1 = context.get[Trait1]
      assert(instantiated1.isInstanceOf[Trait1])

      val instantiated2 = context.get[Trait2]
      assert(instantiated2.isInstanceOf[Trait2])

      val instantiated3 = context.get[Trait3]
      assert(instantiated3.isInstanceOf[Trait3])

      instantiated3.prr()
    }

    "handle sub-type trait" in {
      import Case8._

      val definition = new ModuleDef {
        make[Trait2].fromStatic[Trait3]
        make[Dependency3]
        make[Dependency2]
        make[Dependency1]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated3 = context.get[Trait2]
      assert(instantiated3.isInstanceOf[Trait2])
      assert(instantiated3.asInstanceOf[Trait3].prr() == "Hello World")
    }

    "support named bindings in macro traits" in {
      import Case10._

      val definition = new ModuleDef {
          make[Dep].named("A").fromStatic[DepA]
        make[Dep].named("B").fromStatic[DepB]
        make[Trait].statically
        make[Trait1].statically
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[Trait]

      assert(instantiated.depA.isA)
      assert(!instantiated.depB.isA)

      val instantiated1 = context.get[Trait1]

      assert(instantiated1.depA.isA)
      assert(!instantiated1.depB.isA)
    }

    "override protected defs in macro traits" in {
      import Case14._

      val definition = new ModuleDef {
        make[TestTrait].statically
        make[Dep].statically
      }

        val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]

      assert(instantiated.rd == Dep().toString)
    }

    "Inject config works for trait methods" in {
      import ConfigFixtures._

      val config = AppConfig(ConfigFactory.load("macro-fixtures-test.conf"))
      val injector = Injectors.bootstrap(new ConfigModule(config))

      val definition = new ModuleDef {
        make[TestDependency].statically
        make[TestTrait].statically
      }
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestTrait].x == TestDependency(TestConf(false)))
      assert(context.get[TestTrait].testConf == TestConf(true))
      assert(context.get[TestDependency] == TestDependency(TestConf(false)))
    }

    "Inject config works for concrete and abstract factory products and factory methods" in {
      import ConfigFixtures._

      val config = AppConfig(ConfigFactory.load("macro-fixtures-test.conf"))
      val injector = Injectors.bootstrap(new ConfigModule(config))

      val definition = new ModuleDef {
          make[TestDependency].statically
        make[TestFactory].statically
        make[TestGenericConfFactory[TestConfAlias]].statically
      }
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val factory = context.get[TestFactory]
      assert(factory.make(5) == ConcreteProduct(TestConf(true), 5))
      assert(factory.makeTrait().testConf == TestConf(true))
      assert(factory.makeTraitWith().asInstanceOf[AbstractProductImpl].testConf == TestConf(true))

      assert(context.get[TestDependency] == TestDependency(TestConf(false)))

      assert(context.get[TestGenericConfFactory[TestConf]].x == TestDependency(TestConf(false)))
      assert(context.get[TestGenericConfFactory[TestConf]].make().testConf == TestConf(false))
    }

//    Information:(263, 27) Final syntax tree of factory com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestFactory:
//    Expr[com.github.pshirshov.izumi.distage.provisioning.FactoryConstructor[com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestFactory]]({
//      def constructor(executor$macro$46: FactoryExecutor, x$macro$44: com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestDependency, @new com.github.pshirshov.izumi.distage.config.annotations.AutoConf() transitive$macro$45: com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestConf, @new com.github.pshirshov.izumi.distage.config.annotations.AutoConf() transitive$macro$47: com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestConf, @new com.github.pshirshov.izumi.distage.config.annotations.AutoConf() transitive$macro$48: com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestConf, transitive$macro$49: com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestDependency): com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestFactory = {
//      final class $anon extends com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestFactory {
//        def <init>() = {
//          super.<init>();
//          ()
//        };
//        override def make(fresh$macro$50: Int): com.github.pshirshov.izumi.distage.config.ConfigFixtures.ConcreteProduct = {
//          val symbolDeps: com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring =
//            RuntimeDIUniverse.Wiring.UnaryWiring.Constructor(
//              RuntimeDIUniverse.SafeType.getWeak[com.github.pshirshov.izumi.distage.config.ConfigFixtures.ConcreteProduct]
//              , scala.collection.immutable.List(
//                new RuntimeDIUniverse.Association.Parameter(
//                  context = new RuntimeDIUniverse.DependencyContext.ConstructorParameterContext(RuntimeDIUniverse.SafeType.getWeak[com.github.pshirshov.izumi.distage.config.ConfigFixtures.ConcreteProduct], RuntimeDIUniverse.SymbolInfo.Static("testConf", RuntimeDIUniverse.SafeType.getWeak[com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestConf], scala.collection.immutable.List(_root_.scala.reflect.runtime.universe.Annotation.apply(RuntimeDIUniverse.SafeType.getWeak[com.github.pshirshov.izumi.distage.config.annotations.AutoConf].tpe, scala.collection.immutable.List(), _root_.scala.collection.immutable.ListMap.empty)), RuntimeDIUniverse.SafeType.getWeak[com.github.pshirshov.izumi.distage.config.ConfigFixtures.ConcreteProduct]))
//
//                  , name = "testConf"
//                  , tpe = RuntimeDIUniverse.SafeType.getWeak[com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestConf]
//                  , wireWith = new RuntimeDIUniverse.DIKey.TypeKey(RuntimeDIUniverse.SafeType.getWeak[com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestConf])
//                ), new RuntimeDIUniverse.Association.Parameter(
//                  context = new RuntimeDIUniverse.DependencyContext.ConstructorParameterContext(RuntimeDIUniverse.SafeType.getWeak[com.github.pshirshov.izumi.distage.config.ConfigFixtures.ConcreteProduct], RuntimeDIUniverse.SymbolInfo.Static("int", RuntimeDIUniverse.SafeType.getWeak[Int], scala.collection.immutable.List(), RuntimeDIUniverse.SafeType.getWeak[com.github.pshirshov.izumi.distage.config.ConfigFixtures.ConcreteProduct]))
//                  , name = "int"
//                  , tpe = RuntimeDIUniverse.SafeType.getWeak[Int]
//                  , wireWith = new RuntimeDIUniverse.DIKey.TypeKey(RuntimeDIUniverse.SafeType.getWeak[Int]))));
//          val executorArgs: Map[com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey,Any] = symbolDeps.associations.map(((x$8) => x$8.wireWith)).zip(scala.collection.immutable.List(fresh$macro$50)).toMap;
//          FactoryTools.interpret(executor$macro$46.execute(executorArgs, FactoryTools.mkExecutableOp(new RuntimeDIUniverse.DIKey.TypeKey(RuntimeDIUniverse.SafeType.getWeak[com.github.pshirshov.izumi.distage.config.ConfigFixtures.ConcreteProduct]), symbolDeps))).asInstanceOf[com.github.pshirshov.izumi.distage.config.ConfigFixtures.ConcreteProduct]
//        };
//        override val x: com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestDependency = x$macro$44
//      };
//      new $anon()
//    }.asInstanceOf[com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestFactory];
//      new com.github.pshirshov.izumi.distage.provisioning.FactoryConstructor[com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestFactory](DIKeyWrappedFunction.apply[com.github.pshirshov.izumi.distage.config.ConfigFixtures.TestFactory]((constructor: (() => <empty>))))
//    })
//            make[TestFactory].statically


    "Inject config works for providers" in {
        import ConfigFixtures._

      val config = AppConfig(ConfigFactory.load("macro-fixtures-test.conf"))
      val injector = Injectors.bootstrap(new ConfigModule(config))

      val definition = new ModuleDef {
        make[Int].named("depInt").from(5)
        make[ConcreteProduct].from((conf: TestConf @AutoConf, i: Int @Id("depInt")) => ConcreteProduct(conf, i * 10))
      }
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[ConcreteProduct] == ConcreteProduct(TestConf(false), 50))
    }

  }

}
