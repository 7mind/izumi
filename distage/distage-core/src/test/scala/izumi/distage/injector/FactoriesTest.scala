package izumi.distage.injector

import distage.{ModuleDef, TagKK, With}
import izumi.distage.LocalContext
import izumi.distage.constructors.FactoryConstructor
import izumi.distage.fixtures.FactoryCases.*
import izumi.distage.model.PlannerInput
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

import scala.language.reflectiveCalls

class FactoriesTest extends AnyWordSpec with MkInjector {

  "handle factory injections" in {
    import FactoryCase1._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[Factory]
      make[Dependency]
      makeFactory[OverridingFactory]
      makeFactory[AssistedFactory]
      makeFactory[AbstractFactory]
      make[LocalContext[Identity]].from()
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val factory = context.get[Factory]
    assert(factory.wiringTargetForDependency != null)
    assert(factory.factoryMethodForDependency() != factory.wiringTargetForDependency)
    assert(factory.factoryMethodForDependency() != factory.factoryMethodForDependency())
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

  "handle generic arguments in factory methods" in {
    import FactoryCase1._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[GenericAssistedFactory]
      make[Dependency].from(ConcreteDep())
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[GenericAssistedFactory]
    val product = instantiated.x(List(SpecialDep()), List(5))
    assert(product.a.forall(_.isSpecial))
    assert(product.b.forall(_ == 5))
    assert(product.c == ConcreteDep())
  }

  "handle named assisted dependencies in factory methods" in {
    import FactoryCase1._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[NamedAssistedFactory]
      make[Dependency]
      make[Dependency].named("special").from(SpecialDep())
      make[Dependency].named("veryspecial").from(VerySpecialDep())
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(!context.get[Dependency].isSpecial)
    assert(context.get[Dependency]("special").isSpecial)
    assert(context.get[Dependency]("veryspecial").isVerySpecial)

    val instantiated = context.get[NamedAssistedFactory]

    // makeFactory - change of semantics!
    assert(!instantiated.dep.isVerySpecial)

    assert(instantiated.x(5).b.isSpecial)
  }

  "handle factories with mixed assisted and non-assisted methods" in {
    import FactoryCase1._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[MixedAssistendNonAssisted]
      make[Dependency]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val dep = context.get[Dependency]
    val instantiated = context.get[MixedAssistendNonAssisted]

    assert(instantiated.assisted().b eq dep)
    val dependency = new Dependency {}
    assert(instantiated.nonAssisted(dependency).b ne dep)
    assert(instantiated.nonAssisted(dependency).b eq dependency)
  }

  "handle assisted abstract factories with multiple parameters of the same type with names matching constructor" in {
    import FactoryCase2._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[AssistedAbstractFactory]
      make[Dependency]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val dep = context.get[Dependency]
    val instantiated = context.get[AssistedAbstractFactory]
    val instance = instantiated.x(1, 2, 3)

    assert(instance.isInstanceOf[ProductImpl])
    assert(instance.asInstanceOf[ProductImpl].dependency eq dep)
    assert(instance == ProductImpl(3, 2, 1, dep))
  }

  // FIXME: broken due to dotty bug https://github.com/lampepfl/dotty/issues/16468
  "handle higher-kinded assisted abstract factories with multiple parameters of the same type" in {
    import FactoryCase2._
    import izumi.fundamentals.platform.functional.Identity

    val definition = PlannerInput.everything(new ModuleDef {
      // FIXME: broken due to dotty bug https://github.com/lampepfl/dotty/issues/16468
      makeFactory[AssistedAbstractFactoryF[Identity]]
      make[Identity[Dependency]]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val dep = context.get[Dependency]
    val instantiated = context.get[AssistedAbstractFactoryF[Identity]]
    val instance = instantiated.x(1, 2, 3)

    assert(instance.isInstanceOf[ProductFImpl[Identity]])
    assert(instance.asInstanceOf[ProductFImpl[Identity]].dependency eq dep)
    assert(instance == ProductFImpl[Identity](3, 2, 1, dep))
  }

  "handle structural type factories" in {
    import FactoryCase1._

    FactoryConstructor[{
        def makeConcreteDep(): Dependency @With[ConcreteDep]
      }
    ]

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[{
          def makeConcreteDep(): Dependency @With[ConcreteDep]
        }
      ]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[{ def makeConcreteDep(): Dependency @With[ConcreteDep] }]

    val instance = instantiated.makeConcreteDep()
    assert(instance.isInstanceOf[ConcreteDep])
  }

  "Factory cannot produce factories" in {
    val exc = intercept[TestFailedException] {
      assertCompiles("""
        import FactoryCase1._

        val definition = PlannerInput.everything(new ModuleDef {
          makeFactory[FactoryProducingFactory]
          make[Dependency]
        })

        val injector = mkInjector()
        val plan = injector.planUnsafe(definition)
        val context = injector.produce(plan).unsafeGet()

        val instantiated = context.get[FactoryProducingFactory]

        assert(instantiated.x().x().b == context.get[Dependency])
      """)
    }
    assert(exc.getMessage.contains("Factory cannot produce factories"))
  }

  "Factory cannot produce factories (dotty test) [Scala 3 bug, `Couldn't find position` in `make` macro inside assertCompiles]" in {
    val exc = intercept[TestFailedException] {
      assertCompiles("""
        import FactoryCase1._

        val definition = PlannerInput.everything(new ModuleDef {
          makeFactory[FactoryProducingFactory]
//          make[Dependency]
        })

        val injector = mkInjector()
        val plan = injector.planUnsafe(definition)
        val context = injector.produce(plan).unsafeGet()

        val instantiated = context.get[FactoryProducingFactory]

        assert(instantiated.x().x().b == context.get[Dependency])
      """)
    }
    assert(exc.getMessage.contains("Factory cannot produce factories"))
  }

  "factory always produces new instances" in {
    import FactoryCase1._

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dependency]
      make[TestClass]
      makeFactory[Factory]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[Factory]

    assert(!instantiated.x().eq(context.get[TestClass]))
    assert(!instantiated.x().eq(instantiated.x()))
  }

  "can handle factory methods with implicit parameters" in {
    import FactoryCase3._

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dep1]
      make[Dep2]
      make[TC[Any]].fromValue(TC1)
      makeFactory[ImplicitFactory]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[ImplicitFactory]

    assert(instantiated.x(new Dep1).dep1 ne context.get[Dep1])
    assert(instantiated.x(new Dep1).dep2 eq context.get[Dep2])
    assert(instantiated.x(new Dep1).TC != TC1)
    assert(instantiated.x(new Dep1).TC == TC2)
    assert(instantiated.x(new Dep1).dep3 == Dep3)

    val res = intercept[TestFailedException](assertCompiles("""new ModuleDef {
      makeFactory[InvalidImplicitFactory]
    }"""))
    assert(
      res.getMessage.contains("contains types not required by constructor of the result type") ||
      res.getMessage.contains("has arguments which were not consumed by implementation constructor")
    )
    assert(res.getMessage.contains("UnrelatedTC["))
  }

  "can handle abstract classes" in {
    import FactoryCase1._

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dependency]
      make[TestClass]
      makeFactory[AbstractClassFactory]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[AbstractClassFactory].x(5) == AssistedTestClass(context.get[Dependency], 5))
  }

  "handle assisted dependencies in factory methods" in {
    import FactoryCase1._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[AssistedFactory]
      make[Dependency].from(ConcreteDep())
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[AssistedFactory]

    assert(instantiated.x(5).a == 5)
  }

  "support makeFactory" in {
    import FactoryCase4._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[IFactoryImpl]

      make[IFactory].using[IFactoryImpl]
      make[IFactory1].using[IFactoryImpl]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val factory = context.get[IFactory]
    val factory1 = context.get[IFactory1]

    assert(factory.asInstanceOf[AnyRef] eq factory1.asInstanceOf[AnyRef])
    assert(factory.dep() ne factory1.dep1())
  }

  "support intersection factory types" in {
    import FactoryCase4._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[IFactory1 & IFactory]

      make[IFactory].using[IFactory1 & IFactory]
      make[IFactory1].using[IFactory1 & IFactory]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val factory = context.get[IFactory]
    val factory1 = context.get[IFactory1]

    assert(factory.asInstanceOf[AnyRef] eq factory1.asInstanceOf[AnyRef])
    assert(factory.dep() ne factory1.dep1())
  }

  "support intersection factory types with implicit overrides" in {
    import FactoryCase7._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[IFactory1 & IFactory2]

      make[IFactory1].using[IFactory1 & IFactory2]
      make[IFactory2].using[IFactory1 & IFactory2]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val factory1 = context.get[IFactory1]
    val factory2 = context.get[IFactory2]

    assert(factory2.asInstanceOf[AnyRef] eq factory1.asInstanceOf[AnyRef])
    assert(factory2.dep() ne factory1.dep())
    assert(factory2.dep().isInstanceOf[Dep])
    assert(factory1.dep().isInstanceOf[Dep])
  }

  "support refinement factory types with overrides" in {
    import FactoryCase7._

    val definition = PlannerInput.everything(new ModuleDef {
      make[IFactory1].fromFactory[IFactory1 { def dep(): Dep }]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val factory1 = context.get[IFactory1]

    assert(factory1.dep() ne factory1.dep())
    assert(factory1.dep().isInstanceOf[Dep])
  }

  "support make[].fromFactory" in {
    // this case makes no sense tbh
    import FactoryCase5._

    val definition = PlannerInput.everything(new ModuleDef {
      make[IFactory1].fromFactory[IFactoryImpl]
      make[IFactory].using[IFactory1]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val factory = context.get[IFactory]
    val factory1 = context.get[IFactory1]

    assert(factory.asInstanceOf[AnyRef] eq factory1.asInstanceOf[AnyRef])
    assert(factory.dep() ne factory1.dep1())
  }

  "support make[].fromFactory: narrowing" in {
    import FactoryCase6._

    val definition = PlannerInput.everything(new ModuleDef {
      make[IFactory].fromFactory[IFactoryImpl]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val factory = context.get[IFactory]

    assert(factory.dep() ne factory.dep())
    assert(factory.dep().isInstanceOf[Dep])
  }

  "support polymorphic factory types" in {
    import FactoryCase8._

    def definition[F[+_, +_]: TagKK] = PlannerInput.everything(new ModuleDef {
      makeFactory[XFactory[F]]
      make[XContext[F]]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition[Either])
    val context = injector.produce(plan).unsafeGet()

    val factory = context.get[XFactory[Either]]
    val xContext = context.get[XContext[Either]]
    val param = new XParam[Either]

    assert(factory.create(param) == new XImpl[Either](xContext, param))
  }

}
