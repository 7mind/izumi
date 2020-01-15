package izumi.distage.staticinjector

import izumi.distage.fixtures.FactoryCases.FactoryCase1
import izumi.distage.injector.MkInjector
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import org.scalatest.wordspec.AnyWordSpec

class MacroAutoFactoriesTest extends AnyWordSpec with MkInjector {

  "handle macro factory injections" in {
    import FactoryCase1._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Factory]
      make[Dependency]
      make[OverridingFactory]
      make[AssistedFactory]
      make[AbstractFactory]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

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
    import FactoryCase1._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[GenericAssistedFactory]
      make[Dependency].from(ConcreteDep())
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val instantiated = context.get[GenericAssistedFactory]
    val product = instantiated.x(List(SpecialDep()), List(5))
    assert(product.a.forall(_.isSpecial))
    assert(product.b.forall(_ == 5))
    assert(product.c == ConcreteDep())
  }

  "handle assisted dependencies in macro factory methods" in {
    import FactoryCase1._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[AssistedFactory]
      make[Dependency].from(ConcreteDep())
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val instantiated = context.get[AssistedFactory]

    assert(instantiated.x(5).a == 5)
  }

  "handle named assisted dependencies in macro factory methods" in {
    import FactoryCase1._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[NamedAssistedFactory]
      make[Dependency]
      make[Dependency].named("special").from(SpecialDep())
      make[Dependency].named("veryspecial").from(VerySpecialDep())
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val instantiated = context.get[NamedAssistedFactory]

    assert(instantiated.dep.isVerySpecial)
    assert(instantiated.x(5).b.isSpecial)
  }

  "macro factory cannot produce factories" in {
    assertTypeError(
      """
        |import Case5._
        |
        |val definition: ModuleBase = new ModuleDef {
        |  make[FactoryProducingFactory]
        |  make[Dependency]
        |}
        |
        |val injector = mkStaticInjector()
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
    import FactoryCase1._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Dependency]
      make[TestClass]
      make[Factory]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val instantiated = context.get[Factory]

    assert(!instantiated.x().eq(context.get[TestClass]))
    assert(!instantiated.x().eq(instantiated.x()))
  }

}
