package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.model.Injector
import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.definition.MagicDSL._
import com.github.pshirshov.izumi.distage.model.exceptions.{MissingInstanceException, TraitInitializationFailedException, UnsupportedWiringException, UntranslatablePlanException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan.PlanningFailure.UnsolvableConflict
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring
import org.scalatest.WordSpec

class StaticInjectorTest extends WordSpec {

  def mkInjector(): Injector = Injectors.bootstrap()

  "DI planner" should {

    // TODO GenericFactory polymorphic arguments in producer methods
    "handle macro factory injections" in {
      import Case5._

      val definition = TrivialModuleDef
        .magic[Factory]
        .magic[Dependency]
        .magic[OverridingFactory]
        .magic[AssistedFactory]
        .magic[AbstractFactory]

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val factory = context.get[Factory]
      assert(factory.wiringTargetForDependency != null)
      assert(factory.factoryMethodForDependency() != factory.wiringTargetForDependency)
      assert(factory.x().b.isInstanceOf[Dependency])

      val abstractFactory = context.get[AbstractFactory]
      assert(abstractFactory.x().isInstanceOf[AbstractDependencyImpl])

      val overridingFactory = context.get[OverridingFactory]
      assert(overridingFactory.x(ConcreteDep()).b.isInstanceOf[ConcreteDep])

      val assistedFactory = context.get[AssistedFactory]
      assert(assistedFactory.x(1).a == 1)
      assert(assistedFactory.x(1).b.isInstanceOf[Dependency])
    }

    "handle generic arguments in macro factory methods" in {
      import Case5._

      val definition: AbstractModuleDef = TrivialModuleDef
        .magic[GenericAssistedFactory]
        .bind[Dependency](ConcreteDep())

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

      val definition: AbstractModuleDef = TrivialModuleDef
        .magic[AssistedFactory]
        .bind[Dependency](ConcreteDep())

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[AssistedFactory]

      assert(instantiated.x(5).a == 5)
    }

    "handle named assisted dependencies in macro factory methods" in {
      import Case5._

      val definition: AbstractModuleDef = TrivialModuleDef
        .magic[NamedAssistedFactory]
        .magic[Dependency]
        .bind[Dependency](SpecialDep()).named("special")
        .bind[Dependency](VerySpecialDep()).named("veryspecial")

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[NamedAssistedFactory]

      assert(instantiated.dep.isVerySpecial)
      assert(instantiated.x(5).b.isSpecial)
    }

    "handle one-arg trait" in {
      import Case7._

      val definition = TrivialModuleDef
        .bind[Dependency1]
        .magic[TestTrait]

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]
      assert(instantiated.isInstanceOf[TestTrait])
      assert(instantiated.dep != null)
    }

    "handle named one-arg trait" in {
      import Case7._

      val definition = TrivialModuleDef
        .bind[Dependency1]
        .magic[TestTrait]
        .named("named-trait")

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]("named-trait")
      assert(instantiated.isInstanceOf[TestTrait])
      assert(instantiated.dep != null)
    }

    "handle mixed sub-trait with protected autowires" in {
      import Case8._

      val definition = TrivialModuleDef
        .magic[Trait3]
        .magic[Trait2]
        .magic[Trait1]
        .bind[Dependency3]
        .bind[Dependency2]
        .bind[Dependency1]

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

      val definition = TrivialModuleDef
        .bind[Trait2].magically[Trait3]
        .bind[Dependency3]
        .bind[Dependency2]
        .bind[Dependency1]

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated3 = context.get[Trait2]
      assert(instantiated3.isInstanceOf[Trait2])
      assert(instantiated3.asInstanceOf[Trait3].prr() == "Hello World")
    }

    "support named bindings in macro traits" in {
      import Case10._

      val definition = TrivialModuleDef
        .bind[Dep].magically[DepA].named("A")
        .bind[Dep].magically[DepB].named("B")
        .magic[Trait]
        .magic[Trait1]

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

      val definition = TrivialModuleDef
        .magic[TestTrait]
        .magic[Dep]

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]

      assert(instantiated.rd == Dep().toString)
    }
  }

}
