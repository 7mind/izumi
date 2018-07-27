package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.config.annotations.AutoConf
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.config.{ConfigFixtures, ConfigModule}
import com.github.pshirshov.izumi.distage.model.definition.StaticModuleDef
import com.typesafe.config.ConfigFactory
import com.github.pshirshov.izumi.distage.model.definition.StaticDSL._
import distage._
import org.scalatest.WordSpec

import scala.language.higherKinds
import scala.util.Try

class StaticInjectorTest extends WordSpec {

  def mkInjector(overrides: ModuleBase*): Injector = Injector.noReflection(overrides: _*)

  def mkInjectorWithProxy(): Injector = Injector()

  "DI planner" should {

    "support circular dependencies (with cglib on JVM)" in {
      import Case2._

      val definition: ModuleBase = new StaticModuleDef {
        stat[Circular2]
        stat[Circular1]
      }

      val injector = mkInjectorWithProxy()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[Circular1] != null)
      assert(context.get[Circular2] != null)
      assert(context.get[Circular2].arg != null)
    }

    "support circular dependencies in providers (with cglib on JVM)" in {
      import Case2._

      val definition: ModuleBase = new StaticModuleDef {
        make[Circular2].from { c: Circular1 => new Circular2(c) }
        make[Circular1].from { c: Circular2 => new Circular1 { override val arg: Circular2 = c } }
      }

      val injector = mkInjectorWithProxy()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[Circular1] != null)
      assert(context.get[Circular2] != null)
      assert(context.get[Circular2].arg != null)
    }

    "support complex circular dependencies (with cglib on JVM)" in {
      import Case3._

      val definition: ModuleBase = new StaticModuleDef {
        stat[Circular3]
        stat[Circular1]
        stat[Circular2]
        stat[Circular5]
        stat[Circular4]
      }

      val injector = mkInjectorWithProxy()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      val c3 = context.get[Circular3]
      val traitArg = c3.arg

      assert(traitArg != null && traitArg.isInstanceOf[Circular4])
      assert(c3.method == 2L)
      assert(traitArg.testVal == 1)
      assert(context.instances.nonEmpty)
      assert(context.get[Circular4].factoryFun(context.get[Circular4], context.get[Circular5]) != null)
    }

    "handle macro factory injections" in {
      import Case5._

      val definition = new StaticModuleDef {
        stat[Factory]
        stat[Dependency]
        stat[OverridingFactory]
        stat[AssistedFactory]
        stat[AbstractFactory]
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

      val definition: ModuleBase = new StaticModuleDef {
        stat[GenericAssistedFactory]
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

      val definition: ModuleBase = new StaticModuleDef {
        stat[AssistedFactory]
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

      val definition: ModuleBase = new StaticModuleDef {
        stat[NamedAssistedFactory]
        stat[Dependency]
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

    "macro factory cannot produce factories" in {
      assertTypeError(
        """
          |import Case5._
          |
          |val definition: ModuleBase = new StaticModuleDef {
          |  stat[FactoryProducingFactory]
          |  stat[Dependency]
          |}
          |
          |val injector = mkInjector()
          |val plan = injector.plan(definition)
          |val context = injector.produce(plan)
          |
          |val instantiated = context.get[FactoryProducingFactory]
          |
          |assert(instantiated.x().x().b == context.get[Dependency])
        """.stripMargin
      )
    }

    "macro factory always produces new instances" in {
      import Case5._

      val definition: ModuleBase = new StaticModuleDef {
        stat[Dependency]
        stat[TestClass]
        stat[Factory]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[Factory]

      assert(!instantiated.x().eq(context.get[TestClass]))
      assert(!instantiated.x().eq(instantiated.x()))
    }

    "handle one-arg trait" in {
      import Case7._

      val definition = new StaticModuleDef {
        stat[Dependency1]
        stat[TestTrait]
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

      val definition = new StaticModuleDef {
        stat[Dependency1]
        make[TestTrait].named("named-trait").stat[TestTrait]
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

      val definition = new StaticModuleDef {
        stat[Trait3]
        stat[Trait2]
        stat[Trait1]
        stat[Dependency3]
        stat[Dependency2]
        stat[Dependency1]
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

      val definition = new StaticModuleDef {
        make[Trait2].stat[Trait3]
        stat[Dependency3]
        stat[Dependency2]
        stat[Dependency1]
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

      val definition = new StaticModuleDef {
        make[Dep].named("A").stat[DepA]
        make[Dep].named("B").stat[DepB]
        stat[Trait]
        stat[Trait1]
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

      val definition = new StaticModuleDef {
        stat[TestTrait]
        stat[Dep]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]

      assert(instantiated.rd == Dep().toString)
    }

    "Inject config works for macro trait methods" in {
      import ConfigFixtures._

      val config = AppConfig(ConfigFactory.load("macro-fixtures-test.conf"))
      val injector = mkInjector(new ConfigModule(config))

      val definition = new StaticModuleDef {
        stat[TestDependency]
        stat[TestTrait]
      }
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestTrait].x == TestDependency(TestConf(false)))
      assert(context.get[TestTrait].testConf == TestConf(true))
      assert(context.get[TestDependency] == TestDependency(TestConf(false)))
    }

    "Inject config works for macro factory methods (not products)" in {
      import ConfigFixtures._

      val config = AppConfig(ConfigFactory.load("macro-fixtures-test.conf"))
      val injector = mkInjector(new ConfigModule(config))

      val definition = new StaticModuleDef {
        stat[TestDependency]
        stat[TestGenericConfFactory[TestConfAlias]]
      }
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestDependency] == TestDependency(TestConf(false)))
      assert(context.get[TestGenericConfFactory[TestConf]].x == TestDependency(TestConf(false)))
    }

    "Inject config works for macro factory products" in {
      // FactoryMethod wirings are generated at compile-time and inacessible to ConfigModule, so method ends up depending on TestConf, not TestConf#auto[..]. To fix this, need a new type of binding that would include all factory reflected info
      import ConfigFixtures._

      val config = AppConfig(ConfigFactory.load("macro-fixtures-test.conf"))
      val injector = mkInjector(new ConfigModule(config))

      val definition = new StaticModuleDef {
        stat[TestDependency]
        stat[TestFactory]
        stat[TestGenericConfFactory[TestConfAlias]]
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

    "Inject config works for providers" in {
      import ConfigFixtures._

      val config = AppConfig(ConfigFactory.load("macro-fixtures-test.conf"))
      val injector = mkInjector(new ConfigModule(config))

      val definition = new StaticModuleDef {
        make[Int].named("depInt").from(5)
        make[ConcreteProduct].from((conf: TestConf @AutoConf, i: Int @Id("depInt")) => ConcreteProduct(conf, i * 10))
      }
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[ConcreteProduct] == ConcreteProduct(TestConf(false), 50))
    }

    "macros can handle class local path-dependent injections" in {
      val definition = new StaticModuleDef {
        stat[TopLevelPathDepTest.TestClass]
        stat[TopLevelPathDepTest.TestDependency]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[TopLevelPathDepTest.TestClass].a != null)
    }

    "macros can handle inner path-dependent injections" in {
      new InnerPathDepTest().testCase
    }

    "progression test: macros can't handle function local path-dependent injections" in {
      val fail = Try {
        import Case16._

        val testProviderModule = new TestProviderModule

        val definition = new StaticModuleDef {
          stat[testProviderModule.TestClass]
          stat[testProviderModule.TestDependency]
        }

        val injector = mkInjector()
        val plan = injector.plan(definition)

        val context = injector.produce(plan)

        assert(context.get[testProviderModule.TestClass].a != null)
      }.isFailure
      assert(fail)
    }

    "macros support tagless final style module definitions" in {
      import Case20._

      case class Definition[F[_] : TagK : Pointed](getResult: Int) extends StaticModuleDef {
        // FIXME: hmmm, what to do with this
        make[Pointed[F]].from(Pointed[F])

        make[TestTrait].stat[TestServiceClass[F]]
        stat[TestServiceClass[F]]
        stat[TestServiceTrait[F]]
        make[Int].named("TestService").from(getResult)
        make[F[String]].from { res: Int @Id("TestService") => Pointed[F].point(s"Hello $res!") }
        make[Either[String, Boolean]].from(Right(true))

        //        FIXME: Nothing doesn't resolve properly yet when F is unknown...
        //        make[F[Nothing]]
        //        make[Either[String, F[Int]]].from(Right(Pointed[F].point(1)))
        make[F[Any]].from(Pointed[F].point(1: Any))
        make[Either[String, F[Int]]].from { fAnyInt: F[Any] => Right[String, F[Int]](fAnyInt.asInstanceOf[F[Int]]) }
        make[F[Either[Int, F[String]]]].from(Pointed[F].point(Right[Int, F[String]](Pointed[F].point("hello")): Either[Int, F[String]]))
      }

      val listInjector = mkInjector()
      val listPlan = listInjector.plan(Definition[List](5))
      val listContext = listInjector.produce(listPlan)

      assert(listContext.get[TestTrait].get == List(5))
      assert(listContext.get[TestServiceClass[List]].get == List(5))
      assert(listContext.get[TestServiceTrait[List]].get == List(10))
      assert(listContext.get[List[String]] == List("Hello 5!"))
      assert(listContext.get[List[Any]] == List(1))
      assert(listContext.get[Either[String, Boolean]] == Right(true))
      assert(listContext.get[Either[String, List[Int]]] == Right(List(1)))
      assert(listContext.get[List[Either[Int, List[String]]]] == List(Right(List("hello"))))

      val optionTInjector = mkInjector()
      val optionTPlan = optionTInjector.plan(Definition[OptionT[List, ?]](5))
      val optionTContext = optionTInjector.produce(optionTPlan)

      assert(optionTContext.get[TestTrait].get == OptionT(List(Option(5))))
      assert(optionTContext.get[TestServiceClass[OptionT[List, ?]]].get == OptionT(List(Option(5))))
      assert(optionTContext.get[TestServiceTrait[OptionT[List, ?]]].get == OptionT(List(Option(10))))
      assert(optionTContext.get[OptionT[List, String]] == OptionT(List(Option("Hello 5!"))))

      val idInjector = mkInjector()
      val idPlan = idInjector.plan(Definition[id](5))
      val idContext = idInjector.produce(idPlan)

      assert(idContext.get[TestTrait].get == 5)
      assert(idContext.get[TestServiceClass[id]].get == 5)
      assert(idContext.get[TestServiceTrait[id]].get == 10)
      assert(idContext.get[id[String]] == "Hello 5!")
    }

    "Handle multiple parameter lists" in {
      import Case21._

      val injector = mkInjector()

      val definition = new StaticModuleDef {
        stat[TestDependency1]
        stat[TestDependency2]
        stat[TestDependency3]
        stat[TestClass]
      }
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestClass].a != null)
      assert(context.get[TestClass].b != null)
      assert(context.get[TestClass].c != null)
      assert(context.get[TestClass].d != null)
    }

    "Implicit parameters are injected from the DI context, not from Scala's lexical implicit scope" in {
      import Case21._

      val injector = mkInjector()

      val definition = new StaticModuleDef {
        implicit val testDependency3: TestDependency3 = new TestDependency3

        stat[TestDependency1]
        stat[TestDependency2]
        stat[TestDependency3]
        stat[TestClass]
      }
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestClass].b == context.get[TestClass].d)
    }
  }

  class InnerPathDepTest extends Case16.TestProviderModule {
    private val definition = new StaticModuleDef {
      stat[TestClass]
      stat[TestDependency]
    }

    def testCase = {
      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[TestClass].a != null)
    }
  }

  object TopLevelPathDepTest extends Case16.TestProviderModule

}
