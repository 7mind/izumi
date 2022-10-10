package izumi.distage.injector

import distage.{ModuleDef, With}
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
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(definition)
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
    val plan = injector.plan(definition)
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
    val plan = injector.plan(definition)
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
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val dep = context.get[Dependency]
    val instantiated = context.get[MixedAssistendNonAssisted]

    assert(instantiated.assisted().b eq dep)
    val dependency = new Dependency {}
    assert(instantiated.nonAssisted(dependency).b ne dep)
    assert(instantiated.nonAssisted(dependency).b eq dependency)
  }

  "handle assisted abstract factories with multiple parameters of the same type" in {
    import FactoryCase2._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[AssistedAbstractFactory]
      make[Dependency]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val dep = context.get[Dependency]
    val instantiated = context.get[AssistedAbstractFactory]
    val instance = instantiated.x(1, 2, 3)

    assert(instance.isInstanceOf[ProductImpl])
    assert(instance.asInstanceOf[ProductImpl].dependency eq dep)
    assert(instance == ProductImpl(3, 2, 1, dep))
  }

  "handle higher-kinded assisted abstract factories with multiple parameters of the same type" in {
    import FactoryCase2._

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[AssistedAbstractFactoryF[Identity]]
      make[Identity[Dependency]]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
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
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[{ def makeConcreteDep(): Dependency @With[ConcreteDep] }]

    val instance = instantiated.makeConcreteDep()
    assert(instance.isInstanceOf[ConcreteDep])
  }

  "Factory cannot produce factories" in {
    val exc = intercept[TestFailedException] {
      assertCompiles("""
        import FactoryCase1._

        // FIXME: `make` support? should be compile-time error
        val definition = PlannerInput.everything(new ModuleDef {
          makeFactory[FactoryProducingFactory]
          make[Dependency]
        })

        val injector = mkInjector()
        val plan = injector.plan(definition)
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
    val plan = injector.plan(definition)
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
    val plan = injector.plan(definition)
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
    assert(res.getMessage.contains("contains types not required by constructor of the result type"))
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
    val plan = injector.plan(definition)
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
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[AssistedFactory]

    assert(instantiated.x(5).a == 5)
  }

}
